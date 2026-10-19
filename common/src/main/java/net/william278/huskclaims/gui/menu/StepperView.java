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

package net.william278.huskclaims.gui.menu;

import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.util.StepperModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A value stepper: minus and plus steps mirrored around the value, jumps to the
 * minimum and maximum, an info item, Back, and a Confirm that shows why it is
 * disabled. Shift multiplies a step by five; a step that would overshoot clamps
 * to the bound. Confirm is the final action — the value is already displayed,
 * so there is no second confirm screen.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public abstract class StepperView implements MenuView {

    protected static final int ROWS = 5;
    protected static final int VALUE_SLOT = StepperModel.VALUE_SLOT;
    protected static final int[] MINUS_SLOTS = StepperModel.MINUS_SLOTS;
    protected static final int[] PLUS_SLOTS = StepperModel.PLUS_SLOTS;
    protected static final int INFO_SLOT = 4;
    protected static final int MIN_SLOT = 19;
    protected static final int MAX_SLOT = 25;
    protected static final int BACK_SLOT = 36;
    protected static final int CONFIRM_SLOT = 40;

    protected final GuiController c;
    protected final StepperModel model;
    private final Consumer<OnlineUser> back;

    protected StepperView(@NotNull GuiController c, int initial, int min, int max,
                          @NotNull List<Integer> steps, @NotNull Consumer<OnlineUser> back) {
        this.c = c;
        this.back = back;
        this.model = StepperModel.of(initial, min, max, steps); // bounds before the initial clamp
    }

    @Override
    public final int rows() {
        return ROWS;
    }

    @NotNull
    @Override
    public final Map<Integer, MenuIcon> icons() {
        final Map<Integer, MenuIcon> items = new HashMap<>();
        final List<Integer> steps = model.steps();
        for (int i = 0; i < steps.size(); i++) {
            final int n = steps.get(i);
            items.put(MINUS_SLOTS[i], stepIcon(-n, model.canApply(-n)));
            items.put(PLUS_SLOTS[i], stepIcon(+n, model.canApply(+n)));
        }
        items.put(VALUE_SLOT, valueIcon());
        items.put(INFO_SLOT, infoIcon());
        items.put(MIN_SLOT, minIcon());
        items.put(MAX_SLOT, maxIcon());
        items.put(BACK_SLOT, c.item("minecraft:arrow").name("gui_back").lore(backLoreKey()).build());
        items.put(CONFIRM_SLOT, confirmIcon());
        return items;
    }

    @NotNull
    private MenuIcon stepIcon(int delta, boolean enabled) {
        final String pane = !enabled ? "minecraft:light_gray_stained_glass_pane"
                : delta < 0 ? "minecraft:red_stained_glass_pane" : "minecraft:lime_stained_glass_pane";
        return stepText(c.item(pane).amount(Math.abs(delta)), delta, enabled).build();
    }

    @Override
    public final void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final List<Integer> steps = model.steps();
        final int multiplier = click.isShift() ? 5 : 1;
        for (int i = 0; i < steps.size(); i++) {
            if (slot == MINUS_SLOTS[i]) {
                apply(user, -steps.get(i) * multiplier);
                return;
            }
            if (slot == PLUS_SLOTS[i]) {
                apply(user, +steps.get(i) * multiplier);
                return;
            }
        }
        switch (slot) {
            case MIN_SLOT -> {
                model.toMin();
                c.host().refresh(user, this);
            }
            case MAX_SLOT -> {
                model.toMax();
                c.host().refresh(user, this);
            }
            case BACK_SLOT -> back.accept(user);
            case CONFIRM_SLOT -> onConfirm(user);
            default -> {
                // value and info are display only
            }
        }
    }

    private void apply(@NotNull OnlineUser user, int delta) {
        if (model.applyClamped(delta)) {
            c.host().refresh(user, this);
        }
    }

    @NotNull
    protected abstract IconBuilder stepText(@NotNull IconBuilder icon, int delta, boolean enabled);

    @NotNull
    protected abstract MenuIcon valueIcon();

    @NotNull
    protected abstract MenuIcon infoIcon();

    @NotNull
    protected abstract MenuIcon minIcon();

    @NotNull
    protected abstract MenuIcon maxIcon();

    @NotNull
    protected abstract MenuIcon confirmIcon();

    @NotNull
    protected abstract String backLoreKey();

    protected abstract void onConfirm(@NotNull OnlineUser user);

}
