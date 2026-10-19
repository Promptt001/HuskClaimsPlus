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

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.william278.huskclaims.gui.GuiItemMarker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A handed-out claim tool cannot be put into a furnace's input slot by hand
 * or by shift-click. The furnace block entity's canPlaceItem guard only stops
 * hoppers and droppers; menu slots ask Slot#mayPlace, which vanilla answers
 * with true for any smeltable item. On the client the furnace menu's
 * container is a SimpleContainer, never the block entity, so the check is
 * server-side by construction.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Shadow
    @Final
    public Container container;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void huskclaims$refuseClaimToolInFurnace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof AbstractFurnaceBlockEntity
                && GuiItemMarker.has(stack, GuiItemMarker.TOOL_KEY)) {
            cir.setReturnValue(false);
        }
    }

}
