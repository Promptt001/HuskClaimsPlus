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

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.trust.TrustLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Pure ability-bullet computation for trust levels: group a
 * level's {@link OperationType} flags and {@link TrustLevel.Privilege}s into
 * short, de-duplicated phrases, max 6 bullets plus an "…and k more" line.
 *
 * <p>Returns bullet <b>phrase keys</b> (locale keys like {@code
 * gui_ability_doors}), so it is unit-testable in {@code common} without
 * Bukkit; the menu renders them via {@code gui_trust_bullet}.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class LevelBullets {

    private static final int MAX_BULLETS = 6;

    /** op → phrase key (de-duplicated per level); unknown ops fall back to gui_flag_<op>. */
    private static final Map<OperationType, String> ABILITY_GROUP = buildAbilityGroups();

    private LevelBullets() {
    }

    @NotNull
    private static Map<OperationType, String> buildAbilityGroups() {
        final Map<OperationType, String> map = new LinkedHashMap<>();
        map.put(OperationType.BLOCK_INTERACT, "gui_ability_doors");
        map.put(OperationType.REDSTONE_INTERACT, "gui_ability_redstone");
        map.put(OperationType.ENTITY_INTERACT, "gui_ability_entities");
        map.put(OperationType.PLACE_VEHICLE, "gui_ability_vehicles");
        map.put(OperationType.BREAK_VEHICLE, "gui_ability_vehicles");
        map.put(OperationType.ENDER_PEARL_TELEPORT, "gui_ability_pearls");
        map.put(OperationType.START_RAID, "gui_ability_raids");
        map.put(OperationType.CONTAINER_OPEN, "gui_ability_containers");
        map.put(OperationType.BLOCK_BREAK, "gui_ability_build");
        map.put(OperationType.BLOCK_PLACE, "gui_ability_build");
        map.put(OperationType.FARM_BLOCK_BREAK, "gui_ability_farming");
        map.put(OperationType.FARM_BLOCK_PLACE, "gui_ability_farming");
        map.put(OperationType.FARM_BLOCK_INTERACT, "gui_ability_farming");
        map.put(OperationType.PLACE_HANGING_ENTITY, "gui_ability_hanging");
        map.put(OperationType.BREAK_HANGING_ENTITY, "gui_ability_hanging");
        map.put(OperationType.PLAYER_DAMAGE_MONSTER, "gui_ability_mobs");
        map.put(OperationType.PLAYER_DAMAGE_ENTITY, "gui_ability_mobs");
        map.put(OperationType.PLAYER_DAMAGE_PERSISTENT_ENTITY, "gui_ability_mobs");
        map.put(OperationType.PLAYER_DAMAGE_PLAYER, "gui_ability_pvp");
        map.put(OperationType.FILL_BUCKET, "gui_ability_buckets");
        map.put(OperationType.EMPTY_BUCKET, "gui_ability_buckets");
        map.put(OperationType.USE_SPAWN_EGG, "gui_ability_spawn_eggs");
        return map;
    }

    /** privilege → phrase key. */
    private static final Map<TrustLevel.Privilege, String> PRIVILEGE_PHRASE = buildPrivilegePhrases();

    @NotNull
    private static Map<TrustLevel.Privilege, String> buildPrivilegePhrases() {
        final Map<TrustLevel.Privilege, String> map = new LinkedHashMap<>();
        map.put(TrustLevel.Privilege.MANAGE_TRUSTEES, "gui_privilege_trustees");
        map.put(TrustLevel.Privilege.MANAGE_CHILD_CLAIMS, "gui_privilege_children");
        map.put(TrustLevel.Privilege.MANAGE_OPERATION_GROUPS, "gui_privilege_flags");
        map.put(TrustLevel.Privilege.MANAGE_BANS, "gui_privilege_bans");
        map.put(TrustLevel.Privilege.MAKE_PRIVATE, "gui_privilege_private");
        map.put(TrustLevel.Privilege.MANAGE_WARP, "gui_privilege_warp");
        return map;
    }

    /**
     * Bullet phrase keys for a level: grouped flag phrases (de-duplicated,
     * insertion order), then privilege phrases; truncated to {@value
     * #MAX_BULLETS} with a trailing {@code gui_trust_more} entry when needed.
     *
     * @param level the trust level
     * @return locale phrase keys for the ability bullets
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static List<String> of(@NotNull TrustLevel level) {
        final List<String> phrases = new ArrayList<>();
        for (OperationType flag : level.getFlags()) {
            final String phrase = ABILITY_GROUP.getOrDefault(
                    flag, "gui_flag_" + flag.name().toLowerCase(Locale.ROOT));
            if (!phrases.contains(phrase)) {
                phrases.add(phrase);
            }
        }
        for (TrustLevel.Privilege privilege : level.getPrivileges()) {
            final String phrase = PRIVILEGE_PHRASE.get(privilege);
            if (phrase != null && !phrases.contains(phrase)) {
                phrases.add(phrase);
            }
        }
        if (phrases.size() > MAX_BULLETS) {
            final List<String> truncated = new ArrayList<>(phrases.subList(0, MAX_BULLETS));
            truncated.add("gui_trust_more:" + (phrases.size() - MAX_BULLETS));
            return truncated;
        }
        return phrases;
    }

}
