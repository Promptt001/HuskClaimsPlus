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

package net.william278.huskclaims.highlighter;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.Identifier;
import net.william278.cloplib.operation.OperationChunk;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.hook.GeyserHook;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.FabricUser;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.util.BlockMaterialBlock;
import net.william278.huskclaims.util.Location;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Highlighter that uses {@link Display.BlockDisplay}s - used to highlight {@link Highlightable}s to a user in-game
 */
public class FabricBlockDisplayHighlighter extends BlockHighlighter<FabricBlockDisplayHighlighter.DisplayHighlightBlock> {

    private static final int PRIORITY = 1;

    public FabricBlockDisplayHighlighter(@NotNull HuskClaims plugin) {
        super(plugin);
    }

    @Override
    public void cacheBlock(@NotNull OnlineUser user, @NotNull HighlightBlock origin, @NotNull DisplayHighlightBlock block) {
        replacedBlocks.put(user.getUuid(), block);
    }

    @NotNull
    @Override
    public DisplayHighlightBlock getHighlightBlock(@NotNull Position position, @NotNull Highlightable.Type type,
                                                   @NotNull HuskClaims plugin) {
        return new DisplayHighlightBlock(position, type, plugin);
    }

    @Override
    public void showBlocks(@NotNull OnlineUser user, @NotNull Collection<DisplayHighlightBlock> blocks) {
        blocks.forEach(block -> block.show(user));
    }

    @Override
    public void stopHighlighting(@NotNull OnlineUser user) {
        if (!replacedBlocks.containsKey(user.getUuid())) {
            return;
        }
        replacedBlocks.removeAll(user.getUuid()).forEach(block -> {
            if (block instanceof DisplayHighlightBlock display) {
                display.remove(user);
            }
        });
    }

    @Override
    public boolean canUse(@NotNull OnlineUser user) {
        return plugin.getHook(GeyserHook.class).map(g -> !g.isBedrockPlayer(user.getUuid())).orElse(true);
    }

    @Override
    public short getPriority() {
        return PRIORITY;
    }

    public static final class DisplayHighlightBlock extends HighlightBlock {

        // Block display brightness value
        private static final Brightness FULL_BRIGHT = new Brightness(15, 15);

        // Block display scale constants
        private static final float SCALAR = 0.002f;
        private static final Transformation SCALE_TRANSFORMATION = new Transformation(
                new Vector3f(-(SCALAR / 2), -(SCALAR / 2), -(SCALAR / 2)),
                Transformation.IDENTITY.leftRotation(),
                new Vector3f(Transformation.IDENTITY.scale()).add(SCALAR, SCALAR, SCALAR),
                Transformation.IDENTITY.rightRotation()
        );

        private final Display.BlockDisplay display;
        private Location location;
        private ServerEntity tracker;

        private DisplayHighlightBlock(@NotNull Position position, @NotNull Highlightable.Type type,
                                      @NotNull HuskClaims plugin) {
            super(position, plugin.getSettings().getHighlighter().getBlock(type, plugin));
            this.display = createEntity(plugin, type);
        }

        @NotNull
        private Display.BlockDisplay createEntity(@NotNull HuskClaims plugin, @NotNull Highlightable.Type type) {
            this.location = FabricHuskClaims.Adapter.adapt(position, ((FabricHuskClaims) plugin).requireServer());

            // Check the chunk is loaded
            final OperationChunk chunk = position.getChunk();
            if (!location.world().getChunkSource().hasChunk(chunk.getX(), chunk.getZ())) {
                throw new IllegalStateException("World/chunk is not loaded");
            }

            // Create block display
            final BlockPos blockPos = location.blockPos();
            final Display.BlockDisplay display = new Display.BlockDisplay(
                    Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath("minecraft", "block_display"))),
                    location.world());
            display.absSnapTo(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 0.0f, 0.0f);

            // Set parameters
            display.setBlockState(((BlockMaterialBlock) this.block).getData().defaultBlockState());
            display.setViewRange(BlockHighlighter.VIEWING_RANGE);
            display.setNoGravity(true);
            display.setBrightnessOverride(FULL_BRIGHT);

            // Scale to prevent z-fighting
            display.setTransformation(SCALE_TRANSFORMATION);

            // Glow if needed
            if (plugin.getSettings().getHighlighter().isGlowEffect()) {
                display.setGlowingTag(true);
                display.setGlowColorOverride(plugin.getSettings().getHighlighter().getGlowColor(type).getArgb());
            }

            return display;
        }

        public void show(@NotNull OnlineUser user) {
            final ServerPlayer player = ((FabricUser) user).getFabricPlayer();
            //#if MC>=260300
            //$$ // 26.3: ServerEntity ctor takes UpdateInterval (net.minecraft.world.entity.UpdateInterval)
            //$$ // instead of a raw int; wrap the entity type's interval.
            //$$ this.tracker = new ServerEntity(
            //$$         location.world(), display,
            //$$         net.minecraft.world.entity.UpdateInterval.periodic(display.getType().updateInterval()),
            //$$         display.getType().trackDeltas(),
            //$$         new ServerEntity.Synchronizer() {
            //$$             @Override
            //$$             public void sendToTrackingPlayers(@NotNull Packet<? super ClientGamePacketListener> packet) {
            //$$                 player.connection.send(packet);
            //$$             }

            //$$             @Override
            //$$             public void sendToTrackingPlayersAndSelf(@NotNull Packet<? super ClientGamePacketListener> packet) {
            //$$                 player.connection.send(packet);
            //$$             }

            //$$             @Override
            //$$             public void sendToTrackingPlayersFiltered(@NotNull Packet<? super ClientGamePacketListener> packet,
            //$$                                                     @NotNull Predicate<ServerPlayer> filter) {
            //$$                 if (filter.test(player)) {
            //$$                     player.connection.send(packet);
            //$$                 }
            //$$             }
            //$$         }
            //$$ );
            //#else
            //#if MC==12105
            this.tracker = new ServerEntity(
                    location.world(), display,
                    display.getType().updateInterval(),
                    display.getType().trackDeltas(),
                    new ServerEntity.Synchronizer() {
                        @Override
                        public void sendToTrackingPlayers(@NotNull Packet<? super ClientGamePacketListener> packet) {
                            player.connection.send(packet);
                        }

                        @Override
                        public void sendToTrackingPlayersAndSelf(@NotNull Packet<? super ClientGamePacketListener> packet) {
                            player.connection.send(packet);
                        }

                        @Override
                        public void sendToTrackingPlayersFiltered(@NotNull Packet<? super ClientGamePacketListener> packet,
                                                                @NotNull Predicate<ServerPlayer> filter) {
                            if (filter.test(player)) {
                                player.connection.send(packet);
                            }
                        }
                    }
            );
            //#else
            //$$ this.tracker = new ServerEntity(
            //$$     location.world(), display,
            //$$     display.getType().updateInterval(),
            //$$     display.hasImpulse,
            //$$     player.connection::sendPacket
            //$$ );
            //#endif
            //#endif
            tracker.addPairing(player);
        }

        public void remove(@NotNull OnlineUser user) {
            final ServerPlayer player = ((FabricUser) user).getFabricPlayer();
            if (tracker != null) {
                tracker.removePairing(player);
            }
            display.remove(Entity.RemovalReason.DISCARDED);
        }

    }

}