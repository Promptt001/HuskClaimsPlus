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

package net.william278.huskclaims.listener;

import net.william278.cloplib.handler.Handler;
import net.william278.cloplib.operation.Operation;
import net.william278.cloplib.operation.OperationPosition;
import net.william278.cloplib.operation.OperationUser;
import net.william278.cloplib.operation.OperationWorld;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Protection handler that defers to HuskClaims while a world is open and allows
 * everything otherwise, for example while a client is on the title screen or
 * playing on a remote server.
 */
final class SessionGatedHandler implements Handler {

    private final FabricHuskClaims plugin;

    // Logs every player-caused protection decision when the JVM is started with
    // -Dhuskclaims.traceOperations=true. Used to diagnose reports of wrong denials.
    private static final boolean TRACE_OPERATIONS = Boolean.getBoolean("huskclaims.traceOperations");

    SessionGatedHandler(@NotNull FabricHuskClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean cancelOperation(@NotNull Operation operation) {
        final boolean cancelled = plugin.isSessionActive() && plugin.cancelOperation(operation);
        if (TRACE_OPERATIONS && operation.getUser().isPresent()
                && operation.getUser().get() instanceof OnlineUser user) {
            final Position position = (Position) operation.getOperationPosition();
            plugin.log(Level.INFO, "Protection check %s at %d %d %d for %s: %s".formatted(
                    operation.getType().asMinimalString(),
                    position.getBlockX(), (int) Math.floor(position.getY()), position.getBlockZ(),
                    user.getName(),
                    cancelled ? "denied" : "allowed"));
        }
        return cancelled;
    }

    @Override
    public boolean cancelMovement(@NotNull OperationUser user, @NotNull OperationPosition from,
                                  @NotNull OperationPosition to) {
        return plugin.isSessionActive() && plugin.cancelMovement(user, from, to);
    }

    @Override
    public boolean cancelNature(@NotNull OperationWorld world, @NotNull OperationPosition position1,
                                @NotNull OperationPosition position2) {
        return plugin.isSessionActive() && plugin.cancelNature(world, position1, position2);
    }

    @Override
    public boolean handleInspection(@NotNull OperationPosition position) {
        return plugin.isSessionActive() && plugin.handleInspection(position);
    }

}
