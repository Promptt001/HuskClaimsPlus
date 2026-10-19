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

package net.william278.huskclaims.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jetbrains.annotations.NotNull;

/**
 * Record wrapper for holding Minecraft positional data
 *
 * @param world the {@link ServerLevel} of the position
 * @param pos   the double-accuracy {@link Vec3 vector positional coordinates}
 * @param yaw   the yaw angle at the position
 * @param pitch the pitch angle at the position
 * @since 1.5
 */
public record Location(@NotNull ServerLevel world, @NotNull Vec3 pos, float yaw, float pitch) {

    public Location(@NotNull Entity entity) {
        this((ServerLevel) entity.level(), entity.position(), entity.getYRot(), entity.getXRot());
    }

    public Location(@NotNull ServerLevel world, @NotNull BlockPos pos) {
        this(world, Vec3.atCenterOf(pos), 0.0f, 0.0f);
    }

    public Location(@NotNull ServerLevel world, double x, double y, double z, float yaw, float pitch) {
        this(world, new Vec3(x, y, z), yaw, pitch);
    }

    /**
     * Get the {@link BlockPos}
     *
     * @return the blockPos, floored from the {@link #pos() vector positional coordinates}
     * @since 1.5
     */
    @NotNull
    public BlockPos blockPos() {
        return BlockPos.containing(pos);
    }

    /**
     * Get a {@link TeleportTransition} from this position
     *
     * @return the teleportTarget
     * @since 1.5
     */
    @NotNull
    public TeleportTransition teleportTarget() {
        return new TeleportTransition(world, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING);
    }

}
