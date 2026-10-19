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

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimAccess;
import net.william278.huskclaims.claim.ClaimingMode;
import net.william278.huskclaims.claim.ClaimNaming;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.claim.ClaimWarpNames;
import net.william278.huskclaims.gui.menu.ClaimListMenu;
import net.william278.huskclaims.gui.menu.ClaimSettingsMenu;
import net.william278.huskclaims.gui.menu.ConfirmView;
import net.william278.huskclaims.gui.menu.MainMenu;
import net.william278.huskclaims.gui.menu.PublicClaimsMenu;
import net.william278.huskclaims.gui.menu.PublicPermissionsMenu;
import net.william278.huskclaims.gui.menu.TrusteesMenu;
import net.william278.huskclaims.gui.menu.TrustLevelPicker;
import net.william278.huskclaims.gui.menu.TrustMenu;
import net.william278.huskclaims.gui.model.MenuHost;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.gui.model.TextPrompt;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Platform-free logic half of the built-in claim GUI: navigation, permission
 * preflight ({@code may*} checks), value prompts, claim-scoped action flows
 * and text/icon rules shared by every menu. Rendering and events stay with the
 * platform {@link MenuHost} implementations; menus and this controller never
 * see a platform type.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public class GuiController {

    private final HuskClaims plugin;
    private final MenuHost host;

    public GuiController(@NotNull HuskClaims plugin,
                         @NotNull Function<GuiController, MenuHost> hostFactory) {
        this.plugin = plugin;
        this.host = hostFactory.apply(this);
    }

    @NotNull
    public HuskClaims getPlugin() {
        return plugin;
    }

    @NotNull
    public MenuHost getHost() {
        return host;
    }

    /** Convenience alias for getHost() used by migrated menus. */
    @NotNull
    public MenuHost host() {
        return host;
    }

    /* ------------------------------------------------------------------ |
    |  Navigation (menus open each other through these)                   |
    | ------------------------------------------------------------------ */

    /** Opens the main menu (the hub of the GUI). */
    public void openMainMenu(@NotNull OnlineUser user) {
        host.open(user, new MainMenu(this));
    }

    /** Opens the claim blocks menu. */
    public void openBlocksMenu(@NotNull OnlineUser user) {
        host.open(user, new net.william278.huskclaims.gui.menu.BlocksMenu(this));
    }

    /** Opens the claim list ("My Claims"), or the children of a parent claim. */
    public void openClaimList(@NotNull OnlineUser user, @Nullable ClaimRef parent) {
        host.open(user, new ClaimListMenu(this, user, parent));
    }

    /**
     * Opens the claim list in pick mode: a claim click opens the Friends &amp;
     * Trust hub for that claim. The hub's "pick a different claim" header
     * button lands here, so it must show the picker, not re-resolve the claim
     * at the player's feet.
     */
    public void openTrustPicker(@NotNull OnlineUser user) {
        host.open(user, new ClaimListMenu(this, user, null, ClaimListMenu.Mode.PICK_FOR_TRUST));
    }

    /**
     * Opens Claim Settings for a claim. Refuses when the claim no longer
     * resolves (claim-gone diagnostics + the claim list) and re-routes a
     * viewer who may not manage the claim: to the trust hub when they may
     * manage trustees, else to the main menu — never a hub listing someone
     * else's trustees.
     */
    public void openClaimSettings(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        final ClaimRef.Resolved resolved = ref.resolve(plugin).orElse(null);
        if (resolved == null) {
            claimGone(user, ref);
            return;
        }
        if (!mayManageSettings(user, resolved.claim())) {
            plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            if (mayManageTrustees(user, resolved.claim())) {
                openTrustHub(user, ref);
            } else {
                openMainMenu(user);
            }
            return;
        }
        host.open(user, new ClaimSettingsMenu(this, ref, user));
    }

    /**
     * The trust hub entered from Claim Settings: its Back button returns to
     * this claim's settings.
     */
    public void openTrustHubForSettings(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        openTrustHub(user, ref, u -> openClaimSettings(u, ref));
    }

    /**
     * Trust-hub entry point: open the Friends &amp; Trust hub for a specific
     * claim, or — with no target — the default target if the player has an
     * obvious one (the claim at their feet if they may manage its trustees,
     * else their only claim), else the claim picker.
     *
     * @param user   the player
     * @param target the claim to manage trust in, or {@code null} to resolve
     *               the default target / open the picker
     */
    public void openTrustHub(@NotNull OnlineUser user, @Nullable ClaimRef target) {
        openTrustHub(user, target, (Consumer<OnlineUser>) null);
    }

    /**
     * Hub entry with an explicit Back destination (e.g. Claim Settings). With
     * no target, the default target is resolved and the Back destination
     * applies only when a specific claim was given.
     */
    public void openTrustHub(@NotNull OnlineUser user, @Nullable ClaimRef target,
                             @Nullable Consumer<OnlineUser> backTo) {
        if (target != null) {
            host.open(user, new TrustMenu(this, target, backTo));
            return;
        }
        final Optional<ClaimRef> ref = defaultTrustTarget(user);
        if (ref.isEmpty()) {
            // No obvious claim: let the player pick one
            openTrustPicker(user);
            return;
        }
        host.open(user, new TrustMenu(this, ref.get()));
    }

    /**
     * Claim at feet if the viewer may manage its trustees; else their only
     * claim; else none (the caller opens the picker). Never a claim the
     * viewer cannot manage: the hub must only offer controls for claims the
     * viewer can manage.
     */
    @NotNull
    public Optional<ClaimRef> defaultTrustTarget(@NotNull OnlineUser user) {
        final Optional<ClaimRef> here = plugin.getClaimWorld(user.getWorld()).flatMap(w ->
                w.getClaimAt(user.getPosition())
                        .filter(cl -> mayManageTrustees(user, cl))
                        .map(cl -> ClaimRef.of(user.getWorld().getName(), cl)));
        if (here.isPresent()) {
            return here;
        }
        final List<ClaimRef> mine = new ArrayList<>();
        plugin.getClaimWorlds().forEach((k, w) ->
                w.getClaimsByUser(user.getUuid()).forEach(
                        cl -> mine.add(ClaimRef.of(k, cl))));
        return mine.size() == 1 ? Optional.of(mine.getFirst()) : Optional.empty();
    }

    /* ------------------------------------------------------------------ |
    |  Claim-scoped flows (rename, warp, unclaim)                          |
    | ------------------------------------------------------------------ */

    /**
     * Rename flow: prompt (validated + sanitized) → check ownership again →
     * store → persist. When the claim and warp names were in sync, renames the
     * warp too via one scoped command (its validation, uniqueness check,
     * privilege gate and messages; two dispatches would trip the cooldown),
     * then reopens once the change settles.
     */
    public void renameClaim(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        requestInput(user, "gui_prompt_claim_name", raw -> {
            final Optional<String> name = ClaimNames.sanitize(raw);
            if (name.isEmpty()) {
                plugin.getLocales().getLocale("gui_claim_name_invalid").ifPresent(user::sendMessage);
                renameClaim(user, ref); // re-ask
                return;
            }
            ref.resolve(plugin).ifPresentOrElse(resolved -> {
                final Claim claim = resolved.claim();
                // Ownership is checked again here: the player may have lost the claim while typing
                if (!mayManageSettings(user, claim)) {
                    plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
                    return;
                }
                // The pair is "in sync" when the warp name equals what the claim name
                // turns into as a warp name, or the claim name was auto-assigned
                // (adopted from the warp)
                final ClaimWarp oldWarp = claim.getWarp();
                final String oldClaimName = ClaimNames.displayName(claim, resolved.worldName(), plugin);
                final boolean wasAuto = ClaimNaming.isAuto(claim);
                final int max = plugin.getSettings().getClaims().getWarps().getMaxNameLength();
                final boolean inSync = oldWarp != null
                        && ClaimWarpNames.isInSync(oldClaimName, oldWarp.getName(), max);
                ClaimNames.setName(claim, resolved.world(), plugin, name.get());
                plugin.getLocales().getLocale("gui_claim_renamed", name.get()).ifPresent(user::sendMessage);
                if (inSync || wasAuto && oldWarp != null) {
                    // Rename the warp too. If the new warp name is invalid/taken the
                    // command says so in chat and the claim rename stands.
                    final String newWarpName = ClaimWarpNames.fromClaimName(name.get(), max);
                    ClaimActions.runThen(this, user, ref, "claimwarp",
                            new String[]{"rename", newWarpName},
                            () -> openClaimSettings(user, ref));
                    return;
                }
                openClaimSettings(user, ref); // reopen with the new name
            }, () -> claimGone(user, ref));
        });
    }

    /**
     * "Set claim warp here": runs {@code /claimwarp set <name>} through the
     * scoped dispatcher (privilege gate, validation, uniqueness, anti-trap
     * safe-anchor check, persistence, messages). The GUI pre-checks the player
     * is inside THIS claim because set anchors at the executor's position.
     */
    public void setWarpHere(@NotNull OnlineUser user, @NotNull ClaimRef ref,
                            @NotNull String name, boolean refreshIfOpen) {
        final ClaimRef.Resolved r = ref.resolve(plugin).orElse(null);
        if (r == null) {
            claimGone(user, ref);
            return;
        }
        final int max = plugin.getSettings().getClaims().getWarps().getMaxNameLength();
        if (!ClaimWarpNames.isValid(name, max) || ClaimWarpNames.isTaken(plugin, r.claim(), name)) {
            promptWarpName(user, ref); // closes the menu, asks again
            return;
        }
        ClaimActions.runThen(this, user, ref, "claimwarp", new String[]{"set", name},
                () -> openClaimSettings(user, ref));
    }

    /**
     * Fallback when the proposed warp name was invalid or taken: prompt for a
     * new one, re-checking that the player is still inside the claim (they may
     * have walked away while typing).
     */
    public void promptWarpName(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        final int max = plugin.getSettings().getClaims().getWarps().getMaxNameLength();
        requestInput(user, "gui_prompt_warp_name", raw -> {
            final String name = raw.trim();
            if (!ClaimWarpNames.isValid(name, max)) {
                plugin.getLocales().getLocale("claim_warp_name_invalid", Integer.toString(max))
                        .ifPresent(user::sendMessage);
                promptWarpName(user, ref);
                return;
            }
            // position re-check: the player may have walked away while typing
            final boolean inside = ref.resolve(plugin).filter(r ->
                    plugin.getClaimWorld(user.getWorld()).filter(w -> w == r.world()).isPresent()
                            && r.claim().getRegion().contains(user.getPosition())).isPresent();
            if (!inside) {
                plugin.getLocales().getLocale("gui_claim_warp_not_inside").ifPresent(user::sendMessage);
                openClaimSettings(user, ref);
                return;
            }
            setWarpHere(user, ref, name, false); // taken → command replies claim_warp_name_taken
        });
    }

    /**
     * "Delete claim warp": one confirm screen with an ender-eye info item;
     * the click unsets the warp through the scoped dispatcher and reopens
     * once settled.
     */
    public void confirmDeleteWarp(@NotNull OnlineUser user, @NotNull ClaimRef ref,
                                  @NotNull String warpName) {
        final MenuIcon info = item("minecraft:ender_eye")
                .name("gui_claim_warp_delete_confirm_name", GuiText.literal(warpName))
                .lore("gui_claim_warp_delete_confirm_lore", GuiText.literal(warpName))
                .build();
        confirmScoped(user, ref, "claimwarp", new String[]{"unset"}, info,
                u -> openClaimSettings(u, ref), u -> openClaimSettings(u, ref));
    }

    /**
     * Unclaim a specific claim: one confirm screen naming the claim (TNT info
     * item); the click deletes via the editor's user-delete methods — child
     * claims through the child-aware one — after re-checking permission AND
     * ownership at click time, then the claim list opens once the claim is
     * actually gone.
     */
    public void confirmUnclaim(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        final ClaimRef.Resolved resolved = ref.resolve(plugin).orElse(null);
        if (resolved == null) {
            claimGone(user, ref);
            return;
        }
        if (!mayUnclaim(user) || !mayManageSettings(user, resolved.claim())) {
            // Never offer what the commands deny: /unclaim permission AND claim ownership
            plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return;
        }
        final Claim claim = resolved.claim();
        final boolean child = claim.getParent().isPresent();
        final String name = ClaimNames.displayName(claim, resolved.worldName(), plugin);
        final IconBuilder info = item("minecraft:tnt")
                .name("gui_unclaim_confirm_name", name)
                .lore(child ? "gui_unclaim_confirm_child_lore" : "gui_unclaim_confirm_lore",
                        Integer.toString(claim.getRegion().getSurfaceArea()));
        if (!claim.getChildren().isEmpty()) {
            info.lore("gui_claim_lore_children", Integer.toString(claim.getChildren().size()));
        }
        host.open(user, new ConfirmView(this, info.build(),
                u -> {
                    // Never trust the render: re-check command permission AND ownership at click time
                    final ClaimRef.Resolved r = ref.resolve(plugin).orElse(null);
                    if (r == null) {
                        plugin.getLocales().getLocale("gui_claim_gone").ifPresent(u::sendMessage);
                        openClaimList(u, null);
                        return;
                    }
                    if (!mayUnclaim(u) || !mayManageSettings(u, r.claim())) {
                        plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(u::sendMessage);
                        return;
                    }
                    if (r.claim().getParent().isPresent()) {
                        // Child claim: delete via the child-aware editor method
                        final ClaimRef parentRef = ClaimRef.of(r.worldName(), r.claim().getParent().get());
                        parentRef.resolve(plugin).ifPresent(parent ->
                                plugin.userDeleteChildClaim(u, r.world(), r.claim(), parent.claim()));
                    } else {
                        plugin.userDeleteClaim(u, r.world(), r.claim());
                    }
                    // The list only opens after the claim is actually gone (or the timeout)
                    ClaimActions.settle(this, u, () -> ref.resolve(plugin).isEmpty(),
                            () -> openClaimList(u, null), ClaimActions.MAX_SETTLE_TICKS);
                },
                u -> openClaimSettings(u, ref)));
    }

    /**
     * Privacy toggle: {@code /claimprivate on|off} through the scoped
     * dispatcher (it fires the make-public/private events and teleports
     * untrusted players out), re-rendering only once the change landed.
     */
    public void confirmAndSettlePrivacy(@NotNull OnlineUser user, @NotNull ClaimRef ref,
                                        @NotNull Claim claim, @NotNull MenuView view) {
        ClaimActions.runThen(this, user, ref, "claimprivate",
                new String[]{claim.isPrivateClaim() ? "off" : "on"},
                () -> host.refresh(user, view));
    }



    public void openTrusteesMenu(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        host.open(user, new TrusteesMenu(this, ref));
    }


    /** Trustees list with an explicit Back destination (e.g. the hub). */
    public void openTrusteesMenu(@NotNull OnlineUser user, @NotNull ClaimRef ref,
                                 @NotNull Consumer<OnlineUser> backTo) {
        host.open(user, new TrusteesMenu(this, ref, backTo));
    }

    /** Picker for a fully-resolved target: the ✔ marker is a pure in-memory lookup. */
    public void openTrustLevelPicker(@NotNull OnlineUser user, @NotNull ClaimRef ref,
                                     @Nullable PickTarget target) {
        host.open(user, new TrustLevelPicker(this, ref, target));
    }

    /**
     * Add-trustee flow: name prompt (validated) → async database resolve →
     * picker (or re-prompt on unknown). All I/O off the player's thread.
     */
    public void addTrusteePrompt(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        requestInput(user, "gui_prompt_player", raw -> {
            final Optional<String> name = GuiValidators.playerName(
                    plugin.getSettings().getGui().isAllowNamePrefix()).apply(raw.trim());
            if (name.isEmpty()) {
                plugin.getLocales().getLocale("gui_input_invalid_player", raw)
                        .ifPresent(user::sendMessage);
                addTrusteePrompt(user, ref);
                return;
            }
            plugin.runAsync(() -> {
                final Optional<PickTarget> t = plugin.getDatabase().getUser(name.get())
                        .map(sv -> new PickTarget(sv.getUser().getUuid(), sv.getUser().getName()));
                host().runFor(user, () -> {
                    if (!host().isOnline(user)) {
                        return;
                    }
                    t.ifPresentOrElse(
                            target -> openTrustLevelPicker(user, ref, target),
                            () -> {
                                plugin.getLocales().getLocale("error_invalid_user", name.get())
                                        .ifPresent(user::sendMessage);
                                addTrusteePrompt(user, ref);
                            });
                });
            });
        });
    }

    /**
     * Opens the trust level picker for someone already trusted in the claim.
     * Trust commands take a player name, so the picker needs the name the
     * player has now. The database is updated every time a player joins, so it
     * is asked first (async); the claim world's name cache is only a fallback:
     * it keeps the name a player had when they were trusted.
     */
    public void openPickerFor(@NotNull OnlineUser user, @NotNull ClaimRef ref, @NotNull UUID uuid) {
        plugin.runAsync(() -> {
            final Optional<String> currentName = plugin.getDatabase().getUser(uuid)
                    .map(saved -> saved.getUser().getName())
                    .or(() -> ref.resolve(plugin)
                            .flatMap(resolved -> resolved.world().getUser(uuid))
                            .map(User::getName));
            host().runFor(user, () -> {
                if (!host().isOnline(user)) {
                    return;
                }
                currentName.ifPresentOrElse(
                        name -> openTrustLevelPicker(user, ref, new PickTarget(uuid, name)),
                        () -> plugin.getLocales().getLocale("gui_trustee_unknown")
                                .ifPresent(user::sendMessage));
            });
        });
    }

    /**
     * Runs an action only if the target's name still belongs to the same
     * account. Commands identify players by name, and names can change hands.
     */
    public void whenNameStillMatches(@NotNull OnlineUser user, @NotNull PickTarget target,
                                     @NotNull Runnable action) {
        plugin.runAsync(() -> {
            final boolean sameAccount = plugin.getDatabase().getUser(target.name())
                    .map(saved -> saved.getUser().getUuid().equals(target.uuid()))
                    .orElse(false);
            host().runFor(user, () -> {
                if (!host().isOnline(user)) {
                    return;
                }
                if (sameAccount) {
                    action.run();
                } else {
                    plugin.getLocales().getLocale("gui_trustee_name_changed", target.name())
                            .ifPresent(user::sendMessage);
                }
            });
        });
    }

    public void openPublicClaimsMenu(@NotNull OnlineUser user) {
        // The menu is the clickable /visits: it needs both warp commands, and
        // while claim warps are off neither exists, so it stays closed
        if (!mayUse(user, "visits") || !mayUse(user, "visit")) {
            plugin.getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return;
        }
        host.open(user, new PublicClaimsMenu(this, user));
    }

    public void openPublicPermissionsMenu(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        // Disabled in config: the slot is hidden upstream and the menu refuses to open
        if (!plugin.getSettings().getGui().getPublicPermissions().isEnabled()) {
            return;
        }
        host.open(user, new PublicPermissionsMenu(this, ref, user));
    }

    /** Opens the resize edge picker for a claim (re-checks nothing here; the menu re-checks on click). */
    public void openResizeMenu(@NotNull OnlineUser user, @NotNull ClaimRef ref) {
        host.open(user, new net.william278.huskclaims.gui.menu.ResizeMenu(this, ref, user));
    }

    /** Opens the Create Claim stepper. */
    public void openCreateClaimMenu(@NotNull OnlineUser user) {
        host.open(user, new net.william278.huskclaims.gui.menu.CreateClaimMenu(this, user));
    }

    /* ------------------------------------------------------------------ |
    |  Permissions (preflight the REAL commands; the GUI never grants)    |
    | ------------------------------------------------------------------ */

    /**
     * Find the real registered command by name or alias.
     */
    @NotNull
    public Optional<? extends net.william278.huskclaims.command.Command> findCommand(@NotNull String name) {
        return plugin.getCommands().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name)
                        || c.getAliases().stream().anyMatch(a -> a.equalsIgnoreCase(name)))
                .findFirst();
    }

    /**
     * Whether the user may use a registered command (optionally with a
     * sub-permission such as {@code "other"}). Asks the command itself, so
     * the check matches whatever the command's permission nodes actually are.
     */
    public boolean mayUse(@NotNull OnlineUser user, @NotNull String name, @NotNull String... sub) {
        return findCommand(name).filter(c -> c.hasPermission(user, sub)).isPresent();
    }

    /**
     * Whether the player may use the real {@code /unclaim} command (upstream
     * {@code userDeleteClaim} performs no permission check — it relies on the
     * command's — so the GUI must preflight it).
     */
    public boolean mayUnclaim(@NotNull OnlineUser user) {
        return findCommand("unclaim").filter(c -> c.hasPermission(user)).isPresent();
    }

    /** see {@link #mayManageSettings(OnlineUser, Claim)} */
    public boolean mayManageSettings(@NotNull OnlineUser user, @NotNull Claim claim) {
        final boolean hasOtherOverride = findCommand("unclaim")
                .filter(command -> command.hasPermission(user, "other"))
                .isPresent();
        return ClaimAccess.mayManageSettings(claim, user.getUuid(),
                ClaimingMode.ADMIN_CLAIMS.canUse(user), hasOtherOverride);
    }

    /**
     * Whether this player may manage the trustees of a claim: the privilege, or
     * the trust staff override. Decides where a player lands when Claim Settings
     * refuses them, so they are never shown a hub they have no rights in.
     */
    public boolean mayManageTrustees(@NotNull OnlineUser user, @NotNull Claim claim) {
        return claim.isPrivilegeAllowed(TrustLevel.Privilege.MANAGE_TRUSTEES, user, plugin)
                || mayUse(user, "trust", "other");
    }

    /**
     * Whether the user may manage this claim's warp: holds the claimwarp command
     * permission AND either the MANAGE_WARP privilege on the claim or the
     * {@code .other} override.
     */
    public boolean mayManageWarp(@NotNull OnlineUser user, @NotNull Claim claim) {
        return findCommand("claimwarp")
                .filter(c -> c.hasPermission(user))
                .filter(c -> claim.isPrivilegeAllowed(TrustLevel.Privilege.MANAGE_WARP, user, plugin)
                        || c.hasPermission(user, "other"))
                .isPresent();
    }

    /* ------------------------------------------------------------------ |
    |  Command dispatch                                                   |
    | ------------------------------------------------------------------ */

    /**
     * Run an argument-less registered command as the user (permission-checked).
     */
    public void executeCommand(@NotNull OnlineUser user, @NotNull String command) {
        executeCommand(user, command, new String[0]);
    }

    public void executeCommand(@NotNull OnlineUser user, @NotNull String command, @NotNull String[] args) {
        if (permitted(user, command).isEmpty()) {
            return;
        }
        findCommand(command).ifPresent(c -> c.onExecuted(user, args));
    }

    @NotNull
    private Optional<? extends net.william278.huskclaims.command.Command> permitted(
            @NotNull OnlineUser user, @NotNull String name) {
        final Optional<? extends net.william278.huskclaims.command.Command> registered = findCommand(name);
        if (registered.isEmpty() || !registered.get().hasPermission(user)) {
            plugin.getLocales().getLocale("gui_error_no_permission")
                    .ifPresent(user::sendMessage);
            return Optional.empty();
        }
        return registered;
    }

    /* ------------------------------------------------------------------ |
    |  Prompts + confirm                                                  |
    | ------------------------------------------------------------------ */

    /**
     * Ask the player for a value (host delivers the prompt; chat capture or an
     * anvil is a platform concern). The value is returned raw; callers validate.
     */
    public void requestInput(@NotNull OnlineUser user, @NotNull String promptKey,
                             @NotNull Consumer<String> onValue) {
        host.prompt(user, new TextPrompt(promptKey, "", 64, onValue, () -> { }));
    }

    /**
     * Prompt for a validated value, then open the confirm view. The command only
     * ever executes via the Confirm click.
     */
    public void requestAndConfirm(@NotNull OnlineUser user, @NotNull String command,
                                  @NotNull String promptKey,
                                  @NotNull Function<String, Optional<String>> validator,
                                  @Nullable String invalidKey, @NotNull String actionKey,
                                  @NotNull Consumer<OnlineUser> backTo) {
        if (permitted(user, command).isEmpty()) {
            return;
        }
        askAndConfirm(user, command, promptKey, validator, invalidKey, actionKey, backTo, 3);
    }

    private void askAndConfirm(@NotNull OnlineUser user, @NotNull String command,
                               @NotNull String promptKey,
                               @NotNull Function<String, Optional<String>> validator,
                               @Nullable String invalidKey, @NotNull String actionKey,
                               @NotNull Consumer<OnlineUser> backTo, int attemptsLeft) {
        requestInput(user, promptKey, value -> {
            final Optional<String> ok = validator.apply(value.trim());
            if (ok.isPresent()) {
                confirmAndExecute(user, command, new String[]{ok.get()}, actionKey, ok.get(), backTo);
                return;
            }
            if (invalidKey != null) {
                plugin.getLocales().getLocale(invalidKey, value)
                        .ifPresent(user::sendMessage);
            }
            if (attemptsLeft > 1) {
                askAndConfirm(user, command, promptKey, validator, invalidKey, actionKey, backTo, attemptsLeft - 1);
            }
        });
    }

    /**
     * Open the confirm view for a parameterized command. The Confirm click runs
     * the command with the provided arguments.
     */
    public void confirmAndExecute(@NotNull OnlineUser user, @NotNull String command,
                                  @NotNull String[] args, @NotNull String actionKey,
                                  @NotNull String value, @NotNull Consumer<OnlineUser> backTo) {
        if (permitted(user, command).isEmpty()) {
            return;
        }
        host.open(user, new ConfirmView(this, actionInfo(actionKey, value),
                u -> executeCommand(u, command, args), backTo));
    }

    /**
     * Confirm, then run an in-claim command against the referenced claim; reopen
     * once the change has applied.
     */
    public void confirmScoped(@NotNull OnlineUser user, @NotNull ClaimRef ref, @NotNull String command,
                              @NotNull String[] args, @NotNull MenuIcon info,
                              @NotNull Consumer<OnlineUser> back, @NotNull Consumer<OnlineUser> reopen) {
        host.open(user, new ConfirmView(this, info,
                u -> ClaimActions.runThen(this, u, ref, command, args, () -> reopen.accept(u)), back));
    }

    /**
     * Chat value (validated) → claim-scoped confirm, bound to a specific
     * {@link ClaimRef} instead of the standing claim. The command only ever
     * executes via the Confirm click; {@code reopen} runs once the change has
     * settled (or immediately on pre-dispatch rejection).
     */
    public void requestAndConfirmScoped(@NotNull OnlineUser user, @NotNull ClaimRef ref,
                                        @NotNull String command, @NotNull String promptKey,
                                        @NotNull Function<String, Optional<String>> validator,
                                        @NotNull String invalidKey,
                                        @NotNull Function<String, MenuIcon> infoFor,
                                        @NotNull Consumer<OnlineUser> back,
                                        @NotNull Consumer<OnlineUser> reopen) {
        if (permitted(user, command).isEmpty()) {
            return;
        }
        requestInput(user, promptKey, raw -> {
            final Optional<String> ok = validator.apply(raw.trim());
            if (ok.isEmpty()) {
                plugin.getLocales().getLocale(invalidKey, raw)
                        .ifPresent(user::sendMessage);
                requestAndConfirmScoped(user, ref, command, promptKey, validator, invalidKey,
                        infoFor, back, reopen);
                return;
            }
            confirmScoped(user, ref, command, new String[]{ok.get()}, infoFor.apply(ok.get()), back, reopen);
        });
    }

    /** The info item for a confirm: action name, action lore, then the value the player typed. */
    @NotNull
    public MenuIcon actionInfo(@NotNull String actionKey, @NotNull String value) {
        return item("minecraft:book")
                .name(actionKey)
                .lore(actionKey + "_lore")
                .lore("gui_confirm_value", value)
                .build();
    }

    /* ------------------------------------------------------------------ |
    |  Item text + icons (locale TEMPLATE → coloured → filled)            |
    | ------------------------------------------------------------------ */

    /** Missing GUI locale keys already warned about. */
    private final java.util.Set<String> warnedMissingKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Locale TEMPLATE → coloured → filled. Missing keys are LOUD: the rendered
     * text becomes {@code §c<gui_key>} and one WARNING is logged per key —
     * never a silent fallback.
     *
     * @param key  the locale key
     * @param args replacement values (inserted after colouring: literal, never colour-injecting)
     * @return the formatted item text
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public String text(@NotNull String key, @NotNull String... args) {
        return plugin.getLocales().getRawLocale(key)
                .map(GuiText::color)
                .map(c -> GuiText.fill(c, args))
                .orElseGet(() -> {
                    if (warnedMissingKeys.add(key)) {
                        plugin.log(Level.WARNING, "Missing GUI locale key '" + key
                                + "' — messages file is stale or a key was never added");
                    }
                    return "§c<" + key + ">";
                });
    }

    /**
     * Resolve a GUI icon item key from the {@code gui.icon_materials} config map,
     * falling back to the given default when unset or not an item.
     *
     * @param key      the icon key (e.g. {@code trust.level.build})
     * @param fallback item key used when the key is missing/invalid
     * @return the configured or fallback item key
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public String icon(@NotNull String key, @NotNull String fallback) {
        final String id = plugin.getSettings().getGui().getIconMaterials().get(key);
        if (id == null) {
            return fallback;
        }
        final String normalised = id.contains(":") ? id : "minecraft:" + id;
        return host.isItem(normalised) ? normalised : fallback;
    }

    /**
     * Icon item key for a trust level: config override via {@code
     * gui.icon_materials} key {@code trust.level.<id>}, else the category
     * fallback inferred from what the level allows.
     *
     * @param level the trust level
     * @return the configured or fallback item key
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public String trustLevelIconKey(@NotNull TrustLevel level) {
        return icon("trust.level." + level.getId(),
                "minecraft:" + TrustIcons.fallbackIcon(level).toLowerCase(Locale.ROOT));
    }

    /**
     * Ability bullets for a trust level, rendered into an icon: grouped,
     * de-duplicated phrases from {@link LevelBullets}, via {@code
     * gui_trust_bullet} (with the "…and k more" count variant when truncated).
     * With {@code withIncludes}, higher levels headed by "Everything in
     * &lt;lower&gt;, plus:" when a lower level's flags are fully covered.
     *
     * @param item         the icon under construction
     * @param level        the trust level
     * @param withIncludes whether to prepend the "includes" header
     * @since HuskClaimsPlus 1.0.0
     */
    public void levelBullets(@NotNull IconBuilder item, @NotNull TrustLevel level, boolean withIncludes) {
        final List<TrustLevel> all = plugin.getTrustLevels();
        if (withIncludes) {
            all.stream()
                    .filter(l -> l.getWeight() < level.getWeight()
                            && level.getFlags().containsAll(l.getFlags()))
                    .max(java.util.Comparator.comparingInt(TrustLevel::getWeight))
                    .ifPresent(l -> item.lore("gui_trust_includes", l.getDisplayName()));
        }
        for (String phraseKey : LevelBullets.of(level)) {
            final int sep = phraseKey.indexOf(':');
            if (phraseKey.startsWith("gui_trust_more")) {
                item.lore("gui_trust_more", phraseKey.substring(sep + 1));
            } else {
                item.lore("gui_trust_bullet", text(phraseKey));
            }
        }
    }

    /**
     * Confirm info item for granting a trust level: category icon + a warning
     * when the level carries privileges. Shared by the hub and the picker.
     *
     * @param level       the level being granted
     * @param playerName  the player receiving it
     * @param claimName   the claim's display name
     * @return the built info icon
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public MenuIcon trustGrantInfo(@NotNull TrustLevel level, @NotNull String playerName,
                                   @NotNull String claimName) {
        final IconBuilder builder = item(trustLevelIconKey(level))
                .name("gui_trust_confirm_grant", playerName, level.getDisplayName(), claimName);
        if (!level.getPrivileges().isEmpty()) {
            builder.lore("gui_trust_manage_warning");
        } else {
            builder.lore("gui_trust_grant_lore", level.getDisplayName());
        }
        return builder.build();
    }

    /** Start an icon from an item id, for example {@code "minecraft:arrow"}. */
    @NotNull
    public IconBuilder item(@NotNull String itemKey) {
        return new IconBuilder(this, itemKey);
    }

    /** Start an icon from a configurable icon key with its default item. */
    @NotNull
    public IconBuilder item(@NotNull String iconKey, @NotNull String fallbackItem) {
        return new IconBuilder(this, icon(iconKey, fallbackItem));
    }

    /** The configured filler, rendered once per refresh by hosts. */
    @NotNull
    public MenuIcon filler() {
        return item(validItemOr(plugin.getSettings().getGui().getFillerMaterial(),
                "minecraft:black_stained_glass_pane")).name("gui_filler_name").build();
    }

    /** Default title for a view, rendered (section-coded). */
    @NotNull
    public String defaultTitle(@NotNull MenuView view) {
        return plugin.getLocales().getRawLocale("gui_" + view.typeName() + "_title")
                .map(GuiText::color)
                .orElse("Claim Menu");
    }

    @NotNull
    private String validItemOr(@NotNull String id, @NotNull String fallback) {
        final String normalised = id.contains(":") ? id : "minecraft:" + id;
        return host.isItem(normalised) ? normalised : fallback;
    }

    /* ------------------------------------------------------------------ |
    |  Claim-gone diagnostics                                             |
    | ------------------------------------------------------------------ */

    /** Claim no longer resolves: message + fall back to the claim list. */
    public void claimGone(@NotNull OnlineUser user) {
        plugin.getLocales().getLocale("gui_claim_gone").ifPresent(user::sendMessage);
        openClaimList(user, null);
    }

    /**
     * Claim-gone with diagnostics: logs the ref, the registered world keys with
     * their ids, and whether ANY world holds a claim with that box, so the next
     * "claim no longer exists" report is a one-line root cause in the log.
     */
    public void claimGone(@NotNull OnlineUser user, @Nullable ClaimRef ref) {
        if (ref != null) {
            logClaimGoneDiagnostics(ref);
        }
        claimGone(user);
    }

    private void logClaimGoneDiagnostics(@NotNull ClaimRef ref) {
        final Map<String, Integer> ids = new TreeMap<>();
        plugin.getClaimWorlds().forEach((k, w) -> ids.put(k, w.getId()));
        final boolean boxExists = plugin.getClaimWorlds().values().stream()
                .flatMap(w -> w.getClaims().stream())
                .anyMatch(ref::matches);
        plugin.log(Level.WARNING, "Claim GUI: claim no longer resolves (world=" + ref.worldName()
                + ", owner=" + (ref.owner() != null ? ref.owner() : "unknown")
                + ", box=(" + ref.x1() + "," + ref.z1() + ")-(" + ref.x2() + "," + ref.z2()
                + "), parent=" + (ref.parent() != null)
                + "). Registered claim worlds " + ids + ". Box exists under some owner: " + boxExists + ".");
    }

}
