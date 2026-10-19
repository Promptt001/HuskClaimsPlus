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

import net.william278.huskclaims.trust.TrustLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.HuskClaims;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure "Everyone" public-access ladder for the Friends &amp; Trust hub.
 * No Bukkit, no plugin: unit-tested in common.
 *
 * <p>The {@code #public} tag holds exactly ONE level. A tile is ON (that level is
 * the public level), INCLUDED (a higher public level grants everything it does),
 * OFF, or LOCKED (cannot be public). Clicking ON/INCLUDED lowers public access to
 * the highest candidate strictly below the clicked level, or revokes; clicking OFF
 * raises to the clicked level. Invariant (tested): a click always flips the clicked
 * tile between green (ON/INCLUDED) and red (OFF).</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class PublicLadder {

    public enum State { ON, INCLUDED, OFF, LOCKED }

    public enum Kind { GRANT, REVOKE, NONE }

    /** What a click does: GRANT {@code level}, REVOKE the tag, or NONE. */
    public record Action(@NotNull Kind kind, @Nullable TrustLevel level) {

        static final Action NONE = new Action(Kind.NONE, null);
        static final Action REVOKE = new Action(Kind.REVOKE, null);

        @NotNull
        static Action grant(@NotNull TrustLevel l) {
            return new Action(Kind.GRANT, l);
        }
    }

    private PublicLadder() {
    }

    /**
     * The public actions the config allows, in order, as operation types.
     * Unknown names warn once and are skipped (a typo in config.yml must not
     * hide the rest of the list).
     *
     * @param plugin the plugin, for settings and warnings
     * @return the allowed operation types, in configured order
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static List<OperationType> allowedOperations(@NotNull HuskClaims plugin) {
        final List<OperationType> ops = new ArrayList<>();
        for (String name : plugin.getSettings().getGui().getPublicPermissions().getAllowed()) {
            try {
                ops.add(OperationType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                GuiDiagnostics.warnOnce(plugin, "unknown-public-action:" + name,
                        "Unknown operation type in gui.public_permissions.allowed: " + name);
            }
        }
        return ops;
    }


    /**
     * Levels that may be public: grantable by command (has an alias),
     * no privileges, weight at or below the ceiling; ascending weight.
     *
     * @param all            every configured trust level
     * @param ceilingWeight  the configured public-access maximum weight
     *                       ({@code gui.public_access.max_level})
     * @return candidate public levels, ascending by weight
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static List<TrustLevel> candidates(@NotNull List<TrustLevel> all, int ceilingWeight) {
        return all.stream()
                .filter(l -> !l.getCommandAliases().isEmpty())
                .filter(l -> l.getPrivileges().isEmpty())
                .filter(l -> l.getWeight() <= ceilingWeight)
                .sorted(Comparator.comparingInt(TrustLevel::getWeight))
                .toList();
    }

    /**
     * The rendered state of a level's public tile.
     *
     * @param level      the level of the tile being rendered
     * @param candidates candidate public levels (ascending)
     * @param current    the current public level, if any
     * @return the tile state
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static State state(@NotNull TrustLevel level, @NotNull List<TrustLevel> candidates,
                              @NotNull Optional<TrustLevel> current) {
        if (candidates.stream().noneMatch(c -> c.getId().equals(level.getId()))) {
            return State.LOCKED;
        }
        if (current.isEmpty()) {
            return State.OFF;
        }
        final TrustLevel cur = current.get();
        if (cur.getId().equals(level.getId())) {
            return State.ON;
        }
        return cur.getWeight() > level.getWeight() && cur.getFlags().containsAll(level.getFlags())
                ? State.INCLUDED : State.OFF;
    }

    /**
     * What clicking a level's public tile does, from the live claim state.
     *
     * @param level      the clicked level
     * @param candidates candidate public levels (ascending)
     * @param current    the current public level, if any
     * @return the action to dispatch
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static Action click(@NotNull TrustLevel level, @NotNull List<TrustLevel> candidates,
                               @NotNull Optional<TrustLevel> current) {
        return switch (state(level, candidates, current)) {
            case LOCKED -> Action.NONE;
            case OFF -> Action.grant(level);
            case ON, INCLUDED -> candidates.stream()
                    .filter(c -> c.getWeight() < level.getWeight())
                    .max(Comparator.comparingInt(TrustLevel::getWeight))
                    .map(Action::grant)
                    .orElse(Action.REVOKE);
        };
    }

    /**
     * The {@code #public} level after applying {@code action} — used by tests and
     * by the caller to sanity-check the dispatched command.
     *
     * @param action  the dispatched action
     * @param current the current public level, if any
     * @return the expected public level afterwards
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static Optional<TrustLevel> after(@NotNull Action action, @NotNull Optional<TrustLevel> current) {
        return switch (action.kind()) {
            case GRANT -> Optional.of(action.level());
            case REVOKE -> Optional.empty();
            case NONE -> current;
        };
    }

}
