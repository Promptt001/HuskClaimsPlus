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

package net.william278.huskclaims.user;

import com.google.common.collect.Lists;
import com.pokeskies.fabricpluginmessaging.PluginMessagePacket;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.william278.cloplib.listener.InspectorCallbackProvider;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.hook.HuskHomesHook;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.util.Location;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class FabricUser extends OnlineUser {

    private final ServerPlayer fabricPlayer;

    private FabricUser(@NotNull ServerPlayer fabricPlayer, @NotNull HuskClaims plugin) {
        super(fabricPlayer.getGameProfile().name(), fabricPlayer.getUUID(), plugin);
        this.fabricPlayer = fabricPlayer;
    }

    @NotNull
    @ApiStatus.Internal
    public static FabricUser adapt(@NotNull ServerPlayer player, @NotNull HuskClaims plugin) {
        return new FabricUser(player, plugin);
    }

    @NotNull
    @Override
    public Position getPosition() {
        return FabricHuskClaims.Adapter.adapt(new Location(fabricPlayer));
    }

    @Override
    public void sendPluginMessage(@NotNull String channel, byte[] message) {
        ServerPlayNetworking.send(fabricPlayer, new PluginMessagePacket(message));
    }

    @Override
    public boolean hasPermission(@NotNull String node) {
        final Map<String, Boolean> permissionDefaultsMap = ((FabricHuskClaims) plugin).getPermissions();
        return hasPermission(node, !permissionDefaultsMap.getOrDefault(node, true));
    }

    @Override
    public boolean hasPermission(@NotNull String node, boolean isDefault) {
        // LAN/singleplayer policy: the operator fallback applies on an integrated server.
        // LAN guests keep the world owner's operator fallback only when the Fabric
        // settings allow it.
        return FabricPermissions.check(
                (FabricHuskClaims) plugin, fabricPlayer, node, !isDefault);
    }

    @Override
    public boolean isHolding(@NotNull InspectorCallbackProvider.InspectionTool tool) {
        // one shared rule (see FabricUserClaimTool): the GUI handout and the protection
        // check must never disagree about what counts as the claim tool
        return FabricUserClaimTool.isClaimTool(fabricPlayer.getMainHandItem(), tool);
    }

    @Override
    @NotNull
    public Optional<Long> getNumericalPermission(@NotNull String prefix) {
        final List<Long> permissions = Lists.newArrayList();
        for (long l = 0; l < 100; l++) {
            if (hasPermission(prefix + l)) {
                permissions.add(l);
            }
        }
        return permissions.stream().max(Long::compareTo);
    }

    @Override
    public boolean isSneaking() {
        return fabricPlayer.isShiftKeyDown();
    }

    @Override
    public void teleport(@NotNull Position position, boolean instant) {
        if (instant) {
            teleportInstant(position);
            return;
        }
        plugin.getHook(HuskHomesHook.class).ifPresentOrElse(
                homes -> homes.teleport(this, position, plugin.getServerName()),
                () -> teleportInstant(position)
        );
    }

    private void teleportInstant(@NotNull Position position) {
        fabricPlayer.stopRiding();
        fabricPlayer.getPassengers().forEach(Entity::stopRiding);
        final FabricHuskClaims fabricPlugin = (FabricHuskClaims) plugin;
        if (fabricPlugin.getMinecraftServer() == null) {
            return; // no session: nothing to teleport within
        }
        fabricPlayer.teleport(FabricHuskClaims.Adapter.adapt(
                position, fabricPlugin.requireServer()
        ).teleportTarget());
    }

    @NotNull
    public ServerPlayer getFabricPlayer() {
        return fabricPlayer;
    }

    @Override
    public boolean canSee(@NotNull OnlineUser other) {
        if (other instanceof FabricUser fabricOther) {
            return fabricPlayer.level().getServer() != null && fabricPlayer.level().getServer()
                    .getPlayerList().getPlayer(fabricOther.getFabricPlayer().getUUID()) != null;
        }
        return true;
    }

}
