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

import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Claim Menu that {@code /claimgui} opens: five buttons leading to the rest
 * of the GUI. By default the buttons form a square in a three-row menu, like
 * the five on a die: My Claims in the centre, with Public Claims, Friends
 * &amp; Trust, the claim tool and Claim Blocks on the corners. Every slot can be
 * moved in {@code gui.main_menu} in config.yml. Public Claims is left out while
 * claim warps are turned off, because there would be nothing to show.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class MainMenu implements MenuView {

    private final GuiController controller;

    public MainMenu(@NotNull GuiController controller) {
        this.controller = controller;
    }

    @NotNull
    @Override
    public String typeName() {
        return "main";
    }

    @Override
    public int rows() {
        final Settings.GuiSettings.MainMenuSettings slots = settings();
        return GuiLayout.rowsFor(slots.getRows(), slots.getMyClaimsSlot(), slots.getPublicClaimsSlot(),
                slots.getTrustSlot(), slots.getClaimToolSlot(), slots.getBlocksSlot());
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Settings.GuiSettings.MainMenuSettings slots = settings();
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        items.put(slots.getMyClaimsSlot(), c().item("main.my_claims", "minecraft:grass_block")
                .name("gui_main_my_claims").lore("gui_main_my_claims_lore").build());
        if (warpsEnabled()) {
            items.put(slots.getPublicClaimsSlot(), c().item("main.public_claims", "minecraft:ender_eye")
                    .name("gui_main_public_claims").lore("gui_main_public_claims_lore").build());
        }
        items.put(slots.getTrustSlot(), c().item("main.trust", "minecraft:diamond")
                .name("gui_main_trust").lore("gui_main_trust_lore").build());
        items.put(slots.getClaimToolSlot(), c().item("main.claim_tool", "minecraft:golden_shovel")
                .name("gui_main_claim_tool").lore("gui_main_claim_tool_lore").build());
        items.put(slots.getBlocksSlot(), c().item("main.blocks", "minecraft:gold_ingot")
                .name("gui_main_blocks").lore("gui_main_blocks_lore").build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final Settings.GuiSettings.MainMenuSettings slots = settings();
        if (slot == slots.getMyClaimsSlot()) {
            c().openClaimList(user, null);
        } else if (slot == slots.getPublicClaimsSlot()) {
            c().openPublicClaimsMenu(user);
        } else if (slot == slots.getTrustSlot()) {
            // With no claim given, the hub picks the claim the player stands in, or asks which one
            c().openTrustHub(user, null);
        } else if (slot == slots.getClaimToolSlot()) {
            c().host().giveClaimTool(user);
        } else if (slot == slots.getBlocksSlot()) {
            c().openBlocksMenu(user);
        }
    }

    private boolean warpsEnabled() {
        return c().getPlugin().getSettings().getClaims().getWarps().isEnabled();
    }

    @NotNull
    private Settings.GuiSettings.MainMenuSettings settings() {
        return c().getPlugin().getSettings().getGui().getMainMenu();
    }

    @NotNull
    private GuiController c() {
        return controller;
    }

}
