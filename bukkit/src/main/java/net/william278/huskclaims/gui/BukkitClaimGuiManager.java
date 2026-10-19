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

import net.william278.huskclaims.BukkitHuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimAccess;
import net.william278.huskclaims.claim.ClaimNaming;
import net.william278.huskclaims.claim.ClaimingMode;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.claim.ClaimWarpNames;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.command.GuiProvider;
import net.william278.huskclaims.event.BukkitPostCreateClaimEvent;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.user.BukkitUser;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.function.BiConsumer;
import org.bukkit.event.inventory.ClickType;

/**
 * Bukkit implementation of the built-in claim GUI: renders menus, tracks
 * open sessions, cancels item-taking clicks/drag, and routes actions to
 * existing HuskClaims commands and services. Implements {@link GuiProvider}
 * so {@code /huskclaimgui} delegates here.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public class BukkitClaimGuiManager implements GuiProvider, Listener {

    private final BukkitHuskClaims plugin;
    /** Marker on every item rendered into a menu; a tagged item outside a menu is a bug and gets deleted */
    private final NamespacedKey guiItemKey;
    /** Marks claim tools handed out by the main menu; see {@link ClaimToolDelivery} */
    private final NamespacedKey handoutKey;
    /** Players with one of our menus open — used ONLY for shutdown cleanup and stale-action checks,
     *  never to decide whether a click is cancelled (the holder check does that). */
    private final Map<UUID, ClaimGuiMenu> openMenus = new ConcurrentHashMap<>();
    /** Debounce: at most one queued click action per player */
    private final Set<UUID> pendingActions = ConcurrentHashMap.newKeySet();
    /** Captures private chat input for GUI value prompts */
    private final ChatInputListener chatInput = new ChatInputListener(this);
    /** Menu locale keys already warned about: a missing key is logged once */
    private final Set<String> warnedMissingKeys = ConcurrentHashMap.newKeySet();

    private final GuiController controller;

    public BukkitClaimGuiManager(@NotNull BukkitHuskClaims plugin) {
        this.plugin = plugin;
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");
        this.handoutKey = new NamespacedKey(plugin, "claim_tool_handout");
        final BukkitMenuHost host = new BukkitMenuHost(this);
        this.controller = new GuiController(plugin, c -> host);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(chatInput, plugin);
        auditClaimWorldIds();
        warnLegacyWarpNames();
    }

    /**
     * Names created before the warp-name rules may contain characters that break
     * chat click actions, so those rows are shown without one. This lists them once
     * at startup so their owners can be asked to rename them; nothing is renamed here.
     */
    private void warnLegacyWarpNames() {
        final List<String> unsafe = plugin.getClaimWorlds().entrySet().stream()
                .flatMap(entry -> entry.getValue().getWarpClaims().stream()
                        .filter(claim -> claim.getWarp() != null && !ClaimWarpNames.isClickSafe(claim.getWarp().getName()))
                        .map(claim -> entry.getKey() + ": " + claim.getWarp().getName()))
                .toList();
        if (!unsafe.isEmpty()) {
            plugin.log(Level.WARNING, "Some claim warps have old-style names that will not be clickable "
                    + "in /visits (ask their owners to rename them): " + String.join(", ", unsafe));
        }
    }

    /**
     * Logs the id of every registered claim world and flags duplicates. Duplicate ids
     * mean {@code updateClaimWorld} saves will overwrite each other's rows (the
     * pre-Fix-A SQLite bug, or a corrupted table) — restart before claiming.
     */
    private void auditClaimWorldIds() {
        final Map<Integer, List<String>> byId = new TreeMap<>();
        plugin.getClaimWorlds().forEach((k, w) -> byId.computeIfAbsent(w.getId(), i -> new ArrayList<>()).add(k));
        plugin.log(Level.INFO, "Claim world ids: " + byId);
        byId.forEach((id, keys) -> {
            if (keys.size() > 1) {
                plugin.log(Level.SEVERE, "Claim worlds " + keys + " share id " + id
                        + " — saves will overwrite each other (SQLite createClaimWorld id bug). Restart before claiming.");
            }
        });
    }

    // Session tracking ------------------------------------------------------

    @Override
    public boolean isGuiAvailable() {
        return plugin.getSettings().getGui().isEnabled();
    }

    @Override
    public void openClaimGui(@NotNull OnlineUser user) {
        if (user instanceof BukkitUser bukkitUser) {
            final Player player = bukkitUser.getBukkitPlayer();
            // Safe from any thread: hops to the player's scheduler (main thread on Paper, entity thread on Folia)
            plugin.runSync(player, () -> {
                if (player.isOnline()) {
                    openMainMenu(player);
                }
            });
        }
    }

    void openMainMenu(@NotNull Player player) {
        controller.openMainMenu(adaptUser(player));
    }

    // Claim list and per-claim settings

    void openClaimList(@NotNull Player player, @Nullable ClaimRef parent) {
        controller.openClaimList(adaptUser(player), parent);
    }

    /**
     * "Public Claims" warp browser (user request 2026-10-14): one clickable
     * slot per usable claim warp — public warps plus the opener's own
     * trusted-only warps. Clicking teleports via the /visit checks.
     *
     * @param player the player
     * @since HuskClaimsPlus 1.0.0
     */
    /**
     * Opens the Public Claims browser. It is the menu form of {@code /visits}, and each click
     * is a {@code /visit}, so a player needs both commands; when claim warps are turned off,
     * neither command exists and the menu stays closed.
     */


    void openClaimSettings(@NotNull Player player, @NotNull ClaimRef ref) {
        controller.openClaimSettings(adaptUser(player), ref);
    }




    /** The claim a menu pointed at is gone: say so and reopen the browser. */
    void claimGone(@NotNull Player player, @NotNull OnlineUser user) {
        controller.claimGone(user);
    }

    /** Claim-gone with diagnostics: one log line naming the broken reference. */
    void claimGone(@NotNull Player player, @NotNull OnlineUser user, @Nullable ClaimRef ref) {
        controller.claimGone(user, ref);
    }

    /** Rename flow, shared-model: prompt → validate → rename (warp kept in sync). */
    void renameClaim(@NotNull Player player, @NotNull OnlineUser user, @NotNull ClaimRef ref) {
        controller.renameClaim(user, ref);
    }
    /** Set-warp-here flow, shared-model. */
    void setWarpHere(@NotNull Player player, @NotNull OnlineUser user, @NotNull ClaimRef ref,
                     @NotNull String name, boolean fromOpenMenu) {
        controller.setWarpHere(user, ref, name, fromOpenMenu);
    }
    /** Warp-name prompt, shared-model. */
    void promptWarpName(@NotNull Player player, @NotNull OnlineUser user, @NotNull ClaimRef ref) {
        controller.promptWarpName(user, ref);
    }
    /** Delete-warp confirm, shared-model. */
    void confirmDeleteWarp(@NotNull Player player, @NotNull OnlineUser user, @NotNull ClaimRef ref,
                           @NotNull String warpName) {
        controller.confirmDeleteWarp(user, ref, warpName);
    }
    /** Unclaim confirm, shared-model. */
    void confirmUnclaim(@NotNull Player player, @NotNull OnlineUser user, @NotNull ClaimRef ref) {
        controller.confirmUnclaim(user, ref);
    }
    /**
     * The registered world key for this exact ClaimWorld instance, resolved by identity
     * (==), never {@code equals()} — ClaimWorld#equals is id-only and wrong when ids
     * collide.
     */
    @NotNull

    void openBlocksMenu(@NotNull Player player) {
        controller.openBlocksMenu(adaptUser(player));
    }

    GuiController getController() {
        return controller;
    }

    /** The menu this player currently has open, for host refresh checks. */
    @Nullable
    ClaimGuiMenu openMenuOf(@NotNull Player player) {
        return openMenus.get(player.getUniqueId());
    }

    @NotNull
    ChatInputListener getChatInput() {
        return chatInput;
    }

    void openMenu(@NotNull Player player, @NotNull ClaimGuiMenu menu) {
        // Title: per-menu dynamic title if provided, else the locale default
        String title = menu instanceof AbstractMenu m && m.getTitle().isPresent()
                ? m.getTitle().get()
                : plugin.getLocales().getRawLocale("gui_" + menu.getType().name().toLowerCase(Locale.ROOT) + "_title")
                        .orElse("Claim Menu");
        // Inventory titles are capped at 32 chars on older versions
        if (title.length() > 32) {
            title = title.substring(0, 32);
        }
        final int size = menu.getRows() * 9;
        final Inventory inv = Bukkit.createInventory(menu, size, title);
        // Safety net: every item goes through tag(), hand-built items included
        menu.getItems().forEach((slot, item) -> {
            if (slot >= 0 && slot < size) { // never throw on a bad config slot
                inv.setItem(slot, tag(item));
            }
        });
        // Fill unlisted slots with filler
        final ItemStack filler = createFiller();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
        if (menu instanceof AbstractMenu m) {
            m.setInventory(inv);
        }
        player.openInventory(inv);
        // AFTER openInventory: the InventoryCloseEvent for the previous menu (if any)
        // has already fired by now, so recording here can no longer be wiped by it (D1 fix)
        openMenus.put(player.getUniqueId(), menu);
    }

    /**
     * Rewrite the items of the menu the player currently has open, without
     * closing/reopening (no cursor reset, no flicker). Call on the player's
     * scheduler — {@code handleClick} already qualifies. Safe: not inside a
     * click event, so top-inventory mutation cannot produce ghost items (D5).
     *
     * @param player the player whose menu to refresh
     * @param menu   the menu instance that should be open
     * @since HuskClaimsPlus 1.0.0
     */
    void refresh(@NotNull Player player, @NotNull AbstractMenu menu) {
        if (openMenus.get(player.getUniqueId()) != menu) {
            return; // the player has moved on to another menu (or closed it)
        }
        final Inventory inv = menu.getInventory();
        final ItemStack filler = createFiller();
        final Map<Integer, ItemStack> items = menu.getItems();
        for (int i = 0; i < inv.getSize(); i++) {
            final ItemStack it = items.get(i);
            inv.setItem(i, it != null ? tag(it) : filler);
        }
    }

    /**
     * Sub-list of one page of items, clamped (claims can be deleted while a
     * menu is open).
     *
     * @param all      all items
     * @param page     0-based page index
     * @param perPage  items per page
     * @return the page's items (empty when out of range)
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    static <T> List<T> page(@NotNull List<T> all, int page, int perPage) {
        final int from = Math.max(0, page * perPage);
        return from >= all.size() ? List.of() : all.subList(from, Math.min(all.size(), from + perPage));
    }

    /**
     * Number of pages needed for the given item count (at least 1).
     *
     * @param size    total item count
     * @param perPage items per page
     * @return page count
     * @since HuskClaimsPlus 1.0.0
     */
    static int pageCount(int size, int perPage) {
        return Math.max(1, (size + perPage - 1) / perPage);
    }

    // Item helpers ---------------------------------------------------------

    /**
     * Icon for a trust level: config override via {@code gui.icon_materials}
     * key {@code trust.level.<id>}, else a distinct material per weight band
     *.
     *
     * @param level the trust level
     * @return the icon material for that level
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Material trustLevelIcon(@NotNull TrustLevel level) {
        // Sort id-first, then capability fallback
        final Material fallback = Material.matchMaterial(TrustIcons.fallbackIcon(level));
        return icon("trust.level." + level.getId(), fallback != null ? fallback : Material.OAK_DOOR);
    }

    /**
     * Legacy-signature icon builder, now routed through GuiItem/GuiText:
     * colour-translated, non-italic, loud on missing keys. Prefer GuiItem directly in new code.
     */
    @NotNull
    ItemStack createIcon(@NotNull Material material, @NotNull String nameKey, @NotNull String... loreKeys) {
        final GuiItem item = GuiItem.of(material).name(this, nameKey);
        for (String key : loreKeys) {
            item.lore(this, key);
        }
        return item.build(this);
    }

    /**
     * Tag an item as GUI-rendered (PDC marker). Every item placed into a menu
     * must pass through this — {@link #createIcon} does it, but items built
     * by hand (heads, stepper value stacks, toggle items) must be tagged too,
     * so the purge-on-sight safety net always applies.
     *
     * @param stack the item to tag
     * @return the same stack, tagged
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    ItemStack tag(@NotNull ItemStack stack) {
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(guiItemKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    boolean isGuiItem(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(guiItemKey, PersistentDataType.BYTE);
    }

    /**
     * Remove every GUI-tagged item from a player's inventory and cursor. Logs a warning
     * if any were found (any occurrence means a protection bug slipped through).
     *
     * @param player the player
     * @param reason why the purge is running (for the warning message)
     * @since HuskClaimsPlus 1.0.0
     */
    void purgeGuiItems(@NotNull Player player, @NotNull String reason) {
        if (!player.isOnline()) {
            return;
        }
        int removed = 0;
        final PlayerInventory inv = player.getInventory();
        final ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGuiItem(contents[i])) {
                inv.setItem(i, null);
                removed++;
            }
        }
        if (isGuiItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed++;
        }
        if (removed > 0) {
            plugin.log(Level.WARNING, "Removed " + removed + " claim-GUI item(s) from "
                    + player.getName() + " (" + reason + ") - please report how this happened");
        }
    }

    /**
     * Close all open claim menus, drop pending chat-input sessions and purge
     * stray GUI items; call from onDisable / before reload.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    public void closeAll() {
        openMenus.keySet().forEach(uuid -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
                purgeGuiItems(player, "plugin disable");
            }
        });
        openMenus.clear();
        chatInput.onDisable();
    }

    @NotNull
    ItemStack createFiller() {
        return createIcon(parseMaterial(plugin.getSettings().getGui().getFillerMaterial()), "gui_filler_name");
    }

    /**
     * Locale TEMPLATE → coloured → filled. Missing keys are
     * LOUD: the rendered name/lore becomes {@code §c<gui_key>} and one
     * WARNING is logged per key, never a silent fallback to the vanilla
     * item name.
     *
     * @param key  the locale key
     * @param args replacement values (inserted after colouring: literal, never colour-injecting)
     * @return the formatted item text
     * @since HuskClaimsPlus 1.0.0
     */
    /**
     * Raw locale text for inventory titles: titles are not
     * item text, but raw access stays in the manager so the source scan can
     * exempt exactly this class.
     *
     * @param key the locale key
     * @return the raw title text, if present
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Optional<String> title(@NotNull String key) {
        return plugin.getLocales().getRawLocale(key);
    }

    @NotNull
    String text(@NotNull String key, @NotNull String... args) {
        return plugin.getLocales().getRawLocale(key)          // 1-arg overload: no replacement yet
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
     * Resolve a GUI icon material from the {@code gui.icon_materials} config map,
     * falling back to the given default when unset or invalid (a config
     * must not promise customization it does not deliver).
     *
     * @param key      the icon key (e.g. {@code trust.level.build})
     * @param fallback material used when the key is missing/invalid
     * @return the configured material or the fallback
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    Material icon(@NotNull String key, @NotNull Material fallback) {
        final String id = plugin.getSettings().getGui().getIconMaterials().get(key);
        if (id == null) {
            return fallback;
        }
        final Material m = Material.matchMaterial(id.replace("minecraft:", ""));
        return (m == null || !m.isItem() || m.isAir()) ? fallback : m;
    }

    @NotNull
    Material parseMaterial(@NotNull String id) {
        final Material m = Material.matchMaterial(id.replace("minecraft:", ""));
        return m != null ? m : Material.LIME_STAINED_GLASS_PANE;
    }

    // Event handling -------------------------------------------------------
    // RULES fix):
    //   - Never call InventoryView methods (binary-incompatible: compiled against
    //     spigot-api 1.17.1 where it is a class; an interface on modern Paper).
    //     event.getInventory() IS the top inventory; use event.getRawSlot().
    //   - If the top inventory's holder is one of our menus, cancel EVERY click,
    //     both halves of the window, any click type — no exceptions, no state gating.
    //   - Menu actions run on the NEXT TICK on the player's scheduler, never inside the event.

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        final Inventory top = event.getInventory();
        if (top.getHolder() instanceof ClaimGuiMenu menu) {
            // Our menu is open: cancel EVERY click, top or bottom, any click type. No exceptions.
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            final int rawSlot = event.getRawSlot();
            if (rawSlot < 0 || rawSlot >= top.getSize()) {
                return; // bottom inventory or outside the window: cancelled, no action
            }
            if (!event.getClick().isLeftClick() && !event.getClick().isRightClick()) {
                return; // number keys, drop, middle-click, double-click: cancelled, no action
            }
            if (!pendingActions.add(player.getUniqueId())) {
                return; // an action is already queued (spam click)
            }
            // Capture the click type BEFORE scheduling: it lives on the event, not the InventoryView
            final ClickType click = event.getClick();
            plugin.runSync(player, () -> {
                try {
                    if (player.isOnline() && openMenus.get(player.getUniqueId()) == menu) {
                        menu.handleClick(player, adaptUser(player), rawSlot, click);
                    }
                } finally {
                    pendingActions.remove(player.getUniqueId());
                }
            });
            return;
        }

        // Not our menu. A GUI item must never exist outside a menu: delete it on sight.
        // (This handler also receives InventoryCreativeEvent, which extends InventoryClickEvent.)
        final boolean current = isGuiItem(event.getCurrentItem());
        final boolean cursor = isGuiItem(event.getCursor());
        if (current || cursor) {
            event.setCancelled(true);
            if (current) {
                event.setCurrentItem(null);
            }
            if (event instanceof InventoryCreativeEvent creative && cursor) {
                creative.setCursor(new ItemStack(Material.AIR));
            }
            if (event.getWhoClicked() instanceof Player player) {
                plugin.runSync(player, () -> purgeGuiItems(player, "stray item click"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ClaimGuiMenu || isGuiItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ClaimGuiMenu menu
                && event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId(), menu); // only if THIS menu is the recorded one
            plugin.runSync(player, () -> purgeGuiItems(player, "menu close"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (isGuiItem(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            plugin.log(Level.WARNING, "Deleted dropped claim-GUI item from " + event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(@NotNull EntityPickupItemEvent event) {
        if (isGuiItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        if (isGuiItem(event.getItemInHand())) {
            event.setCancelled(true);
            plugin.runSync(event.getPlayer(), () -> purgeGuiItems(event.getPlayer(), "block place"));
        }
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.runSync(player, () -> purgeGuiItems(player, "join sweep"));
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        // A click queued in the player's last tick would otherwise leave its debounce entry behind
        // and block every click after they rejoin
        pendingActions.remove(player.getUniqueId());
        openMenus.remove(player.getUniqueId());
        // WarpCooldowns.onQuit is now cleared in common UserListener.onUserQuit,
        // so it also runs on Fabric and on Paper with the GUI disabled
    }

    /** A claim tool handed out by the menu must not be smelted back into gold. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSmelt(@NotNull FurnaceSmeltEvent event) {
        if (ClaimToolDelivery.isHandout(event.getSource(), this)) {
            event.setCancelled(true);
        }
    }

    /**
     * Keeps the handout marker through crafting. Combining two tools in a crafting grid builds
     * a new item, which would otherwise come out unmarked and could then be smelted.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(@NotNull PrepareItemCraftEvent event) {
        final CraftingInventory grid = event.getInventory();
        final ItemStack result = grid.getResult();
        if (result == null || result.getType().isAir() || ClaimToolDelivery.isHandout(result, this)) {
            return;
        }
        final boolean madeFromHandout = Arrays.stream(grid.getMatrix())
                .anyMatch(ingredient -> ClaimToolDelivery.isHandout(ingredient, this));
        if (madeFromHandout) {
            grid.setResult(ClaimToolDelivery.markAsHandout(result.clone(), this));
        }
    }

    /**
     * Gives a newly created top-level player claim a default name, {@code <Owner>_<NN>}.
     * <p>
     * Runs at MONITOR priority, after other plugins have had their say, and runs even when
     * the GUI is disabled, because this listener is always registered. Admin claims and
     * child claims are not named.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimCreated(@NotNull BukkitPostCreateClaimEvent event) {
        final Claim claim = event.getClaim();
        final OnlineUser creator = event.getOnlineUser();
        // Only name a claim after the player who owns it, should another plugin ever fire this event for someone else
        final boolean createdByOwner = claim.getOwner().map(creator.getUuid()::equals).orElse(false);
        if (claim.isChildClaim() || !createdByOwner) {
            return;
        }
        if (ClaimNaming.assignDefaultIfMissing(plugin, claim, creator.getName())) {
            final ClaimWorld world = event.getClaimWorld();
            plugin.runQueued(() -> plugin.getDatabase().updateClaimWorld(world));
        }
    }

    /** The persistent-data key that marks a handed-out claim tool; see {@link ClaimToolDelivery}. */
    @NotNull
    NamespacedKey getHandoutKey() {
        return handoutKey;
    }

    @NotNull
    BukkitHuskClaims getPlugin() {
        return plugin;
    }

    @NotNull
    OnlineUser adaptUser(@NotNull Player player) {
        return plugin.getOnlineUser(player);
    }

}
