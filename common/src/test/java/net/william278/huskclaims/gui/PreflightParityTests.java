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

import net.william278.huskclaims.gui.menu.BlocksMenu;
import net.william278.huskclaims.gui.model.ClickKind;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariant the Claim Menu must never break: <em>the menu never asks for
 * input the command will refuse, and never asks for more than the command
 * checks.</em> The first half ("never grants what the command would not") holds
 * because every button dispatches the real command, which re-checks; the second
 * half leaked once — Gift preflighted the base {@code claimblocks} node (or, in
 * the first fix attempt, only the sub-node) while the command checks both, so a
 * normal player could answer two prompts and a Confirm before being refused.
 * <p>
 * This table is the contract. Each row names a menu button, the command it
 * dispatches and the exact nodes that command checks. For every row, a user
 * missing exactly one of those nodes must get no input prompt and no confirm
 * view — never a prompt the command would refuse.
 * <p>
 * Add a row whenever a button dispatches a command that checks a sub-node
 * (derived as {@code huskclaims.command.<name>.<sub>}).
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Claim Menu preflight parity")
class PreflightParityTests {

    /**
     * @param promptsForInput whether the button asks the player for input at all; a
     *                        read-only button (balance) only dispatches, and the
     *                        command's own refusal is a perfectly good answer there
     */
    private record Row(String button, String command, List<String> nodes, boolean promptsForInput) {
    }

    private static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of(new Row("blocks.gift", "claimblocks", List.of("gift"), true)),
                Arguments.of(new Row("blocks.buy", "buyclaimblocks", List.of(), true)),
                Arguments.of(new Row("blocks.balance", "claimblocks", List.of(), false))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    @DisplayName("A button never prompts for input the dispatched command would refuse")
    void buttonPreflightsTheCommandItDispatches(@NotNull Row row) {
        final List<String> nodes = nodeNames(row);
        for (final String missing : nodes) {
            final GuiTestKit kit = new GuiTestKit();
            kit.command(row.command());
            final BlocksMenu menu = new BlocksMenu(kit.controller);
            final int slot = slotFor(kit, row.button());

            menu.onClick(userMissing(kit, nodes, missing), slot, ClickKind.LEFT);

            if (row.promptsForInput()) {
                assertEquals(0, kit.host.prompts.size(),
                        row.button() + ": must not prompt for input when " + missing + " is missing");
                assertEquals(0, kit.host.opened.size(),
                        row.button() + ": must not open a confirm view when " + missing + " is missing");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    @DisplayName("A fully permitted user still reaches the button's action")
    void fullyPermittedUserStillActs(@NotNull Row row) {
        final GuiTestKit kit = new GuiTestKit();
        kit.command(row.command());
        final BlocksMenu menu = new BlocksMenu(kit.controller);
        final int slot = slotFor(kit, row.button());

        menu.onClick(kit.user(), slot, ClickKind.LEFT);

        final boolean reached = kit.host.prompts.size() > 0 || kit.host.opened.size() > 0
                || kit.host.closed.size() > 0;
        assertTrue(reached, row.button() + ": a fully permitted user must reach the prompt or the dispatch");
    }

    private static List<String> nodeNames(@NotNull Row row) {
        final List<String> nodes = new ArrayList<>();
        nodes.add("huskclaims.command." + row.command());
        row.nodes().forEach(sub -> nodes.add("huskclaims.command." + row.command() + "." + sub));
        return nodes;
    }

    private static int slotFor(@NotNull GuiTestKit kit, @NotNull String button) {
        final var blocks = kit.settings.getGui().getBlocksMenu();
        return switch (button) {
            case "blocks.gift" -> blocks.getGiftSlot();
            case "blocks.buy" -> blocks.getBuySlot();
            case "blocks.balance" -> blocks.getBalanceSlot();
            default -> throw new IllegalArgumentException("no slot mapping for " + button);
        };
    }

    /**
     * A user holding exactly the nodes the button's command checks, except {@code denied}.
     * No wildcard is granted: a player holding {@code claimblocks.gift} does not hold
     * {@code claimblocks} or {@code claimblocks.*}, and the fixture must not pretend
     * otherwise or it would hide a missing base-node preflight.
     */
    private static GuiTestKit.FakeOnlineUser userMissing(@NotNull GuiTestKit kit, @NotNull List<String> held,
                                                         @NotNull String denied) {
        return new GuiTestKit.FakeOnlineUser(kit.plugin) {
            @Override
            public boolean hasPermission(@NotNull String node) {
                return !node.equals(denied) && held.contains(node);
            }

            @Override
            public boolean hasPermission(@NotNull String node, boolean isDefault) {
                return hasPermission(node);
            }
        };
    }

}
