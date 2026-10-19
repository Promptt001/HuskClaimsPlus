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
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.user.OnlineUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfirmView render and routing tests: the info item shows the action name,
 * action lore and the typed value literally; Confirm closes then runs; Cancel
 * returns and never confirms.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Confirm View (shared model)")
public class ConfirmViewTests {

    private GuiTestKit kit;
    private Settings.GuiSettings.ConfirmMenuSettings cfg;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        cfg = kit.settings.getGui().getConfirmMenu();
    }

    @Test
    @DisplayName("actionInfo: book icon, action name+lore keys, value filled literally")
    public void testActionInfoRendersWithLiteralValue() {
        final MenuIcon info = kit.controller.actionInfo("gui_action_buy_blocks", "500");

        assertEquals("minecraft:book", info.itemKey());
        assertEquals(GuiTestKit.name("gui_action_buy_blocks"), info.name());
        assertEquals(java.util.List.of(
                GuiTestKit.lore("gui_action_buy_blocks_lore"),
                GuiTestKit.lore("gui_confirm_value", "500")), info.lore());
    }

    @Test
    @DisplayName("Confirm closes the menu, then runs exactly once; Cancel only returns")
    public void testConfirmClosesThenRunsCancelReturns() {
        final AtomicInteger confirmed = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();
        final ConfirmView view = new ConfirmView(kit.controller,
                kit.controller.actionInfo("k", "v"),
                u -> confirmed.incrementAndGet(),
                u -> cancelled.incrementAndGet());
        final OnlineUser user = kit.user();

        view.onClick(user, cfg.getConfirmSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.closed.size(), "Confirm must close the menu first");
        assertEquals(1, confirmed.get());

        view.onClick(user, cfg.getCancelSlot(), ClickKind.LEFT);
        assertEquals(1, cancelled.get());
        assertEquals(1, confirmed.get(), "cancel never confirms");
    }

    @Test
    @DisplayName("Caller info item wins over the default buttons (same precedence as Paper)")
    public void testCallerInfoWins() {
        final MenuIcon custom = kit.controller.item("minecraft:tnt").name("gui_action_unclaim").build();
        final ConfirmView view = new ConfirmView(kit.controller, custom, u -> { }, u -> { });

        final Map<Integer, MenuIcon> icons = view.icons();
        assertEquals(custom, icons.get(cfg.getInfoSlot()));
        assertEquals("minecraft:lime_concrete", icons.get(cfg.getConfirmSlot()).itemKey());
        assertEquals("minecraft:red_concrete", icons.get(cfg.getCancelSlot()).itemKey());
        assertTrue(view.rows() >= 1 && view.rows() <= 6);
        assertEquals("confirm", view.typeName());
        assertEquals(Optional.empty(), view.title());
    }

    @Test
    @DisplayName("Non-button slots do nothing")
    public void testFillerSlotsInert() {
        final AtomicInteger confirmed = new AtomicInteger();
        final ConfirmView view = new ConfirmView(kit.controller,
                kit.controller.actionInfo("k", "v"), u -> confirmed.incrementAndGet(), u -> { });

        view.onClick(kit.user(), 0, ClickKind.LEFT);
        view.onClick(kit.user(), 3, ClickKind.RIGHT);
        assertEquals(0, confirmed.get());
        assertEquals(0, kit.host.closed.size());
    }

}
