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

package net.william278.huskclaims.mixins;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskclaims.util.PlayerActionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayer player;

    // 26.2: the mojmap handler is handlePlayerAction; its swap-hands branch
    // calls setItemInHand(air) then stopUsingItem()V, so anchor on
    // stopUsingItem — the last call in the swap path, same point clearActiveItem occupied.
    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;stopUsingItem()V"))
    private void onPlayerActionMixin(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        PlayerActionEvents.AFTER_HELD_ITEM_CHANGE.invoker().swapHands(
                player, player.getMainHandItem(), player.getOffhandItem()
        );
    }

    @Inject(method = "handleSetCarriedItem", at = @At("TAIL"))
    private void onUpdateSelectedSlotMixin(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        PlayerActionEvents.AFTER_HELD_ITEM_CHANGE.invoker().swapHands(
                player, player.getMainHandItem(), player.getOffhandItem()
        );
    }

}
