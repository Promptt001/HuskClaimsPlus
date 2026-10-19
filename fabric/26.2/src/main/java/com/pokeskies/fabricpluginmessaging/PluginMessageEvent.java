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

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public interface PluginMessageEvent {

    Event<PluginMessageEvent> EVENT = EventFactory.createArrayBacked(PluginMessageEvent.class,
            listeners -> (payload, context) -> {
                for (PluginMessageEvent listener : listeners) {
                    listener.onReceive(payload, context);
                }
            });

    void onReceive(PluginMessagePacket payload, ServerPlayNetworking.Context context);

}
