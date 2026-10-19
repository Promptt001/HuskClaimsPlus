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

package net.william278.huskclaims.gui.model;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A request for a line of text, such as a claim or warp name.
 *
 * @param titleKey     locale key shown to the player while typing
 * @param initialValue text to start from, may be empty
 * @param maxLength    longest accepted input
 * @param onSubmit     receives the text on the server thread
 * @param onCancel     runs if the player closes the prompt or times out
 * @since HuskClaimsPlus 1.0.0
 */
public record TextPrompt(@NotNull String titleKey, @NotNull String initialValue, int maxLength,
                         @NotNull Consumer<String> onSubmit, @NotNull Runnable onCancel) {
}
