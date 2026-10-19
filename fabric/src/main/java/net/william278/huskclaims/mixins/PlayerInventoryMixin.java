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

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskclaims.util.PlayerActionEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Inventory.class)
public class PlayerInventoryMixin {

    @Shadow
    @Final
    public Player player;

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void dropAllHeadMixin(CallbackInfo ci, @Share("items") LocalRef<List<ItemEntity>> itemsLocalRef) {
        itemsLocalRef.set(Lists.newArrayList());
    }

    // 26.2 (Mojang names): Inventory.dropAll invokes
    // Player.drop(ItemStack, boolean, boolean).
    // 26.3 moved the second boolean into net.minecraft.util.Prediction: Player.drop now
    // takes (ItemStack, boolean, Prediction) and dropAll calls createItemStackToDrop(ItemStack, boolean, boolean).
    //#if MC>=260300
    //$$ // 26.3: Player.drop(ItemStack,Z,Prediction) is called as createItemStackToDrop(ItemStack,ZZ)
    //$$ // inside Inventory.dropAll: invokevirtual Player.createItemStackToDrop:(ItemStack;ZZ)ItemEntity.
    //$$ @ModifyExpressionValue(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;createItemStackToDrop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    //#else
    @ModifyExpressionValue(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    //#endif
    private ItemEntity dropAllMixin(ItemEntity original, @Share("items") LocalRef<List<ItemEntity>> itemsLocalRef) {
        itemsLocalRef.get().add(original);
        return original;
    }

    // Player.drop runs for the local player during client prediction, so
    // only collect and report drops made by authoritative server players.
    @Inject(method = "dropAll", at = @At("TAIL"))
    private void dropAllTailMixin(CallbackInfo ci, @Share("items") LocalRef<List<ItemEntity>> itemsLocalRef) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PlayerActionEvents.AFTER_DEATH_DROP_ITEMS.invoker().dropItems(serverPlayer, itemsLocalRef.get());
    }

}
