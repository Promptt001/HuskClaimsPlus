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

package net.william278.huskclaims.listener;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import net.william278.huskclaims.HuskClaims;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FabricPetListener {

    default InteractionResult onPlayerTamedInteract(Player player, Level world, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
        // UseEntityCallback also fires on the physical client while playing on a
        // remote server; only the integrated server should enforce pet rules.
        if (world.isClientSide()) {
            return InteractionResult.PASS;
        }
        return this.onUserTamedEntityAction(player, entity) ? InteractionResult.PASS : InteractionResult.FAIL;
    }

    default boolean onPlayerDamageTamed(LivingEntity entity, DamageSource damageSource, float damage) {
        return this.onUserTamedEntityAction(damageSource.getEntity(), entity);
    }

    boolean onUserTamedEntityAction(@Nullable Entity player, @NotNull Entity entity);

    @NotNull
    HuskClaims getPlugin();

}
