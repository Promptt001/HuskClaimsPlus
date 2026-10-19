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

package net.william278.huskclaims.pet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
//#if MC>=12105
import net.minecraft.world.entity.EntityReference;
//#endif
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.user.FabricUser;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

public interface FabricPetHandler extends PetHandler {

    int TRANSFER_RANGE = 6;

    @Override
    default void userTransferPet(@NotNull OnlineUser user, @NotNull User newOwner, boolean forceTransfer) {
        getPlugin().runSync(() -> {
            final ServerPlayer player = ((FabricUser) user).getFabricPlayer();
            final Optional<TamableAnimal> lookingAt = getLookingAtTamed(player);
            final Optional<User> owner = lookingAt.flatMap(this::getPetOwner);
            if (lookingAt.isEmpty() || owner.isEmpty()) {
                getPlugin().getLocales().getLocale("error_look_at_pet_transfer")
                    .ifPresent(user::sendMessage);
                return;
            }

            if (forceTransfer || !cancelPetOperation(user, owner.get())) {
                this.transferPet(lookingAt.get(), user, newOwner);
            }
        });
    }

    private void transferPet(@NotNull TamableAnimal tamed, @NotNull OnlineUser owner, @NotNull User newOwner) {
        //#if MC>=12105
        tamed.setOwnerReference(EntityReference.of(newOwner.getUuid()));
        //#else
        //$$ tamed.setOwnerUuid(newOwner.getUuid());
        //#endif

        //#if MC>=12104
        Component nameComponent = getPlugin().getAudiences().asAdventure(tamed.getName());
        //#else
        //$$ Component nameComponent = tamed.getName().asComponent();
        //#endif
        String plainName = PlainTextComponentSerializer.plainText().serialize(nameComponent);
        getPlugin().getLocales().getLocale("pet_transferred", plainName, newOwner.getName())
            .ifPresent(owner::sendMessage);
    }

    default Optional<User> getPetOwner(@NotNull TamableAnimal entity) {
        if (!entity.isTame()) {
            return Optional.empty();
        }
        //#if MC>=12105
        final UUID ownerUuid = entity.getOwnerReference() != null ? entity.getOwnerReference().getUUID() : null;
        //#else
        //$$ final UUID ownerUuid = entity.getOwnerUuid();
        //#endif
        if (ownerUuid == null) {
            return Optional.empty();
        }

        final LivingEntity owner = entity.getOwner();
        return Optional.of(User.of(ownerUuid, !(owner instanceof ServerPlayer playerOwner)
            ? getPlugin().getLocales().getNotApplicable() : playerOwner.getGameProfile().name()));
    }

    private Optional<TamableAnimal> getLookingAtTamed(@NotNull ServerPlayer player) {
        Vec3 minPos = player.getEyePosition(0);
        Vec3 connect = player.getViewVector(0).multiply(TRANSFER_RANGE, TRANSFER_RANGE, TRANSFER_RANGE);
        Vec3 maxPos = minPos.add(connect);
        AABB box = player.getBoundingBox().expandTowards(connect).inflate(1);
        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(player, minPos, maxPos, box, entity -> entity instanceof TamableAnimal, TRANSFER_RANGE * TRANSFER_RANGE);
        if (hitResult == null) {
            return Optional.empty();
        }
        return Optional.of((TamableAnimal) hitResult.getEntity());
    }

    @NotNull
    FabricHuskClaims getPlugin();

}
