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

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.position.Position;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;

public interface FabricSafeTeleportProvider extends SafeTeleportProvider {

    List<Predicate<BlockState>> SAFETY_CHECKS = List.of(
            b -> b.canOcclude() || b.getBlock() == Blocks.WATER, BlockState::isAir, BlockState::isAir
    );

    @Override
    @NotNull
    default CompletableFuture<Boolean> isSafeStandingSpot(@NotNull Position position) {
        final ServerLevel world;
        try {
            world = FabricHuskClaims.Adapter.adapt(position.getWorld(), getPlugin().requireServer());
        } catch (IllegalArgumentException | NullPointerException e) {
            getPlugin().log(Level.WARNING, "The warp's world '%s' is not loaded".formatted(
                    position.getWorld().getName()), e);
            return CompletableFuture.completedFuture(false); // the warp's world isn't loaded: not a safe destination
        }

        // Check the blocks on the thread that owns the region; loading one chunk there is acceptable
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Measure from just below the feet, so a slab or carpet counts as the ground
                final BlockPos ground = BlockPos.containing(
                        position.getX(), position.getY() - 0.01, position.getZ());
                final BlockPos feet = ground.relative(Direction.UP);
                final BlockPos head = feet.relative(Direction.UP);
                return isSolidGround(world, ground) && isOpenSpace(world, feet) && isOpenSpace(world, head);
            } catch (RuntimeException e) {
                getPlugin().log(Level.WARNING, "Could not check whether a warp anchor is safe", e);
                return false;
            }
        }, getPlugin().requireServer());
    }

    private static boolean isSolidGround(@NotNull ServerLevel world, @NotNull BlockPos pos) {
        final BlockState state = world.getBlockState(pos);
        // "Solid" = a non-empty collision shape, the closest equivalent of Bukkit's Material#isSolid
        return !state.getCollisionShape(world, pos).isEmpty() && !isHarmful(state);
    }

    private static boolean isOpenSpace(@NotNull ServerLevel world, @NotNull BlockPos pos) {
        final BlockState state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos).isEmpty()
                && state.getFluidState().isEmpty() && !isHarmful(state);
    }

    private static boolean isHarmful(@NotNull BlockState state) {
        final Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && HARMFUL_BLOCK_KEYS.contains(key.toString());
    }

    @Override
    @NotNull
    default CompletableFuture<Optional<Position>> findSafePosition(@NotNull Position position) {
        final Location location = FabricHuskClaims.Adapter.adapt(position, getPlugin().requireServer());
        return CompletableFuture.completedFuture(getSafe(location.world(), location.blockPos()));
    }

    private Optional<Position> getSafe(@NotNull ServerLevel world, @NotNull BlockPos pos) {
        int y = getHighestYAt(world, pos);
        for (Predicate<BlockState> check : SAFETY_CHECKS) {
            if (!check.test(world.getBlockState(pos.atY(y)))) {
                return Optional.empty();
            }
            y++;
        }
        if (pos.getY() >= world.getHeight()) {
            return Optional.empty();
        }
        return Optional.of(FabricHuskClaims.Adapter.adapt(world, Vec3.atCenterOf(pos.atY(y - 1)), 0f, 0f));
    }

    private int getHighestYAt(@NotNull ServerLevel blockView, @NotNull BlockPos pos) {
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), blockView.getHeight(), pos.getZ());
        while (blockView.getBlockState(cursor).isAir() && cursor.getY() > blockView.getMinY()) {
            cursor.move(Direction.DOWN);
        }
        return cursor.getY();
    }

    @NotNull
    FabricHuskClaims getPlugin();

}
