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

/**
 * An edge of a claim the resize menus can move, or all sides at once.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public enum ResizeDirection {
    NORTH, SOUTH, WEST, EAST, ALL;

    /** Whether moving this edge changes the claim's Z extent. */
    public boolean isZAxis() {
        return this == NORTH || this == SOUTH;
    }

    /** Whether moving this edge changes the claim's X extent. */
    public boolean isXAxis() {
        return this == WEST || this == EAST;
    }

    /** Locale suffix for this direction, e.g. {@code north}. */
    public String localeSuffix() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
