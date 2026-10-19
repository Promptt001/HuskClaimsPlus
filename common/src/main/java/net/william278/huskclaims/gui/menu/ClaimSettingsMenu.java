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

import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.claim.ClaimWarpNames;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.ClaimNames;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiText;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.PublicLadder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "Claim Settings": shows and edits one specific claim's configuration — name,
 * privacy, public access, public permissions, trustees, children, resize, warp
 * info, unclaim — without the player standing in it. The claim is re-resolved
 * through its {@link ClaimRef} on every render and click, so a menu that
 * outlives its claim (deleted, resized, transferred) says so instead of acting
 * on the wrong land.
 *
 * <p>Owner-only actions re-check permission at click time as well as when the
 * menu opens, because a menu can stay open while ownership changes.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimSettingsMenu implements MenuView {

    private final GuiController controller;
    private final ClaimRef ref;
    @Nullable
    private final OnlineUser viewer;

    public ClaimSettingsMenu(@NotNull GuiController controller, @NotNull ClaimRef ref,
                             @Nullable OnlineUser viewer) {
        this.controller = controller;
        this.ref = ref;
        this.viewer = viewer;
    }

    @NotNull
    @Override
    public String typeName() {
        return "claim_settings";
    }

    @Override
    public int rows() {
        final var cfg = settings();
        return net.william278.huskclaims.gui.GuiLayout.rowsFor(cfg.getRows(), cfg.getInfoSlot(),
                cfg.getRenameSlot(), cfg.getPrivacySlot(), cfg.getPublicAccessSlot(),
                cfg.getPublicPermissionsSlot(), cfg.getTrusteesSlot(), cfg.getChildrenSlot(),
                cfg.getResizeSlot(), cfg.getWarpSlot(), cfg.getBackSlot(), cfg.getUnclaimSlot());
    }

    @NotNull
    @Override
    public Optional<String> title() {
        return Optional.of(claimName());
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final var cfg = settings();
        if (slot == cfg.getBackSlot()) {
            c().openClaimList(user, null);
            return;
        }
        // Everything else requires the claim to still exist
        final ClaimRef.Resolved resolved = ref.resolve(c().getPlugin()).orElse(null);
        if (resolved == null) {
            c().claimGone(user, ref);
            return;
        }
        if (slot == cfg.getRenameSlot()) {
            c().renameClaim(user, ref);
            return;
        }
        if (slot == cfg.getPrivacySlot()) {
            // claimprivate on/off fires the make-public/private events and teleports
            // untrusted players out — always reuse the command, never a direct edit
            c().confirmAndSettlePrivacy(user, ref, resolved.claim(), this);
            return;
        }
        if (slot == cfg.getPublicAccessSlot()) {
            c().openTrustHubForSettings(user, ref);
            return;
        }
        if (slot == cfg.getPublicPermissionsSlot()) {
            c().openPublicPermissionsMenu(user, ref);
            return;
        }
        if (slot == cfg.getTrusteesSlot()) {
            c().openTrusteesMenu(user, ref);
            return;
        }
        if (slot == cfg.getChildrenSlot() && !resolved.claim().getChildren().isEmpty()) {
            c().openClaimList(user, ref);
            return;
        }
        if (slot == cfg.getResizeSlot()) {
            c().openResizeMenu(user, ref);
            return;
        }
        if (slot == cfg.getWarpSlot()) {
            handleWarpClick(user, resolved);
            return;
        }
        if (slot == cfg.getUnclaimSlot()) {
            c().confirmUnclaim(user, ref);
            return;
        }
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final var cfg = settings();
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        final int size = rows() * 9;
        put(items, size, cfg.getInfoSlot(), infoIcon());
        put(items, size, cfg.getRenameSlot(), c().item("claim_settings.rename", "minecraft:name_tag")
                .name("gui_claim_rename")
                .lore("gui_claim_rename_lore", GuiText.literal(claimName())).build());
        final boolean privateClaim = ref.resolve(c().getPlugin())
                .map(r -> r.claim().isPrivateClaim()).orElse(false);
        put(items, size, cfg.getPrivacySlot(), privacyIcon(privateClaim));
        put(items, size, cfg.getPublicAccessSlot(), trustHubIcon());
        // Public Actions slot is hidden entirely when disabled in config
        if (c().getPlugin().getSettings().getGui().getPublicPermissions().isEnabled()) {
            put(items, size, cfg.getPublicPermissionsSlot(), publicActionsIcon());
        }
        put(items, size, cfg.getTrusteesSlot(), trusteesIcon());
        put(items, size, cfg.getResizeSlot(), resizeIcon());
        put(items, size, cfg.getWarpSlot(), warpIcon());
        put(items, size, cfg.getBackSlot(), c().item("minecraft:arrow")
                .name("gui_back").lore("gui_back_lore_claims").build());
        // Locked BARRIER when the viewer lacks /unclaim permission
        if (viewer != null && !c().mayUnclaim(viewer)) {
            put(items, size, cfg.getUnclaimSlot(), c().item("minecraft:barrier")
                    .name("gui_unclaim_locked")
                    .lore("gui_unclaim_locked_lore").build());
        } else {
            put(items, size, cfg.getUnclaimSlot(), c().item("claim_settings.unclaim", "minecraft:tnt")
                    .name("gui_unclaim").build());
        }
        // Children button only when the claim actually has children
        ref.resolve(c().getPlugin()).ifPresent(resolved -> {
            if (!resolved.claim().getChildren().isEmpty()) {
                put(items, size, cfg.getChildrenSlot(), c().item("claim_settings.children", "minecraft:oak_fence")
                        .name("gui_children")
                        .lore("gui_children_lore",
                                Integer.toString(resolved.claim().getChildren().size())).build());
            }
        });
        return items;
    }

    /* Icons ---------------------------------------------------------------- */

    @NotNull
    private String claimName() {
        return ref.resolve(c().getPlugin())
                .map(r -> ClaimNames.displayName(r.claim(), r.worldName(), c().getPlugin()))
                .orElse("?");
    }

    /** Two-state privacy toggle (private = iron door, normal = oak door). */
    @NotNull
    private MenuIcon privacyIcon(boolean privateClaim) {
        final String fallback = privateClaim ? "minecraft:iron_door" : "minecraft:oak_door";
        return c().item("claim_settings.privacy", fallback)
                .name(privateClaim
                        ? "gui_claim_privacy_private" : "gui_claim_privacy_normal")
                .lore(privateClaim
                        ? "gui_claim_privacy_private_lore" : "gui_claim_privacy_normal_lore")
                .build();
    }

    /** Friends &amp; Trust hub entry (trusted count + public level). */
    @NotNull
    private MenuIcon trustHubIcon() {
        final Optional<ClaimRef.Resolved> resolved = ref.resolve(c().getPlugin());
        final int trustees = resolved.map(r -> r.claim().getTrustedUsers().size()
                + r.claim().getTrustedGroups().size() + r.claim().getTrustedTags().size()).orElse(0);
        final String pub = resolved.flatMap(r -> r.claim()
                        .getTagTrustLevel(c().getPlugin().getSettings().getTrustTags().getPublicAccessName(), c().getPlugin()))
                .map(TrustLevel::getDisplayName)
                .orElseGet(() -> c().text("gui_claim_lore_public_none"));
        return c().item("claim_settings.trust_hub", "minecraft:player_head")
                .name("gui_claim_trust_hub")
                .lore("gui_claim_trust_hub_lore",
                        Integer.toString(trustees), pub)
                .build();
    }

    /** Public Actions (emerald when any default flag is on). */
    @NotNull
    private MenuIcon publicActionsIcon() {
        final List<net.william278.cloplib.operation.OperationType> allowed =
                PublicLadder.allowedOperations(c().getPlugin());
        final long on = ref.resolve(c().getPlugin()).stream()
                .flatMap(r -> r.claim().getDefaultFlags().stream())
                .filter(allowed::contains).count();
        final String fallback = on > 0 ? "minecraft:emerald_block" : "minecraft:redstone_block";
        return c().item("claim_settings.public_permissions", fallback)
                .name("gui_public_permissions")
                .lore("gui_public_permissions_lore",
                        Long.toString(on), Integer.toString(allowed.size()))
                .build();
    }

    /** Trustees list. */
    @NotNull
    private MenuIcon trusteesIcon() {
        final int trustees = ref.resolve(c().getPlugin()).map(r -> r.claim().getTrustedUsers().size()
                + r.claim().getTrustedGroups().size() + r.claim().getTrustedTags().size()).orElse(0);
        return c().item("claim_settings.trustees", "minecraft:book")
                .name("gui_trustees")
                .lore("gui_trustees_lore", Integer.toString(trustees))
                .build();
    }

    /** Resize (scaffolding, current size). */
    @NotNull
    private MenuIcon resizeIcon() {
        final Optional<ClaimRef.Resolved> resolved = ref.resolve(c().getPlugin());
        final int width = resolved.map(r -> Math.abs(r.claim().getRegion().getFarCorner().getBlockX()
                - r.claim().getRegion().getNearCorner().getBlockX()) + 1).orElse(0);
        final int depth = resolved.map(r -> Math.abs(r.claim().getRegion().getFarCorner().getBlockZ()
                - r.claim().getRegion().getNearCorner().getBlockZ()) + 1).orElse(0);
        return c().item("claim_settings.resize", "minecraft:scaffolding")
                .name("gui_resize")
                .lore("gui_resize_lore",
                        Integer.toString(width), Integer.toString(depth),
                        Long.toString(resolved.map(r -> (long) r.claim().getRegion().getSurfaceArea()).orElse(0L)))
                .build();
    }

    /** Claim info header, same data as the browser icon (no click line). */
    @NotNull
    private MenuIcon infoIcon() {
        final IconBuilder item = c().item("claim_list.claim", "minecraft:grass_block")
                .nameLiteral(claimName());
        ref.resolve(c().getPlugin()).ifPresent(resolved -> {
            final Claim claim = resolved.claim();
            final Region region = claim.getRegion();
            final int width = Math.abs(region.getFarCorner().getBlockX()
                    - region.getNearCorner().getBlockX()) + 1;
            final int depth = Math.abs(region.getFarCorner().getBlockZ()
                    - region.getNearCorner().getBlockZ()) + 1;
            item.lore("gui_claim_lore_world", resolved.worldName());
            item.lore("gui_claim_lore_size", Integer.toString(width),
                    Integer.toString(depth), Long.toString(region.getSurfaceArea()));
            item.lore("gui_claim_lore_status",
                    c().text(claim.isPrivateClaim() ? "gui_claim_status_private" : "gui_claim_status_normal"));
            if (!claim.getChildren().isEmpty()) {
                item.lore("gui_claim_lore_children",
                        Integer.toString(claim.getChildren().size()));
            }
            if (claim.getWarp() != null) {
                item.lore("gui_claim_lore_warp", GuiText.literal(claim.getWarp().getName()));
            }
        });
        return item.build();
    }

    /**
     * Warp management. States (in order): warps disabled → not rendered; child
     * claim → info-only; viewer can't manage → locked; warp exists → delete
     * flow; no warp → set-here flow.
     */
    @Nullable
    private MenuIcon warpIcon() {
        if (!c().getPlugin().getSettings().getClaims().getWarps().isEnabled()) {
            return null; // the claimwarp command isn't even registered
        }
        final ClaimRef.Resolved r = ref.resolve(c().getPlugin()).orElse(null);
        if (r == null) {
            return null;
        }
        final Claim claim = r.claim();
        if (claim.getParent().isPresent()) {
            return c().item("minecraft:gray_dye").name("gui_claim_warp_child")
                    .lore("gui_claim_warp_child_lore").build();
        }
        if (viewer == null || !c().mayManageWarp(viewer, claim)) {
            return c().item("minecraft:barrier").name("gui_claim_warp_locked")
                    .lore("gui_claim_warp_locked_lore").build();
        }
        final ClaimWarp warp = claim.getWarp();
        if (warp != null) {
            final Position a = warp.getAnchor();
            // The owner may have shrunk the claim since setting the warp — flag it
            final boolean outside = !claim.getRegion().contains(a);
            final IconBuilder item = c().item("claim_settings.warp", "minecraft:ender_eye")
                    .name("gui_claim_warp_set", GuiText.literal(warp.getName()))
                    .lore("gui_claim_warp_delete_lore",
                            c().text(warp.isPubliclyAccessible() ? "gui_claim_warp_vis_public" : "gui_claim_warp_vis_trusted"),
                            fmt(a.getX()), fmt(a.getY()), fmt(a.getZ()));
            if (outside) {
                item.loreBlank().lore("gui_claim_warp_outside_lore");
            }
            return item.build();
        }
        return c().item("claim_settings.warp_create", "minecraft:ender_pearl")
                .name("gui_claim_warp_create")
                .lore("gui_claim_warp_create_lore", GuiText.literal(proposedWarpName(r)))
                .build();
    }

    @NotNull
    private String proposedWarpName(@NotNull ClaimRef.Resolved r) {
        final int max = c().getPlugin().getSettings().getClaims().getWarps().getMaxNameLength();
        return ClaimWarpNames.fromClaimName(
                ClaimNames.displayName(r.claim(), r.worldName(), c().getPlugin()), max);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.0f", Math.floor(v));
    }

    private void put(@NotNull Map<Integer, MenuIcon> items, int size, int slot, @Nullable MenuIcon item) {
        if (item != null && slot >= 0 && slot < size) {
            items.put(slot, item);
        }
    }

    /* Warp click ------------------------------------------------------------- */

    private void handleWarpClick(@NotNull OnlineUser user, @NotNull ClaimRef.Resolved r) {
        if (!c().getPlugin().getSettings().getClaims().getWarps().isEnabled()
                || r.claim().getParent().isPresent()) {
            return; // info-only states
        }
        if (!c().mayManageWarp(user, r.claim())) { // never trust the render
            c().getPlugin().getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return;
        }
        final ClaimWarp warp = r.claim().getWarp();
        if (warp != null) {
            c().confirmDeleteWarp(user, ref, warp.getName());
            return;
        }
        // Set here: the anchor is the player's exact position, so they must be standing inside THIS claim
        final boolean sameWorld = c().getPlugin().getClaimWorld(user.getWorld()).filter(w -> w == r.world()).isPresent();
        if (!sameWorld || !r.claim().getRegion().contains(user.getPosition())) {
            c().getPlugin().getLocales().getLocale("gui_claim_warp_not_inside").ifPresent(user::sendMessage);
            return;
        }
        c().setWarpHere(user, ref, proposedWarpName(r), true);
    }

    @NotNull
    private Settings.GuiSettings.ClaimSettingsMenuSettings settings() {
        return c().getPlugin().getSettings().getGui().getClaimSettingsMenu();
    }

    @NotNull
    private GuiController c() {
        return controller;
    }

}
