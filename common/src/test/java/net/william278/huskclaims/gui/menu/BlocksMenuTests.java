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
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BlocksMenu render and routing tests: the slot map matches the Paper menu
 * item-for-item (icon key from config with fallback, name key, lore key), Back
 * routes to the main menu, Balance closes and runs /claimblocks, and the gift
 * flow chains prompts through the controller.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Blocks Menu (shared model)")
public class BlocksMenuTests {

    private GuiTestKit kit;
    private Settings.GuiSettings.BlocksMenuSettings cfg;
    private BlocksMenu menu;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        cfg = kit.settings.getGui().getBlocksMenu();
        menu = new BlocksMenu(kit.controller);
    }

    @Test
    @DisplayName("Render: every button is the configured/default icon with its name and lore keys")
    public void testRenderMatchesPaperSlots() {
        final Map<Integer, MenuIcon> icons = menu.icons();

        assertEquals(4, icons.size());
        assertEquals("minecraft:gold_nugget", icons.get(cfg.getBalanceSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_blocks_balance"), icons.get(cfg.getBalanceSlot()).name());
        assertEquals(java.util.List.of(GuiTestKit.lore("gui_blocks_balance_lore")),
                icons.get(cfg.getBalanceSlot()).lore());

        assertEquals("minecraft:emerald", icons.get(cfg.getBuySlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_blocks_buy"), icons.get(cfg.getBuySlot()).name());

        assertEquals("minecraft:player_head", icons.get(cfg.getGiftSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_blocks_gift"), icons.get(cfg.getGiftSlot()).name());

        assertEquals("minecraft:arrow", icons.get(cfg.getBackSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_back"), icons.get(cfg.getBackSlot()).name());
        assertEquals(java.util.List.of(GuiTestKit.lore("gui_back_lore")), icons.get(cfg.getBackSlot()).lore());

        assertTrue(menu.rows() >= 1 && menu.rows() <= 6);
        assertEquals("blocks", menu.typeName());
    }

    @Test
    @DisplayName("Invalid configured icon falls back to the default (never a barrier)")
    public void testInvalidIconFallsBack() throws Exception {
        final var field = net.william278.huskclaims.config.Settings.GuiSettings.class
                .getDeclaredField("iconMaterials");
        field.setAccessible(true);
        field.set(kit.settings.getGui(), new java.util.HashMap<>(
                java.util.Map.of("blocks.balance", "minecraft:not_an_item")));
        final Map<Integer, MenuIcon> icons = new BlocksMenu(kit.controller).icons();

        assertEquals("minecraft:gold_nugget", icons.get(cfg.getBalanceSlot()).itemKey(),
                "an invalid configured icon must render the fallback, not a barrier");
    }

    @Test
    @DisplayName("Back opens the main menu")
    public void testBackGoesToMainMenu() {
        menu.onClick(kit.user(), cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("main", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Balance: closes the menu, then runs /claimblocks as the user")
    public void testBalanceClosesThenRunsCommand() {
        final GuiTestKit.RecordingCommand claimblocks = kit.command("claimblocks");
        menu.onClick(kit.user(), cfg.getBalanceSlot(), ClickKind.LEFT);

        assertEquals(1, kit.host.closed.size(), "balance closes the menu (chat output is the confirmation)");
        assertEquals(1, claimblocks.executedWith.size());
        assertEquals("", claimblocks.executedWith.getFirst());
    }

    @Test
    @DisplayName("Buy with no permission: refuses before prompting")
    public void testBuyRefusedWithoutPermission() {
        // no buyclaimblocks command registered → mayUse false → permission error message
        menu.onClick(kit.user(), cfg.getBuySlot(), ClickKind.LEFT);
        assertEquals(0, kit.host.prompts.size(), "no prompt may open when permission fails");
        assertEquals(0, kit.host.opened.size());
    }

    @Test
    @DisplayName("Gift: player prompt opens; valid name chains to the amount prompt")
    public void testGiftPromptsChain() {
        kit.command("claimblocks");
        menu.onClick(kit.user(), cfg.getGiftSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.prompts.size());
        assertEquals("gui_prompt_player", kit.host.prompts.getFirst().titleKey());

        // valid player name → the amount prompt follows
        kit.host.prompts.getFirst().onSubmit().accept("William278");
        assertEquals(2, kit.host.prompts.size());
        assertEquals("gui_prompt_amount", kit.host.prompts.get(1).titleKey());

        // valid amount → the confirm view opens, nothing executed yet
        kit.host.prompts.get(1).onSubmit().accept("500");
        assertEquals(1, kit.host.opened.size());
        assertEquals("confirm", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Gift: invalid player name is rejected without chaining")
    public void testGiftInvalidPlayerRejected() {
        kit.command("claimblocks");
        menu.onClick(kit.user(), cfg.getGiftSlot(), ClickKind.LEFT);
        kit.host.prompts.getFirst().onSubmit().accept("bad name!");
        assertEquals(1, kit.host.prompts.size(), "invalid name must not chain to the amount prompt");
    }

    @Test
    @DisplayName("Gift without the gift permission asks for nothing")
    public void testGiftWithoutPermissionPromptsNothing() {
        // /claimblocks <player> gift <amount> checks huskclaims.command.claimblocks.gift,
        // which is operator-only by default. Answering two prompts and a Confirm only to
        // be told "no permission" is the exact defect a non-op play session found.
        kit.command("claimblocks");
        final GuiTestKit.FakeOnlineUser noGift = new GuiTestKit.FakeOnlineUser(kit.plugin) {
            @Override
            public boolean hasPermission(@org.jetbrains.annotations.NotNull String node) {
                return !node.equals("huskclaims.command.claimblocks.gift")
                        && !node.equals("huskclaims.command.claimblocks.*");
            }

            @Override
            public boolean hasPermission(@org.jetbrains.annotations.NotNull String node, boolean isDefault) {
                return hasPermission(node);
            }
        };

        menu.onClick(noGift, cfg.getGiftSlot(), ClickKind.LEFT);

        assertEquals(0, kit.host.prompts.size(),
                "no player/amount prompt for a player who cannot gift");
        assertEquals(0, kit.host.opened.size(), "no confirm view either");
    }

    @Test
    @DisplayName("Gift with the gift permission still prompts (the preflight is not a blanket refusal)")
    public void testGiftWithPermissionStillPrompts() {
        kit.command("claimblocks");
        menu.onClick(kit.user(), cfg.getGiftSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.prompts.size());
        assertEquals("gui_prompt_player", kit.host.prompts.getFirst().titleKey());
    }

    @Test
    @DisplayName("Gift: invalid amount re-asks at most three times, then stops")
    public void testGiftAmountReAskCapped() {
        kit.command("claimblocks");
        menu.onClick(kit.user(), cfg.getGiftSlot(), ClickKind.LEFT);
        kit.host.prompts.getFirst().onSubmit().accept("William278");

        kit.host.prompts.get(1).onSubmit().accept("abc");
        kit.host.prompts.get(2).onSubmit().accept("abc");
        kit.host.prompts.get(3).onSubmit().accept("abc");
        assertEquals(4, kit.host.prompts.size(), "initial prompt + 3 attempts");
        assertEquals(0, kit.host.opened.size(), "no confirm after the cap");
    }

    @Test
    @DisplayName("Gift: confirmed values reach the command exactly once")
    public void testGiftConfirmedRunsCommand() {
        final GuiTestKit.RecordingCommand claimblocks = kit.command("claimblocks");
        menu.onClick(kit.user(), cfg.getGiftSlot(), ClickKind.LEFT);
        kit.host.prompts.getFirst().onSubmit().accept("William278");
        final var amountPrompt = kit.host.prompts.get(1);
        amountPrompt.onSubmit().accept("500");

        final ConfirmView confirm = (ConfirmView) kit.host.last();
        confirm.onClick(kit.user(),
                kit.settings.getGui().getConfirmMenu().getConfirmSlot(), ClickKind.LEFT);
        assertEquals(1, claimblocks.executedWith.size());
        assertEquals("William278|gift|500", claimblocks.executedWith.getFirst());
    }

}
