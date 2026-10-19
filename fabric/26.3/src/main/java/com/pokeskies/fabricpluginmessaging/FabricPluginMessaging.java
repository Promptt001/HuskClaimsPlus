/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Originally from PokeSkies/FabricPluginMessaging
 * (https://github.com/PokeSkies/FabricPluginMessaging), via HuskHomes.
 * Modified for HuskClaimsPlus (Minecraft 26.2 networking API); kept in the
 * com.pokeskies.fabricpluginmessaging package. MPL-2.0 applies to this file.
 */

package com.pokeskies.fabricpluginmessaging;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;

public final class FabricPluginMessaging implements ServerPlayNetworking.PlayPayloadHandler<PluginMessagePacket> {

    public static final FabricPluginMessaging INSTANCE = new FabricPluginMessaging();
    public static final Identifier BUNGEE_CHANNEL = Identifier.fromNamespaceAndPath("bungeecord", "main");

    private FabricPluginMessaging() {
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(PluginMessagePacket.CHANNEL_ID, PluginMessagePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PluginMessagePacket.CHANNEL_ID, PluginMessagePacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PluginMessagePacket.CHANNEL_ID, INSTANCE);
    }

    @Override
    public void receive(PluginMessagePacket payload, ServerPlayNetworking.Context context) {
        PluginMessageEvent.EVENT.invoker().onReceive(payload, context);
    }

}
