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

import com.google.gson.annotations.Expose;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * A public teleport anchor ("claim warp") attached to a {@link Claim}.
 * <p>
 * A claim has at most one warp. A claim without a warp ({@code null}) behaves
 * exactly as stock HuskClaims.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClaimWarp {

    /**
     * Unique, case-insensitive name used for {@code /visit <name>} lookup
     */
    @Expose
    @Nullable
    private String name;

    /**
     * The UUID of the user who created the warp (used for attribution and list sorting)
     */
    @Expose
    @Nullable
    private UUID setter;

    /**
     * The exact anchor position (world, coordinates, yaw, pitch) players teleport to
     */
    @Expose
    @Nullable
    private Position anchor;

    /**
     * Whether everyone may use this warp, or just the owner and players with
     * Build/Manage-capable trust. Nullable wrapper: warps saved before this
     * field existed deserialize as {@code null} and are treated as public
     * (the original behavior).
     */
    @Expose
    @Nullable
    private Boolean publiclyAccessible;

    private ClaimWarp(@Nullable String name, @Nullable UUID setter, @Nullable Position anchor) {
        this.name = name;
        this.setter = setter;
        this.anchor = anchor;
    }

    @NotNull
    public static ClaimWarp of(@NotNull String name, @NotNull User setter, @NotNull Position anchor) {
        return new ClaimWarp(name, setter.getUuid(), anchor);
    }

    /**
     * Returns the warp name, or {@code "unnamed"} if unset (defensive; names are required at set-time)
     */
    @NotNull
    public String getName() {
        return name == null ? "unnamed" : name;
    }

    /**
     * Returns the UUID of the user who set the warp
     *
     * @throws IllegalStateException if the setter is unknown (corrupted data)
     */
    @NotNull
    public UUID getSetter() {
        if (setter == null) {
            throw new IllegalStateException("Claim warp setter is not set");
        }
        return setter;
    }

    /**
     * Returns whether everyone can use this warp. Warps with no saved value
     * (created before warp privacy existed) default to public.
     *
     * @return whether the warp is publicly accessible
     * @since HuskClaimsPlus 1.0.0
     */
    public boolean isPubliclyAccessible() {
        return publiclyAccessible == null || publiclyAccessible;
    }

    /**
     * Sets whether everyone can use this warp, or only the owner and
     * Build/Manage-capable trusted players.
     *
     * @param publiclyAccessible whether the warp is publicly accessible
     * @since HuskClaimsPlus 1.0.0
     */
    public void setPubliclyAccessible(boolean publiclyAccessible) {
        this.publiclyAccessible = publiclyAccessible;
    }

    /**
     * Returns the anchor position of the warp
     *
     * @throws IllegalStateException if the anchor is not set (corrupted data)
     */
    @NotNull
    public Position getAnchor() {
        if (anchor == null) {
            throw new IllegalStateException("Claim warp anchor is not set");
        }
        return anchor;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ClaimWarp warp && warp.getName().equalsIgnoreCase(getName());
    }

    @Override
    public int hashCode() {
        // Locale.ROOT: the default locale would make "I" a dotless i on a Turkish server, breaking equals/hashCode agreement
        return getName().toLowerCase(Locale.ROOT).hashCode();
    }

}
