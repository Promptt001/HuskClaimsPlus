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
import net.william278.huskclaims.hook.EconomyHook;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manage Claim Blocks: show the balance, buy blocks, or give some to another player. Each
 * button runs the matching upstream command. Buying and gifting ask for the amount (and the
 * player) in chat first, and nothing is spent until the player clicks Confirm.
 *
 * <p>The menu is a platform-free {@link MenuView}: rendered on Bukkit through
 * {@code BukkitMenuAdapter}, on Fabric through the sgui host.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class BlocksMenu implements MenuView {

    private final GuiController controller;

    public BlocksMenu(@NotNull GuiController controller) {
        this.controller = controller;
    }

    @NotNull
    @Override
    public String typeName() {
        return "blocks";
    }

    @Override
    public int rows() {
        final Settings.GuiSettings.BlocksMenuSettings cfg = settings().getBlocksMenu();
        return GuiLayout.rowsFor(cfg.getRows(), cfg.getBalanceSlot(), cfg.getBuySlot(),
                cfg.getGiftSlot(), cfg.getBackSlot());
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Settings.GuiSettings.BlocksMenuSettings cfg = settings().getBlocksMenu();
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        items.put(cfg.getBalanceSlot(), c().item("blocks.balance", "minecraft:gold_nugget")
                .name("gui_blocks_balance").lore("gui_blocks_balance_lore").build());
        items.put(cfg.getBuySlot(), c().item("blocks.buy", "minecraft:emerald")
                .name("gui_blocks_buy").lore("gui_blocks_buy_lore").build());
        items.put(cfg.getGiftSlot(), c().item("blocks.gift", "minecraft:player_head")
                .name("gui_blocks_gift").lore("gui_blocks_gift_lore").build());
        items.put(cfg.getBackSlot(), c().item("blocks.back", "minecraft:arrow")
                .name("gui_back").lore("gui_back_lore").build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final Settings.GuiSettings.BlocksMenuSettings cfg = settings().getBlocksMenu();
        if (slot == cfg.getBackSlot()) {
            c().openMainMenu(user);
            return;
        }
        if (slot == cfg.getBalanceSlot()) {
            // Read-only: output IS the confirmation
            c().host().close(user);
            c().executeCommand(user, "claimblocks");
            return;
        }
        if (slot == cfg.getBuySlot()) {
            // Check the economy hook itself, not just the config flag (the flag
            // stays true when no economy plugin is installed)
            if (c().getPlugin().getHook(EconomyHook.class).isEmpty()) {
                c().host().close(user);
                c().getPlugin().getLocales().getLocale("gui_error_no_economy")
                        .ifPresent(user::sendMessage);
                return;
            }
            // /buyclaimblocks <amount>: a typo gets "try again" here, not a command syntax error after Confirm
            c().requestAndConfirm(user, "buyclaimblocks",
                    "gui_prompt_amount", net.william278.huskclaims.gui.GuiValidators.positiveAmount(),
                    "gui_input_invalid_number", "gui_action_buy_blocks",
                    u -> c().openBlocksMenu(u));
            return;
        }
        if (slot == cfg.getGiftSlot()) {
            // /claimblocks <player> gift <amount>: chained prompts (player, then amount),
            // each validated before the next step, one final confirm click. The re-ask
            // is capped so a confused player is not trapped in the loop.
            //
            // ClaimBlockOption.GIFT checks huskclaims.command.claimblocks.gift, which is
            // operator-only by default (upstream). Ask the command itself before prompting:
            // a player who cannot gift must never answer two prompts and a Confirm first.
            if (!c().mayUse(user, "claimblocks") || !c().mayUse(user, "claimblocks", "gift")) {
                c().getPlugin().getLocales().getLocale("gui_error_no_permission")
                        .ifPresent(user::sendMessage);
                return;
            }
            final boolean allowPrefix = settings().isAllowNamePrefix();
            c().requestInput(user, "gui_prompt_player", playerName -> {
                if (net.william278.huskclaims.gui.GuiValidators.playerName(allowPrefix).apply(playerName.trim()).isEmpty()) {
                    c().getPlugin().getLocales().getLocale("gui_input_invalid_player", playerName)
                            .ifPresent(user::sendMessage);
                    return;
                }
                askAmount(user, playerName.trim(), 3);
            });
        }
    }

    private void askAmount(@NotNull OnlineUser user, @NotNull String playerName, int attemptsLeft) {
        c().requestInput(user, "gui_prompt_amount", amount -> {
            if (net.william278.huskclaims.gui.GuiValidators.positiveAmount().apply(amount.trim()).isEmpty()) {
                c().getPlugin().getLocales().getLocale("gui_input_invalid_number", amount)
                        .ifPresent(user::sendMessage);
                if (attemptsLeft > 1) {
                    askAmount(user, playerName, attemptsLeft - 1);
                }
                return;
            }
            c().confirmAndExecute(user, "claimblocks",
                    new String[]{playerName, "gift", amount.trim()},
                    "gui_action_gift_blocks",
                    playerName + " — " + amount.trim(),
                    u -> c().openBlocksMenu(u));
        });
    }

    @NotNull
    private GuiController c() {
        return controller;
    }

    private Settings.GuiSettings settings() {
        return c().getPlugin().getSettings().getGui();
    }

}
