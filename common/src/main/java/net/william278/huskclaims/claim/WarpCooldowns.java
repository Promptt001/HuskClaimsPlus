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

package net.william278.huskclaims.claim;

import net.william278.huskclaims.HuskClaims;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player visit cooldown shared by {@code /visit} and the Public Claims menu:
 * a visit counts against the cooldown only once the player has been teleported,
 * and only one visit per player runs at a time, so a quick double click can't
 * get past the cooldown while the safety check is still running.
 */
public final class WarpCooldowns {

    private static final Map<UUID, Long> LAST_VISIT = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    private WarpCooldowns() {
    }

    /**
     * Whether the player may visit now (cooldown elapsed or disabled).
     * Does NOT stamp: call {@link #finish} only once the visit has actually happened.
     */
    public static boolean mayVisit(@NotNull HuskClaims plugin, @NotNull UUID player) {
        final float cooldown = plugin.getSettings().getClaims().getWarps().getVisitCooldownSeconds();
        if (cooldown <= 0f) {
            return true;
        }
        final Long last = LAST_VISIT.get(player);
        return last == null || System.currentTimeMillis() - last >= cooldown * 1000L;
    }

    /**
     * Starts a visit if the cooldown has passed and no other visit by this player is still
     * being checked. Every successful call must be followed by {@link #finish}.
     */
    public static boolean tryStart(@NotNull HuskClaims plugin, @NotNull UUID player) {
        return mayVisit(plugin, player) && IN_PROGRESS.add(player);
    }

    /**
     * Ends a visit started with {@link #tryStart}. The cooldown starts only if the player was
     * teleported, and players whose wait has already passed are forgotten, so the map only
     * ever holds players still waiting.
     */
    public static void finish(@NotNull HuskClaims plugin, @NotNull UUID player, boolean teleported) {
        finish(player, teleported);
        if (teleported && plugin.getSettings().getClaims().getWarps().getVisitCooldownSeconds() > 0f) {
            final long cooldown = (long) (plugin.getSettings().getClaims().getWarps().getVisitCooldownSeconds() * 1000L);
            LAST_VISIT.values().removeIf(stamp -> System.currentTimeMillis() - stamp >= cooldown);
        }
    }

    /** Ends a visit started with {@link #tryStart}. The cooldown starts only if the player was teleported. */
    public static void finish(@NotNull UUID player, boolean teleported) {
        if (teleported) {
            LAST_VISIT.put(player, System.currentTimeMillis());
        }
        IN_PROGRESS.remove(player);
    }

    /**
     * Drops the in-progress marker when a player disconnects. The time of their
     * last visit is kept, so reconnecting does not reset the wait between visits.
     */
    public static void onQuit(@NotNull UUID player) {
        IN_PROGRESS.remove(player);
    }

}
