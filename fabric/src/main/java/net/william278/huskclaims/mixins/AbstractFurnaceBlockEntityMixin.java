/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributorss
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

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.william278.huskclaims.gui.GuiItemMarker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses claim tools handed out by the GUI as furnace input, so a marked
 * golden shovel cannot be smelted back into nuggets. One check sits in
 * {@code canPlaceItem}, which covers both hopper insertion and hand
 * placement into the input slot: a refused item never enters, so it can
 * never burn.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    /** Hopper or hand insertion of a handed-out tool into any furnace slot is refused. */
    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void huskclaims$refuseClaimToolPlacement(int slot, ItemStack stack,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (GuiItemMarker.has(stack, GuiItemMarker.TOOL_KEY)) {
            cir.setReturnValue(false);
        }
    }

}
