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

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
//#if MC>=260300
//$$ import net.minecraft.world.level.block.entity.SignTextSlot;
//#endif
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.network.FilteredText;
import net.minecraft.server.level.ServerPlayer;
import net.william278.huskclaims.util.PlayerActionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;
import java.util.function.UnaryOperator;

@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin {

    // 26.2 (Mojang names):
    // tryChangeText → updateSignText; changeText → updateText;
    // getTextWithMessages → setMessages (all same descriptors).
    //#if MC>=260300
    //$$ // 26.3: updateSignText(Player, SignTextSlot, List<FilteredText>) calls
    //$$ // updateText(UnaryOperator, SignTextSlot); filtering moved to static
    //$$ // updateMessages(List<FilteredText>, SignText, boolean).
    //$$ @Shadow
    //$$ protected static SignText updateMessages(List<FilteredText> messages, SignText text, boolean front) {
    //$$     throw new AssertionError(); // @Shadow static requires a body for javac; replaced at apply time
    //$$ }
    //$$
    //$$ @ModifyArg(method = "updateSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;updateText(Ljava/util/function/UnaryOperator;Lnet/minecraft/world/level/block/entity/SignTextSlot;)Z"))
    //$$ UnaryOperator<SignText> tryChangeTextMixin(UnaryOperator<SignText> textChanger, @Local(argsOnly = true) Player player,
    //$$     @Local(argsOnly = true) SignTextSlot slot, @Local(argsOnly = true) List<FilteredText> messages) {
    //$$     List<FilteredText> filteredMessages = PlayerActionEvents.BEFORE_CHANGE_TEXT_ON_SIGN.invoker()
    //$$         .changeTextOnSign((SignBlockEntity) (Object) this, (ServerPlayer) player, slot == SignTextSlot.FRONT, messages);
    //$$     return (text) -> updateMessages(filteredMessages, text, slot == SignTextSlot.FRONT);
    //$$ }
    //#else
    @Shadow
    protected abstract SignText setMessages(Player player, List<FilteredText> messages, SignText text);

    @ModifyArg(method = "updateSignText", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/SignBlockEntity;updateText(Ljava/util/function/UnaryOperator;Z)Z"))
    UnaryOperator<SignText> tryChangeTextMixin(UnaryOperator<SignText> textChanger, @Local(argsOnly = true) Player player,
        @Local(argsOnly = true) boolean front, @Local(argsOnly = true) List<FilteredText> messages) {
        List<FilteredText> filteredMessages = PlayerActionEvents.BEFORE_CHANGE_TEXT_ON_SIGN.invoker()
            .changeTextOnSign((SignBlockEntity) (Object) this, (ServerPlayer) player, front, messages);
        return (text) -> this.setMessages(player, filteredMessages, text);
    }
    //#endif

}
