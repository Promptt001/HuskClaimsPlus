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

import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.util.Task;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.time.Duration;

/**
 * Captures private chat input for GUI flows.
 *
 * <p>When a menu action needs a value, the GUI asks in chat ("Type the player
 * name…") and records a pending session here. The player's next chat message
 * is intercepted (cancelled — never broadcast to public chat), parsed as the
 * value, and the confirm menu reopens. The real command is only ever executed
 * by the confirm menu's Confirm click.</p>
 *
 * <p>Sessions expire after {@code gui.input-timeout-seconds} (default 60s) and
 * are dropped on quit. Players can always type {@code cancel} to abort.
 * Captured values are only ever passed as arguments to real, permission-checked
 * commands; the GUI never parses or executes them itself.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public class ChatInputListener implements Listener {

    private static final String CANCEL_KEYWORD = "cancel";

    private final BukkitClaimGuiManager manager;
    /** Pending sessions: at most one per player (a new request replaces the old) */
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    ChatInputListener(@NotNull BukkitClaimGuiManager manager) {
        this.manager = manager;
    }

    // Session API ------------------------------------------------------------

    /**
     * Ask the player for a value in chat; their next chat message within the
     * timeout is captured privately as that value.
     *
     * @param player     the player to prompt
     * @param promptKey  locale key for the chat prompt
     * @param onComplete callback receiving the typed value (on the player's scheduler), or empty on timeout
     * @since HuskClaimsPlus 1.0.0
     */
    void requestInput(@NotNull Player player, @NotNull String promptKey,
                      @NotNull Consumer<Optional<String>> onComplete) {
        final UUID uuid = player.getUniqueId();
        clear(uuid); // a new request replaces any pending one

        // Close the open menu FIRST: the vanilla client cannot open chat
        // while a container screen is open, and the menu covers most of the
        // chat area. Must run on the player's scheduler (all callers do).
        player.closeInventory();

        final PendingInput session = new PendingInput(onComplete);
        pending.put(uuid, session);

        // Prompt the player in chat — plain MineDown text, no command prefill
        manager.getPlugin().getLocales().getLocale(promptKey)
                .ifPresent(t -> manager.adaptUser(player).sendMessage(t));

        // Timeout task: expire the session, send the localized
        // timeout message on the player's scheduler, and do NOT invoke the
        // callback — a player who walked away must not get a menu forced open
        // a minute later. Same policy as the cancel keyword: message-only.
        final long timeout = Math.max(30, manager.getPlugin().getSettings().getGui().getInputTimeoutSeconds());
        session.task = manager.getPlugin().runSyncDelayed(() -> {
            if (pending.remove(uuid, session) && player.isOnline()) {
                manager.getPlugin().runSync(player, () -> {
                    if (player.isOnline()) {
                        manager.getPlugin().getLocales().getLocale("gui_input_timeout")
                                .ifPresent(t -> manager.adaptUser(player).sendMessage(t));
                    }
                });
            }
        }, Duration.ofSeconds(timeout));
    }


    public void clear(@NotNull UUID uuid) {
        final PendingInput session = pending.remove(uuid);
        if (session != null && session.task != null) {
            session.task.cancel();
        }
    }

    // Event handling ---------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NotNull AsyncPlayerChatEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final PendingInput session = pending.get(uuid);
        if (session == null) {
            return;
        }
        // The chat message becomes a GUI value: never broadcast it to public chat
        event.setCancelled(true);

        final Player player = event.getPlayer();
        final OnlineUser user = manager.adaptUser(player);
        final String message = event.getMessage().trim();

        // Cancel keyword aborts the flow (case-insensitive)
        if (message.equalsIgnoreCase(CANCEL_KEYWORD)) {
            if (pending.remove(uuid, session)) {
                cancelTask(session);
                manager.getPlugin().getLocales().getLocale("gui_input_cancelled")
                        .ifPresent(user::sendMessage);
            }
            return;
        }

        if (pending.remove(uuid, session)) {
            cancelTask(session);
            // Chat event fires async; hop to the player's scheduler for the callback
            manager.getPlugin().runSync(player, () -> {
                if (player.isOnline()) {
                    session.callback.accept(Optional.of(message));
                }
            });
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void cancelTask(@NotNull PendingInput session) {
        if (session.task != null) {
            session.task.cancel();
        }
    }

    /** Called from the manager on plugin disable: drop all sessions. */
    void onDisable() {
        pending.values().forEach(this::cancelTask);
        pending.clear();
    }

    /**
     * A pending input session: the value callback plus its timeout task.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    private static final class PendingInput {
        private final Consumer<Optional<String>> callback;
        @Nullable
        private Task.Sync task;

        private PendingInput(@NotNull Consumer<Optional<String>> callback) {
            this.callback = callback;
        }
    }

}
