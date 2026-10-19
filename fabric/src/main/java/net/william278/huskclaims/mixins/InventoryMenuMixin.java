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

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.william278.huskclaims.gui.HandoutMarks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same handout-marker guard as the crafting-table mixin, for the player's
 * built-in 2×2 crafting grid: repairing two handed-out tools in the inventory
 * screen must not launder out an unmarked tool.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {

    @Shadow
    @Final
    private Player owner;

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void huskclaims$keepHandoutMark(net.minecraft.world.Container container, CallbackInfo ci) {
        if (owner instanceof ServerPlayer) {
            HandoutMarks.remarkResult((AbstractCraftingMenu) (Object) this);
        }
    }

}
