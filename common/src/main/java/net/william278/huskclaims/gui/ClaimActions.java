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

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.command.InClaimCommand;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Runs an in-claim command against a resolved claim (not the claim the player
 * stands in), with the same permission, cooldown and privilege checks as the
 * position-based command. The 4-arg execute performs database lookups, so the
 * whole call runs async. Upstream applies the change two scheduler hops after
 * the command returns, so callers that re-render use {@link #runThen}, which
 * waits until the claim's observed state changed (or a 40-tick timeout, which
 * covers slow database writes and cancelled events).
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimActions {

    /** Ticks to wait for the change before re-rendering anyway (slow database, cancelled event). */
    static final int MAX_SETTLE_TICKS = 40;

    private ClaimActions() {
    }

    /**
     * Dispatch a registered in-claim command against a specific claim for this
     * player. Returns whether it was DISPATCHED (base permission + cooldown
     * passed). Dispatched is NOT applied — the upstream mutation lands two
     * scheduler hops later. Never chain a re-render to this method; use
     * {@link #runThen} instead.
     *
     * @param c           the GUI controller
     * @param user        the clicking player
     * @param ref         the claim reference (re-resolved at call time)
     * @param commandName the registered command name or alias
     * @param args        the command arguments
     * @return whether the command was dispatched (false: rejected pre-dispatch; nothing will change)
     * @since HuskClaimsPlus 1.0.0
     */
    public static boolean run(@NotNull GuiController c, @NotNull OnlineUser user, @NotNull ClaimRef ref,
                              @NotNull String commandName, @NotNull String... args) {
        final HuskClaims plugin = c.getPlugin();
        final Optional<InClaimCommand> command = c.findCommand(commandName)
                .filter(InClaimCommand.class::isInstance)
                .map(InClaimCommand.class::cast);
        if (command.isEmpty() || !command.get().hasPermission(user)) {
            plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return false;
        }
        if (user.isOnCooldown(plugin.getSettings().getCommandCooldownSeconds())) {
            plugin.getLocales().getLocale("error_on_cooldown").ifPresent(user::sendMessage);
            return false;
        }
        plugin.runAsync(() -> ref.resolve(plugin).ifPresentOrElse(resolved -> {
            final InClaimCommand cmd = command.get();
            final Optional<TrustLevel.Privilege> privilege = cmd.getPrivilege();
            if (privilege.isPresent()
                    && !resolved.claim().isPrivilegeAllowed(privilege.get(), user, plugin)
                    && !cmd.hasPermission(user, "other")) {
                plugin.getLocales().getLocale("no_claim_privilege").ifPresent(user::sendMessage);
                return;
            }
            cmd.execute(user, resolved.world(), resolved.claim(), args);
        }, () -> plugin.getLocales().getLocale("gui_claim_gone").ifPresent(user::sendMessage)));
        return true;
    }

    /**
     * Dispatch, then run {@code onSettled} on the player's thread once the
     * claim's observable state changed (or the settle timeout passed, or
     * immediately when the dispatch was rejected — nothing will change). The
     * only sanctioned way to re-render after a claim-scoped action.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    public static void runThen(@NotNull GuiController c, @NotNull OnlineUser user, @NotNull ClaimRef ref,
                               @NotNull String commandName, @NotNull String[] args,
                               @NotNull Runnable onSettled) {
        final Optional<ClaimSnapshot> before = snapshot(c, ref);
        if (!run(c, user, ref, commandName, args)) {
            c.host().runFor(user, onSettled);   // rejected: nothing will change; re-render now
            return;
        }
        settle(c, user, () -> !snapshot(c, ref).equals(before), onSettled, MAX_SETTLE_TICKS);
    }

    /**
     * Poll once per tick, on the player's thread, until {@code settled} or the
     * timeout. A concurrent modification means the async apply is mid-write;
     * look again next tick.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    public static void settle(@NotNull GuiController c, @NotNull OnlineUser user,
                              @NotNull BooleanSupplier settled, @NotNull Runnable then, int ticksLeft) {
        c.getPlugin().runSyncDelayed(() -> c.host().runFor(user, () -> {
            if (!c.host().isOnline(user)) {
                return;
            }
            boolean done;
            try {
                done = settled.getAsBoolean();
            } catch (ConcurrentModificationException e) {
                done = false; // the async apply is mid-write; look again next tick
            }
            if (done || ticksLeft <= 1) {
                then.run();
                return;
            }
            settle(c, user, settled, then, ticksLeft - 1);
        }), Duration.ofMillis(50));
    }

    @NotNull
    static Optional<ClaimSnapshot> snapshot(@NotNull GuiController c, @NotNull ClaimRef ref) {
        return ClaimSnapshot.of(ref.resolve(c.getPlugin()).map(ClaimRef.Resolved::claim).orElse(null));
    }

}
