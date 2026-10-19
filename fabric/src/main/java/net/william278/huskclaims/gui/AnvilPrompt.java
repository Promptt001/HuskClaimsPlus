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

import eu.pb4.sgui.api.gui.AnvilInputGui;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskclaims.gui.model.TextPrompt;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks for a line of text with an anvil screen; closing the anvil cancels.
 *
 * @since HuskClaimsPlus 1.0.0
 */
final class AnvilPrompt {

    private AnvilPrompt() {
    }

    static void open(@NotNull SguiMenuHost host, @NotNull GuiController controller,
                     @NotNull ServerPlayer player, @NotNull TextPrompt prompt) {
        final AtomicBoolean submitted = new AtomicBoolean();
        final AnvilInputGui anvil = new AnvilInputGui(player, false) {
            @Override
            public void onPlayerClose(boolean success) {
                if (!submitted.get()) {
                    controller.getPlugin().runSync(prompt.onCancel());
                }
            }
        };
        // The chat-style prompt text (with chat instructions and formatting) is
        // far too long for an anvil title, which would show it cut off as raw
        // MineDown; use the short _anvil sibling key instead.
        anvil.setTitle(SguiMenuHost.text(controller.text(prompt.titleKey() + "_anvil")));
        anvil.setDefaultInputValue(prompt.initialValue());
        anvil.setSlot(1, host.element(controller.item("minecraft:barrier")
                .name("gui_cancel").lore("gui_cancel_lore").build())
                .setCallback((i, type, action, gui) -> anvil.close(false)));
        anvil.setSlot(2, host.element(controller.item("minecraft:lime_dye")
                .name("gui_prompt_confirm").lore("gui_prompt_confirm_lore").build())
                .setCallback((i, type, action, gui) -> {
                    final String input = anvil.getInput().strip();
                    if (input.isEmpty() || input.length() > prompt.maxLength()) {
                        return;
                    }
                    submitted.set(true);
                    anvil.close(false);
                    controller.getPlugin().runSync(() -> prompt.onSubmit().accept(input));
                }));
        anvil.open();
    }

}
