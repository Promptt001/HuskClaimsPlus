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

package net.william278.huskclaims.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Worker threads for one server session.
 * <p>
 * A dedicated server runs one session for the life of the process. A client starts a
 * new session each time a world is opened, so the pool is created when the server
 * starts and drained when it stops instead of being shared through a static field.
 */
public final class FabricScheduler {

    private static final int THREADS = 4;
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final ScheduledThreadPoolExecutor executor;

    private FabricScheduler(@NotNull ScheduledThreadPoolExecutor executor) {
        this.executor = executor;
    }

    /**
     * Start a pool for a new session.
     *
     * @param sessionName short label used in thread names, for example the world folder name
     * @return the running scheduler
     */
    @NotNull
    public static FabricScheduler start(@NotNull String sessionName) {
        final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(THREADS,
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("HuskClaims-" + sessionName.replaceAll("[^A-Za-z0-9_-]", "_") + "-%d")
                        .build());
        // On stop, periodic and delayed work is abandoned; only already queued one-shot
        // tasks (database writes) are allowed to finish.
        pool.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        pool.setRemoveOnCancelPolicy(true);
        return new FabricScheduler(pool);
    }

    @NotNull
    public ScheduledExecutorService executor() {
        return executor;
    }

    public boolean isRunning() {
        return !executor.isShutdown();
    }

    /**
     * Stop accepting work and wait for queued tasks to finish, interrupting any that
     * are still running after the timeout.
     *
     * @param warn receives a message if the pool has to be interrupted
     */
    public void drain(@NotNull Consumer<String> warn) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                warn.accept("Background tasks were still running " + DRAIN_TIMEOUT.toSeconds()
                        + " seconds after shutdown and were interrupted");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
