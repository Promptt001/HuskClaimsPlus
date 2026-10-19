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
package net.william278.huskclaims.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Keeps the claim-tool handout tag on items crafted from a handed-out tool,
 * and only on those. The crafting grid is inspected through the vanilla
 * accessors {@link AbstractCraftingMenu#getResultSlot()} and
 * {@link AbstractCraftingMenu#getInputGridSlots()}, never by scanning the
 * whole slot list: a crafting menu's slot list also contains the player's
 * inventory slots, and a claim tool anywhere in the inventory must not taint
 * unrelated crafted items.
 */
public final class HandoutMarks {

    private HandoutMarks() {
    }

    /**
     * Re-mark the crafting result when one of the grid's own slots holds a
     * handed-out tool (the only consuming recipes are the repair combinations
     * of two damaged tools).
     */
    public static void remarkResult(@NotNull AbstractCraftingMenu menu) {
        final Slot result = menu.getResultSlot();
        final ItemStack crafted = result.getItem();
        if (crafted.isEmpty() || GuiItemMarker.has(crafted, GuiItemMarker.TOOL_KEY)) {
            return;
        }
        final boolean madeFromHandout = menu.getInputGridSlots().stream()
                .anyMatch(slot -> GuiItemMarker.has(slot.getItem(), GuiItemMarker.TOOL_KEY));
        if (madeFromHandout) {
            final ItemStack marked = crafted.copy();
            marked.set(DataComponents.CUSTOM_DATA, GuiItemMarker.TOOL_ITEM);
            result.set(marked);
        }
    }

}
