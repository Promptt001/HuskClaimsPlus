/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskclaims.gui;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuHost;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.gui.model.TextPrompt;
import net.william278.huskclaims.user.FabricUser;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows claim menus to Fabric players as server-side screens (sgui), so players need
 * no client mod. The screen cancels every click; the menu's action runs on the server
 * thread one tick later, at most one action per player at a time.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class SguiMenuHost implements MenuHost {

    private static final MenuType<?>[] CHEST_ROWS = {
            MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6
    };

    private final FabricHuskClaims plugin;
    private final GuiController controller;
    private final Map<UUID, Session> open = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public SguiMenuHost(@NotNull FabricHuskClaims plugin, @NotNull GuiController controller) {
        this.plugin = plugin;
        this.controller = controller;   // do not call into it from the constructor
    }

    @NotNull
    private static ServerPlayer player(@NotNull OnlineUser user) {
        return ((FabricUser) user).getFabricPlayer();
    }

    @Override
    public void open(@NotNull OnlineUser user, @NotNull MenuView view) {
        runFor(user, () -> {
            final ServerPlayer player = player(user);
            final int rows = Math.clamp(view.rows(), 1, 6);
            final Session[] holder = new Session[1];
            final SimpleGui gui = new SimpleGui(CHEST_ROWS[rows - 1], player, false) {
                @Override
                public void onPlayerClose(boolean success) {
                    open.remove(player.getUUID(), holder[0]);
                    GuiItemMarker.purge(player, plugin);
                }
            };
            holder[0] = new Session(view, gui);
            open.put(player.getUUID(), holder[0]);
            render(holder[0], user);
            gui.open();
        });
    }

    @Override
    public void refresh(@NotNull OnlineUser user, @NotNull MenuView view) {
        runFor(user, () -> {
            final Session session = open.get(user.getUuid());
            if (session != null && session.view() == view && session.gui().isOpen()) {
                render(session, user);
            } else {
                open(user, view);
            }
        });
    }

    @Override
    public void close(@NotNull OnlineUser user) {
        runFor(user, () -> {
            final Session session = open.remove(user.getUuid());
            if (session != null) {
                session.gui().close(false);
            }
        });
    }

    private void render(@NotNull Session session, @NotNull OnlineUser user) {
        final SimpleGui gui = session.gui();
        final MenuView view = session.view();
        gui.setTitle(text(view.title().orElseGet(() -> controller.defaultTitle(view))));
        final Map<Integer, MenuIcon> icons = view.icons();
        final MenuIcon filler = controller.filler();
        for (int slot = 0; slot < gui.getSize(); slot++) {
            final int index = slot;
            final MenuIcon icon = icons.getOrDefault(slot, filler);
            final boolean isButton = icons.containsKey(slot);
            final GuiElementBuilder element = element(icon);
            if (isButton) {
                element.setCallback((i, type, action, g) -> click(user, session, index, type));
            }
            gui.setSlot(slot, element.build());
        }
    }

    private void click(@NotNull OnlineUser user, @NotNull Session session, int slot,
                       @NotNull ClickType type) {
        final Optional<ClickKind> kind = kind(type);
        if (kind.isEmpty() || !pending.add(user.getUuid())) {
            return;
        }
        // one tick later, like the Paper host: never act inside the click packet handler
        plugin.runSyncDelayed(() -> {
            try {
                if (open.get(user.getUuid()) == session) {
                    session.view().onClick(user, slot, kind.get());
                }
            } finally {
                pending.remove(user.getUuid());
            }
        }, Duration.ofMillis(50));
    }

    @NotNull
    private static Optional<ClickKind> kind(@NotNull ClickType type) {
        if (type.isDragging || type.numKey || type == ClickType.MOUSE_MIDDLE) {
            return Optional.empty();
        }
        if (type.isLeft) {
            return Optional.of(type.shift ? ClickKind.SHIFT_LEFT : ClickKind.LEFT);
        }
        if (type.isRight) {
            return Optional.of(type.shift ? ClickKind.SHIFT_RIGHT : ClickKind.RIGHT);
        }
        return Optional.empty();
    }

    @NotNull
    GuiElementBuilder element(@NotNull MenuIcon icon) {
        final Item item = item(icon.itemKey()).orElse(Items.BARRIER);
        final GuiElementBuilder builder = new GuiElementBuilder(item, icon.amount())
                .setName(text(icon.name()))
                .setLore(icon.lore().stream().map(SguiMenuHost::text).toList())
                .hideDefaultTooltip();
        if (icon.glint()) {
            builder.glow();
        }
        if (icon.headOwner() != null) {
            builder.setProfile(icon.headOwner());           // head texture owner
        }
        builder.setComponent(DataComponents.CUSTOM_DATA, GuiItemMarker.MENU_ITEM);
        return builder;
    }

    @NotNull
    private static Optional<Item> item(@NotNull String key) {
        final Identifier id = Identifier.tryParse(key);
        if (id == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(id).filter(i -> i != Items.AIR);
    }

    /** Section-coded text to a non-italic vanilla component. */
    @NotNull
    static Component text(@NotNull String legacy) {
        return Component.literal(legacy).withStyle(style -> style.withItalic(false));
    }

    @Override
    public boolean isItem(@NotNull String itemKey) {
        return item(itemKey).isPresent();
    }

    @Override
    public void runFor(@NotNull OnlineUser user, @NotNull Runnable task) {
        plugin.runSync(task);   // server thread on Fabric (integrated or dedicated)
    }

    @Override
    public boolean isOnline(@NotNull OnlineUser user) {
        return !player(user).hasDisconnected();
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public String cachedUserName(@NotNull java.util.UUID uuid) {
        // The player list is authoritative for connected players and never
        // blocks. Offline names come back null: the shared menus then fall
        // back to the claim world's own name cache.
        final ServerPlayer online = plugin.getMinecraftServer().getPlayerList().getPlayer(uuid);
        return online != null ? online.getGameProfile().name() : null;
    }

    /** Close every menu (session stop, integrated-world switch). */
    public void closeAll() {
        open.values().forEach(s -> s.gui().close(false));
        open.clear();
        pending.clear();
    }

    @Override
    public void prompt(@NotNull OnlineUser user, @NotNull TextPrompt prompt) {
        runFor(user, () -> AnvilPrompt.open(this, controller, player(user), prompt));
    }

    @Override
    public void giveClaimTool(@NotNull OnlineUser user) {
        runFor(user, () -> FabricClaimTool.give(plugin, player(user), user));
    }

    /** A player's open menu: the view (what to show) and the sgui screen (how). */
    private record Session(@NotNull MenuView view, @NotNull SimpleGui gui) {
    }

}
