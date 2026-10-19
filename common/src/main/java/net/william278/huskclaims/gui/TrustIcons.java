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

/**
 * Pure trust-level icon fallback logic: choose by <b>id first, then
 * by capability</b> — never by weight arithmetic, which is off by one
 * (Access→chest, Container→iron pickaxe, Build/Manage→diamond).
 *
 * <p>Returns a material <b>name</b> string so it is unit-testable without
 * Bukkit. The four default ids get the exact user-approved category icons:
 * Access = door, Container = chest, Build = bricks, Manage = golden helmet.
 * Custom levels are inferred from what they allow.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class TrustIcons {

    private TrustIcons() {
    }

    /**
     * Fallback icon material name for a trust level.
     *
     * @param level the trust level
     * @return a Bukkit material name (e.g. {@code "OAK_DOOR"})
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String fallbackIcon(@NotNull TrustLevel level) {
        switch (level.getId()) {
            case "access":    return "OAK_DOOR";
            case "container": return "CHEST";
            case "build":     return "BRICKS";
            case "manage":    return "GOLDEN_HELMET";
            default:          break;
        }
        // Custom levels: infer from what they allow (most powerful signal first)
        if (!level.getPrivileges().isEmpty()) {
            return "GOLDEN_HELMET";
        }
        if (level.getFlags().contains(OperationType.BLOCK_PLACE)) {
            return "BRICKS";
        }
        if (level.getFlags().contains(OperationType.CONTAINER_OPEN)) {
            return "CHEST";
        }
        if (level.getFlags().contains(OperationType.REDSTONE_INTERACT)) {
            return "LEVER";
        }
        return "OAK_DOOR";
    }

}
