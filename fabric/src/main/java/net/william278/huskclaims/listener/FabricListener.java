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

package net.william278.huskclaims.listener;

import com.google.common.collect.Lists;
import lombok.Getter;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.william278.cloplib.listener.FabricOperationListener;
import net.william278.cloplib.operation.OperationPosition;
import net.william278.cloplib.operation.OperationUser;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.gui.GuiItemMarker;
import net.william278.huskclaims.moderation.SignListener;
import net.william278.huskclaims.moderation.SignWrite;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.user.User;
import net.william278.huskclaims.util.Location;
import net.william278.huskclaims.util.PlayerActionEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@Getter
public class FabricListener extends FabricOperationListener implements FabricPetListener, FabricDropsListener,
        ClaimsListener, UserListener, SignListener {

    protected final FabricHuskClaims plugin;

    public FabricListener(@NotNull FabricHuskClaims plugin) {
        super(new SessionGatedHandler(plugin), plugin.getMod());
        this.plugin = plugin;
    }

    @Override
    public void register() {
        setInspectorCallbacks();

        ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
        ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerQuit);
        ServerLevelEvents.LOAD.register(this::onWorldLoad);
        PlayerActionEvents.AFTER_HELD_ITEM_CHANGE.register(this::onUserChangeHeldItems);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::onPlayerDamageTamed);
        UseEntityCallback.EVENT.register(this::onPlayerTamedInteract);
        PlayerActionEvents.BEFORE_CHANGE_TEXT_ON_SIGN.register(this::onSignEdit);
        PlayerActionEvents.AFTER_DEATH_DROP_ITEMS.register(this::onPlayerDeath);
    }

    private void onPlayerJoin(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server) {
        if (!plugin.isSessionActive()) {
            return;
        }
        plugin.getOnlineUserMap().remove(handler.getPlayer().getUUID());
        this.onUserJoin(plugin.getOnlineUser(handler.getPlayer()));
        // Defence in depth for the GUI: remove any menu icons that survived a crash
        // or client-side trickery (sgui slots are virtual, but see GuiItemMarker)
        GuiItemMarker.purge(handler.getPlayer(), plugin);
    }


    public void onPlayerQuit(ServerGamePacketListenerImpl handler, MinecraftServer server) {
        if (!plugin.isSessionActive()) {
            return; // the session already ended; its state was reset
        }
        this.onUserQuit(plugin.getOnlineUser(handler.getPlayer()));
    }

    public void onUserChangeHeldItems(@NotNull ServerPlayer player,
                                      @NotNull ItemStack mainHandItem, @NotNull ItemStack offHandItme) {
        if (!plugin.isSessionActive()) {
            return;
        }
        this.onUserSwitchHeldItem(
                plugin.getOnlineUser(player),
                BuiltInRegistries.ITEM.getKey(mainHandItem.isEmpty() ? Items.AIR : mainHandItem.getItem()).toString(),
                BuiltInRegistries.ITEM.getKey(offHandItme.isEmpty() ? Items.AIR : offHandItme.getItem()).toString()
        );
    }

    public List<FilteredText> onSignEdit(SignBlockEntity sign, ServerPlayer player, boolean front, List<FilteredText> messages) {
        if (!plugin.isSessionActive()) {
            return messages; // no session: let vanilla proceed untouched
        }
        SignWrite write = handleSignEdit(SignWrite.create(
            plugin.getOnlineUser(player),
            FabricHuskClaims.Adapter.adapt(new Location((ServerLevel) sign.getLevel(), sign.getBlockPos())),
            front ? SignWrite.Type.SIGN_EDIT_FRONT : SignWrite.Type.SIGN_EDIT_BACK,
            messages.stream().map(FilteredText::filtered).toArray(String[]::new),
            plugin.getServerName()
        ));

        if (write.getFilteredText() == null) {
            return messages;
        }

        List<FilteredText> filteredMessages = Lists.newArrayList();
        for (int l = 0; l < messages.size(); l++) {
            filteredMessages.add(FilteredText.passThrough(write.getFilteredText().get(l)));
        }
        return filteredMessages;
    }

//    @EventHandler(ignoreCancelled = true)
//    public void onUserTeleport(@NotNull PlayerTeleportEvent e) {
//        if (e.getTo() != null && getPlugin().cancelMovement(
//                plugin.getOnlineUser(e.getPlayer()),
//                BukkitHuskClaims.Adapter.adapt(e.getFrom()),
//                BukkitHuskClaims.Adapter.adapt(e.getTo())
//        )) {
//            e.setCancelled(true);
//        }
//    }

    public void onWorldLoad(@NotNull MinecraftServer server, @NotNull ServerLevel serverWorld) {
        if (!plugin.isSessionActive()) {
            return;
        }
        plugin.runAsync(() -> {
            final World world = FabricHuskClaims.Adapter.adapt(serverWorld);
            plugin.loadClaimWorld(world);
            plugin.getClaimWorld(world).ifPresent(loaded -> plugin.getMapHooks().forEach(
                    hook -> hook.markClaims(loaded.getClaims(), loaded))
            );
        });
    }

    @Override
    public boolean onUserTamedEntityAction(@Nullable Entity player, @NotNull Entity entity) {
        if (player == null || !plugin.isSessionActive()
                || !plugin.getSettings().getPets().isEnabled() || !(entity instanceof TamableAnimal tamed)) {
            return true;
        }

        final Optional<ServerPlayer> source = getPlayerSource(player);
        final Optional<User> owner = plugin.getPetOwner(tamed);
        if (source.isEmpty() || owner.isEmpty()) {
            return true;
        }

        return !plugin.cancelPetOperation(plugin.getOnlineUser(source.get()), owner.get());
    }

    @Override
    @NotNull
    public OperationPosition getPosition(@NotNull Vec3 vec3d, @NotNull net.minecraft.world.level.Level world,
                                         float yaw, float pitch) {
        return FabricHuskClaims.Adapter.adapt(world, vec3d, yaw, pitch);
    }

    @Override
    @NotNull
    public OperationUser getUser(@NotNull Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            throw new IllegalArgumentException("Claims are only checked for server-side players");
        }
        return plugin.getOnlineUser(serverPlayer);
    }

    @Override
    public void setInspectionDistance(int i) {
        throw new UnsupportedOperationException("Cannot change inspection distance");
    }

}
