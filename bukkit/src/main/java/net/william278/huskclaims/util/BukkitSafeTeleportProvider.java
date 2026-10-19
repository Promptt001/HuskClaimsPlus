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

import io.papermc.lib.PaperLib;
import net.william278.huskclaims.BukkitHuskClaims;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.position.World;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;

public interface BukkitSafeTeleportProvider extends SafeTeleportProvider {

    @NotNull
    List<Function<Material, Boolean>> SAFETY_CHECKS = List.of(
            m -> m.isSolid() || m == Material.WATER, Material::isAir, Material::isAir
    );

    /** Blocks that hurt, trap or transport a player standing in or on them. */
    Set<Material> HARMFUL_BLOCKS = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.MAGMA_BLOCK, Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.COBWEB,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY
    );

    @Override
    @NotNull
    default CompletableFuture<Boolean> isSafeStandingSpot(@NotNull Position position) {
        final Location anchor = BukkitHuskClaims.Adapter.adapt(position);
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (anchor.getWorld() == null) {
            result.complete(false); // the warp's world isn't loaded: not a safe destination
            return result;
        }

        // Load the chunk without blocking, then read the blocks on the thread that owns that region
        PaperLib.getChunkAtAsync(anchor).whenComplete((chunk, loadError) -> {
            if (loadError != null) {
                result.complete(false); // an unloadable chunk is not a safe destination
                return;
            }
            getPlugin().runSync(position, () -> {
                try {
                    // Measure from just below the feet, so a slab or carpet counts as the ground
                    final Block ground = anchor.clone().subtract(0, 0.01, 0).getBlock();
                    final Block feet = ground.getRelative(BlockFace.UP);
                    final Block head = feet.getRelative(BlockFace.UP);
                    result.complete(isSolidGround(ground) && isOpenSpace(feet) && isOpenSpace(head));
                } catch (RuntimeException e) {
                    getPlugin().log(Level.WARNING, "Could not check whether a warp anchor is safe", e);
                    result.complete(false);
                }
            });
        });
        return result;
    }

    private static boolean isSolidGround(@NotNull Block block) {
        return block.getType().isSolid() && !HARMFUL_BLOCKS.contains(block.getType());
    }

    private static boolean isOpenSpace(@NotNull Block block) {
        return block.isPassable() && !block.isLiquid() && !HARMFUL_BLOCKS.contains(block.getType());
    }

    @Override
    @NotNull
    default CompletableFuture<Optional<Position>> findSafePosition(@NotNull Position position) {
        final Location location = BukkitHuskClaims.Adapter.adapt(position);
        return PaperLib.getChunkAtAsync(location).thenApply(Chunk::getChunkSnapshot).thenApply(
                (chunk) -> getSafe(position.getWorld(), chunk, location.getBlockX(), location.getBlockZ())
        );
    }

    private Optional<Position> getSafe(@NotNull World world, @NotNull ChunkSnapshot chunk, int x, int z) {
        int chunkX = x & 0xF;
        int chunkZ = z & 0xF;
        int y = chunk.getHighestBlockYAt(chunkX, chunkZ);
        for (Function<Material, Boolean> check : SAFETY_CHECKS) {
            if (!check.apply(chunk.getBlockType(chunkX, y, chunkZ))) {
                return Optional.empty();
            }
            y++;
        }
        return Optional.of(Position.at(x, y - 1, z, world));
    }

}
