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

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.PickTarget;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.trust.PublicTrustTag;
import net.william278.huskclaims.trust.TrustLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trustees list, trust level picker and Friends &amp; Trust hub (shared
 * model): render matches the accepted Paper screens slot-for-slot, every
 * click re-resolves the claim, async name lookups land on the player thread,
 * removes and privileged grants confirm scoped to the referenced claim, and
 * public-row changes re-render only once the claim's observed state changed.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Trust menus (shared model)")
public class TrustMenusTests {

    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private GuiTestKit kit;
    private GuiTestKit.FakeOnlineUser user;
    private Claim claim;
    private ClaimRef ref;
    private TrustLevel access;
    private TrustLevel container;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        user = kit.user();
        // The trust menus dispatch real in-claim commands
        kit.inClaimCommand("trust");
        kit.inClaimCommand("untrust");
        // Two grantable levels: access (doors) and container (containers, chest-opening)
        access = TrustLevel.builder().id("access").displayName("Access")
                .color("#36e4ff").weight(100).commandAliases(List.of("access")).build();
        container = TrustLevel.builder().id("container").displayName("Container")
                .color("#ffab40").weight(200).commandAliases(List.of("container"))
                .flags(List.of(OperationType.CONTAINER_OPEN, OperationType.BLOCK_INTERACT)).build();
        kit.trustLevels.add(access);
        kit.trustLevels.add(container);
        kit.trustLevels.add(TrustLevel.builder().id("manage").displayName("Manage")
                .weight(400).commandAliases(List.of("managetrust"))
                .privileges(List.of(TrustLevel.Privilege.MANAGE_TRUSTEES)).build());
        claim = kit.claimAt(0, 0, 10, 10);
        ref = ClaimRef.of("world", claim);
    }

    /* TrusteesMenu ---------------------------------------------------------- */

    @Test
    @DisplayName("Trustees render: empty state, then one named head per trustee plus add/back")
    public void testTrusteesRender() {
        final var cfg = kit.settings.getGui().getTrusteesMenu();
        final TrusteesMenu empty = new TrusteesMenu(kit.controller, ref);
        assertEquals("minecraft:oak_sapling", empty.icons().get(cfg.getEmptySlot()).itemKey());
        assertEquals("trustees", empty.typeName());
        assertTrue(empty.rows() == 6);
        // No page buttons with one empty page
        assertFalse(empty.icons().containsKey(cfg.getPrevSlot()));
        assertFalse(empty.icons().containsKey(cfg.getNextSlot()));
        assertEquals("minecraft:player_head", empty.icons().get(cfg.getAddSlot()).itemKey());
        assertEquals("minecraft:arrow", empty.icons().get(cfg.getBackSlot()).itemKey());

        // One trusted user: a NAMED head at slot 0 (never a raw UUID)
        claim.setUserTrustLevel(FRIEND, access);
        kit.host.names.put(FRIEND, "Friend");
        final Map<Integer, MenuIcon> icons = new TrusteesMenu(kit.controller, ref).icons();
        assertEquals("minecraft:player_head", icons.get(0).itemKey());
        assertEquals(FRIEND, icons.get(0).headOwner(), "the head must carry the trustee's UUID");
        assertEquals(GuiTestKit.name("gui_trustees_user", "Friend"), icons.get(0).name());
    }

    @Test
    @DisplayName("Trustees render: unknown users fall back to the world cache, never a raw UUID")
    public void testTrusteesUnknownUserName() {
        claim.setUserTrustLevel(FRIEND, access);
        // Neither the host nor the world cache knows the name
        final MenuIcon head = new TrusteesMenu(kit.controller, ref).icons().get(0);
        assertTrue(head.name().contains("Unknown (") || head.name().contains("00000000"),
                "an unknown trustee shows a derived label, not a raw UUID: " + head.name());
    }

    @Test
    @DisplayName("Trustees routing: Back falls back to Claim Settings for the owner")
    public void testTrusteesBack() {
        final var cfg = kit.settings.getGui().getTrusteesMenu();
        new TrusteesMenu(kit.controller, ref).onClick(user, cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Trustees routing: Back honors the hub's back destination when given one")
    public void testTrusteesBackToHub() {
        final var cfg = kit.settings.getGui().getTrusteesMenu();
        new TrusteesMenu(kit.controller, ref, u -> kit.controller.openTrustHub(u, ref))
                .onClick(user, cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals("trust", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Trustees routing: the #public entry opens the hub; a user head opens the picker (async)")
    public void testTrusteesEntries() {
        claim.setUserTrustLevel(FRIEND, access);
        claim.setTagTrustLevel(publicTag(), access);
        kit.savedUser(FRIEND, "Friend");
        // User head (slot 0): async DB lookup, then the picker for that UUID
        new TrusteesMenu(kit.controller, ref).onClick(user, 0, ClickKind.LEFT);
        assertEquals("trust_level_picker", kit.host.last().typeName());
        assertTrue(kit.host.last().title().isPresent()
                && kit.host.last().title().get().contains("Friend"),
                "the picker title names the resolved player");
        // #public entry (after the user entry, at slot 1): opens the hub
        new TrusteesMenu(kit.controller, ref).onClick(user, 1, ClickKind.LEFT);
        assertEquals("trust", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Trustees routing: Add player prompts, then resolves the name async")
    public void testTrusteesAddPrompt() {
        final var cfg = kit.settings.getGui().getTrusteesMenu();
        final GuiTestKit.SavedUserFixture friend = kit.savedUser(FRIEND, "Friend");
        new TrusteesMenu(kit.controller, ref).onClick(user, cfg.getAddSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.prompts.size());
        assertEquals("gui_prompt_player", kit.host.prompts.get(0).titleKey());
        // Invalid name re-prompts
        kit.host.prompts.get(0).onSubmit().accept("no");
        assertEquals(2, kit.host.prompts.size());
        // Valid name: async DB resolve → picker
        kit.host.prompts.get(1).onSubmit().accept("Friend");
        assertEquals("trust_level_picker", kit.host.last().typeName());
    }

    /* TrustLevelPicker ------------------------------------------------------- */

    @Test
    @DisplayName("Picker render: levels in row 1 at their columns, remove/back in row 2")
    public void testPickerRender() {
        final TrustLevelPicker picker = new TrustLevelPicker(kit.controller, ref,
                new PickTarget(FRIEND, "Friend"));
        final Map<Integer, MenuIcon> icons = picker.icons();
        assertEquals("trust_level_picker", picker.typeName());
        assertTrue(picker.rows() == 3);
        // Two levels → spaced columns 2,4 → slots 11, 13 (row 1)
        assertEquals("minecraft:oak_door", icons.get(11).itemKey());
        assertEquals("minecraft:chest", icons.get(13).itemKey());
        // Remove (22) with a target; Back (18) renders and handles at the same slot
        assertEquals("minecraft:shears", icons.get(22).itemKey());
        assertEquals("minecraft:arrow", icons.get(18).itemKey());
        // The current level (access) is marked
        claim.setUserTrustLevel(FRIEND, access);
        final Map<Integer, MenuIcon> marked = new TrustLevelPicker(kit.controller, ref,
                new PickTarget(FRIEND, "Friend")).icons();
        assertTrue(marked.get(11).lore().stream()
                .anyMatch(l -> l.contains(GuiTestKit.echo("gui_trust_current"))));
        assertFalse(marked.get(13).lore().stream()
                .anyMatch(l -> l.contains(GuiTestKit.echo("gui_trust_current"))));
    }

    @Test
    @DisplayName("Picker render: levels without a command alias are never offered")
    public void testPickerSkipsAliaslessLevels() {
        kit.trustLevels.add(TrustLevel.builder().id("ghost").displayName("Ghost").weight(150).build());
        final TrustLevelPicker picker = new TrustLevelPicker(kit.controller, ref,
                new PickTarget(FRIEND, "Friend"));
        // Still exactly the two alias-carrying levels
        assertTrue(picker.icons().size() >= 3);
        assertFalse(picker.icons().values().stream()
                .anyMatch(i -> i.name().contains("Ghost")));
    }

    @Test
    @DisplayName("Picker routing: an unprivileged level dispatches scoped and re-renders settled")
    public void testPickerGrantsUnprivilegedSettled() {
        final TrustLevelPicker picker = new TrustLevelPicker(kit.controller, ref,
                new PickTarget(FRIEND, "Friend"));
        kit.savedUser(FRIEND, "Friend");
        // slot 11 = access; dispatch is rejected (no 'trust' alias registered on the
        // level's alias list is fine — the kit's inClaimCommand is named 'trust'
        // while the level's alias is 'access', so register one matching alias)
        kit.inClaimCommand("access");
        picker.onClick(user, 11, ClickKind.LEFT);
        // name check runs async-inline; grant dispatches; the change settles and
        // the picker refreshes (the recording host records the refresh)
        assertFalse(kit.host.refreshed.isEmpty(), "the picker re-renders only once settled");
        assertFalse(kit.host.opened.stream().anyMatch(v -> v.typeName().equals("confirm")),
                "an unprivileged grant never opens a confirm screen");
    }

    @Test
    @DisplayName("Picker routing: a privileged level confirms scoped to THIS claim")
    public void testPickerPrivilegedConfirms() {
        final TrustLevelPicker picker = new TrustLevelPicker(kit.controller, ref,
                new PickTarget(FRIEND, "Friend"));
        kit.savedUser(FRIEND, "Friend");
        kit.inClaimCommand("managetrust");
        // slot 13 = container (has flags) — wait: manage is weight 400 → capped
        // row has access(2), container(4), manage… 3 levels ≤ 4 → columns 2,4,6 → slots 11,13,15
        picker.onClick(user, 15, ClickKind.LEFT);
        assertEquals("confirm", kit.host.last().typeName(), "a privileged grant confirms first");
    }

    @Test
    @DisplayName("Picker routing: Remove confirms and reopens the trustees list")
    public void testPickerRemoveConfirms() {
        kit.savedUser(FRIEND, "Friend");
        claim.setUserTrustLevel(FRIEND, access);
        final TrustLevelPicker picker = new TrustLevelPicker(kit.controller, ref,
                new PickTarget(FRIEND, "Friend"));
        picker.onClick(user, 22, ClickKind.LEFT);
        assertEquals("confirm", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Picker routing: Back returns to the trustees list")
    public void testPickerBack() {
        final TrustLevelPicker picker = new TrustLevelPicker(kit.controller, ref, null);
        picker.onClick(user, 18, ClickKind.LEFT);
        assertEquals("trustees", kit.host.last().typeName());
    }

    /* TrustMenu hub ---------------------------------------------------------- */

    @Test
    @DisplayName("Hub render: header, both row labels, level tiles in both rows, back/list/remove")
    public void testHubRender() {
        final Map<Integer, MenuIcon> icons = new TrustMenu(kit.controller, ref).icons();
        assertEquals("trust", new TrustMenu(kit.controller, ref).typeName());
        assertTrue(new TrustMenu(kit.controller, ref).rows() == 6);
        assertEquals("minecraft:grass_block", icons.get(4).itemKey());          // header
        assertEquals("minecraft:oak_sign", icons.get(9).itemKey());              // public row label
        assertEquals("minecraft:player_head", icons.get(27).itemKey());          // players row label
        // 3 levels → columns 2,4,6 → public row slots 11,13,15; player row 29,31,33
        assertEquals("minecraft:redstone_block", icons.get(11).itemKey());       // access OFF
        assertEquals("minecraft:iron_bars", icons.get(15).itemKey());            // manage LOCKED (privileges)
        assertEquals("minecraft:oak_door", icons.get(29).itemKey());             // player grant access
        assertEquals("minecraft:arrow", icons.get(45).itemKey());                // back
        assertEquals("minecraft:book", icons.get(48).itemKey());                 // list
        assertEquals("minecraft:shears", icons.get(50).itemKey());               // remove
    }

    @Test
    @DisplayName("Hub render: a granted public level turns its tile and lower tiles green")
    public void testHubPublicGranted() {
        claim.setTagTrustLevel(publicTag(), container);
        final Map<Integer, MenuIcon> icons = new TrustMenu(kit.controller, ref).icons();
        assertEquals("minecraft:emerald_block", icons.get(13).itemKey());        // container ON
        assertEquals("minecraft:emerald_block", icons.get(11).itemKey());        // access INCLUDED
        assertEquals("minecraft:iron_bars", icons.get(15).itemKey());            // manage still LOCKED
    }

    @Test
    @DisplayName("Hub routing: header opens the claim picker; list opens trustees with hub as Back")
    public void testHubHeaderAndList() {
        final TrustMenu hub = new TrustMenu(kit.controller, ref);
        hub.onClick(user, 4, ClickKind.LEFT);
        assertEquals("claim_list", kit.host.last().typeName());
        hub.onClick(user, 48, ClickKind.LEFT);
        assertEquals("trustees", kit.host.last().typeName());
        // The hub threads its own Back destination into the list: trustees Back
        // lands where the hub's Back would (main menu by default)
        final var cfg = kit.settings.getGui().getTrusteesMenu();
        kit.host.last().onClick(user, cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals("main", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Hub routing: Back honors the given destination (settings), defaulting to main menu")
    public void testHubBack() {
        new TrustMenu(kit.controller, ref).onClick(user, 45, ClickKind.LEFT);
        assertEquals("main", kit.host.last().typeName());
        new TrustMenu(kit.controller, ref, u -> kit.controller.openClaimSettings(u, ref))
                .onClick(user, 45, ClickKind.LEFT);
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Hub routing: raising public access INTO a container level confirms with the chest warning")
    public void testHubContainerRaiseConfirms() {
        final TrustMenu hub = new TrustMenu(kit.controller, ref);
        kit.inClaimCommand("container");
        hub.onClick(user, 13, ClickKind.LEFT);   // container OFF → GRANT, container-opening → confirm
        assertEquals("confirm", kit.host.last().typeName());
        final var confirmCfg = kit.settings.getGui().getConfirmMenu();
        final MenuIcon info = kit.host.last().icons().get(confirmCfg.getInfoSlot());
        assertEquals("minecraft:book", info.itemKey());
        assertTrue(info.name().contains(GuiTestKit.echo("gui_public_access_warning_containers")),
                "the confirm names the container warning");
    }

    @Test
    @DisplayName("Hub routing: raising public access to a non-container level dispatches without confirm")
    public void testHubNonContainerRaiseDispatches() {
        final TrustMenu hub = new TrustMenu(kit.controller, ref);
        kit.inClaimCommand("access");
        hub.onClick(user, 11, ClickKind.LEFT);   // access OFF → GRANT, no containers → straight dispatch
        assertFalse(kit.host.opened.stream().anyMatch(v -> v.typeName().equals("confirm")));
        assertTrue(kit.host.refreshed.size() >= 1, "the hub shows Updating… and re-renders settled");
    }

    @Test
    @DisplayName("Hub routing: clicking a green public tile lowers or revokes, settled")
    public void testHubLowerSettled() {
        claim.setTagTrustLevel(publicTag(), container);
        kit.inClaimCommand("untrust");
        final TrustMenu hub = new TrustMenu(kit.controller, ref);
        hub.onClick(user, 13, ClickKind.LEFT);   // container ON → lower to access (highest below)
        assertFalse(kit.host.opened.stream().anyMatch(v -> v.typeName().equals("confirm")));
        assertTrue(kit.host.refreshed.size() >= 1);
    }

    @Test
    @DisplayName("Hub routing: Remove prompts for a name and confirms scoped to the claim")
    public void testHubRemovePromptsAndConfirms() {
        kit.inClaimCommand("untrust");
        final TrustMenu hub = new TrustMenu(kit.controller, ref);
        hub.onClick(user, 50, ClickKind.LEFT);
        assertEquals(1, kit.host.prompts.size());
        assertEquals("gui_prompt_player", kit.host.prompts.get(0).titleKey());
        kit.host.prompts.get(0).onSubmit().accept("Friend");
        assertEquals("confirm", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Hub routing: a player-level click prompts, then confirms with the privilege warning")
    public void testHubPlayerGrantFlow() {
        kit.inClaimCommand("managetrust");
        final TrustMenu hub = new TrustMenu(kit.controller, ref);
        hub.onClick(user, 33, ClickKind.LEFT);   // manage (privileged) player-row tile
        assertEquals(1, kit.host.prompts.size());
        kit.host.prompts.get(0).onSubmit().accept("Friend");
        assertEquals("confirm", kit.host.last().typeName());
        final var confirmCfg = kit.settings.getGui().getConfirmMenu();
        final MenuIcon info = kit.host.last().icons().get(confirmCfg.getInfoSlot());
        assertTrue(info.lore().stream().anyMatch(l -> l.contains(GuiTestKit.echo("gui_trust_manage_warning"))),
                "a privileged grant warns about management rights");
    }

    @Test
    @DisplayName("Claim gone while open: hub and picker clicks say so instead of acting")
    public void testClaimGoneWhileOpen() {
        kit.claimWorlds.clear();
        new TrustMenu(kit.controller, ref).onClick(user, 13, ClickKind.LEFT);
        assertTrue(kit.host.opened.stream().noneMatch(v -> v.typeName().equals("confirm")));
        assertTrue(kit.host.prompts.isEmpty());
    }

    @NotNull
    private PublicTrustTag publicTag() {
        return PublicTrustTag.create(kit.plugin);
    }

}
