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

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.william278.cloplib.listener.InspectorCallbackProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The one rule that decides whether an item is the claim tool: the item id,
 * then custom model data when the setting requires it. Shared by
 * {@code FabricUser.isHolding} (protection callbacks) and
 * {@code FabricClaimTool} (menu handouts) so the two can never disagree.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class FabricUserClaimTool {

    private FabricUserClaimTool() {
    }

    /**
     * Whether the stack is a claim tool of the given spec.
     */
    public static boolean isClaimTool(@NotNull ItemStack stack,
                                      @NotNull InspectorCallbackProvider.InspectionTool tool) {
        final boolean typeMatch = stack.is(holder -> holder.is(Identifier.parse(tool.material())));
        final boolean modelMatch = !tool.useCustomModelData() || getCustomModelData(stack)
                .map(m -> m == tool.customModelData()).orElse(false);
        return typeMatch && modelMatch;
    }

    private static Optional<Integer> getCustomModelData(@NotNull ItemStack i) {
        final CustomModelData modelData = i.getComponents().get(DataComponents.CUSTOM_MODEL_DATA);
        //#if MC>=12104
        if (modelData != null && !modelData.floats().isEmpty()) {
            return Optional.of(modelData.floats().getFirst().intValue());
        //#else
        //$$ if (modelData != null) {
        //$$     return Optional.of(modelData.value());
        //#endif
        }
        return Optional.empty();
    }

}
