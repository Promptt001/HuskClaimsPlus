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

package net.william278.huskclaims.gui.menu;

import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Confirm or cancel one action. The Confirm click is the final action; nothing
 * runs before it. Caller-supplied info items win over the default buttons, the
 * same precedence the accepted Paper confirm menu had.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ConfirmView implements MenuView {

    private final GuiController c;
    private final MenuIcon info;
    private final Consumer<OnlineUser> onConfirm;
    private final Consumer<OnlineUser> onCancel;

    public ConfirmView(@NotNull GuiController c, @NotNull MenuIcon info,
                       @NotNull Consumer<OnlineUser> onConfirm, @NotNull Consumer<OnlineUser> onCancel) {
        this.c = c;
        this.info = info;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @NotNull
    @Override
    public String typeName() {
        return "confirm";
    }

    @Override
    public int rows() {
        final Settings.GuiSettings.ConfirmMenuSettings s = settings();
        return GuiLayout.rowsFor(s.getRows(), s.getInfoSlot(), s.getConfirmSlot(), s.getCancelSlot());
    }

    @NotNull
    @Override
    public Optional<String> title() {
        return Optional.empty();
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Settings.GuiSettings.ConfirmMenuSettings s = settings();
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        items.put(s.getInfoSlot(), info);
        items.putIfAbsent(s.getConfirmSlot(), c.item("minecraft:lime_concrete")
                .name("gui_confirm").lore("gui_confirm_lore").build());
        items.putIfAbsent(s.getCancelSlot(), c.item("minecraft:red_concrete")
                .name("gui_cancel").lore("gui_cancel_lore").build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final Settings.GuiSettings.ConfirmMenuSettings s = settings();
        if (slot == s.getConfirmSlot()) {
            c.host().close(user);
            onConfirm.accept(user);
        } else if (slot == s.getCancelSlot()) {
            onCancel.accept(user);
        }
    }

    @NotNull
    private Settings.GuiSettings.ConfirmMenuSettings settings() {
        return c.getPlugin().getSettings().getGui().getConfirmMenu();
    }

}
