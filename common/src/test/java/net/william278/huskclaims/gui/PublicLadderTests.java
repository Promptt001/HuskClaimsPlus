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

import de.exlll.configlib.YamlConfigurations;
import net.william278.huskclaims.config.TrustLevels;
import net.william278.huskclaims.trust.TrustLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@DisplayName("Public access ladder")
public class PublicLadderTests {

    private static List<TrustLevel> all;
    private static List<TrustLevel> cands;
    private static TrustLevel access;
    private static TrustLevel container;
    private static TrustLevel build;
    private static TrustLevel manage;

    @BeforeAll
    @DisplayName("Levels come from the real default trust levels")
    public static void setUp() {
        all = YamlConfigurations.read(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                TrustLevels.class).getTrustLevels();
        // Settings default trust_tags.max_level: "container"
        final int ceiling = all.stream().filter(l -> l.getId().equals("container"))
                .findFirst().orElseThrow().getWeight();
        cands = PublicLadder.candidates(all, ceiling);
        access = byId("access");
        container = byId("container");
        build = byId("build");
        manage = byId("manage");
    }

    private static TrustLevel byId(@org.jetbrains.annotations.NotNull String id) {
        return all.stream().filter(l -> l.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Defaults: candidates are [access, container]; build/manage are LOCKED")
    public void defaultsGiveAccessContainerCandidatesAndLockBuildManage() {
        assertEquals(List.of("access", "container"), cands.stream().map(TrustLevel::getId).toList());
        assertEquals(PublicLadder.State.LOCKED, PublicLadder.state(build, cands, Optional.empty()));
        assertEquals(PublicLadder.State.LOCKED, PublicLadder.state(manage, cands, Optional.empty()));
    }

    @Test
    @DisplayName("A click always flips the clicked tile")
    public void clickAlwaysFlipsTheClickedTile() {
        final List<Optional<TrustLevel>> currents = new ArrayList<>();
        currents.add(Optional.empty());
        cands.forEach(c -> currents.add(Optional.of(c)));
        for (final TrustLevel level : all) {
            for (final Optional<TrustLevel> cur : currents) {
                final PublicLadder.State before = PublicLadder.state(level, cands, cur);
                final PublicLadder.Action a = PublicLadder.click(level, cands, cur);
                final PublicLadder.State after =
                        PublicLadder.state(level, cands, PublicLadder.after(a, cur));
                switch (before) {
                    case LOCKED -> assertEquals(PublicLadder.Kind.NONE, a.kind(),
                            () -> level.getId() + " from " + describe(cur));
                    case OFF -> assertEquals(PublicLadder.State.ON, after,
                            () -> level.getId() + " from " + describe(cur));
                    case ON, INCLUDED -> assertEquals(PublicLadder.State.OFF, after,
                            () -> level.getId() + " from " + describe(cur));
                }
            }
        }
    }

    @Test
    @DisplayName("An alias-less level is never a candidate and never chosen by click (P6)")
    public void aliaslessLevelIsNeverACandidate() {
        // The REAL default build level, stripped of its command aliases: previously
        // getFirst() on its empty alias list would throw NoSuchElementException.
        final TrustLevel aliaslessBuild = TrustLevel.builder()
                .id("builder")
                .displayName("Builder")
                .weight(250)
                .flags(List.of())  // default build flags: privileges none, but aliases empty
                .privileges(List.of())
                .build();
        final List<TrustLevel> withCustom = new ArrayList<>(all);
        withCustom.add(aliaslessBuild);
        final List<TrustLevel> customCands = PublicLadder.candidates(withCustom, container.getWeight());
        assertFalse(customCands.stream().anyMatch(c -> c.getId().equals("builder")),
                "alias-less level must not be a candidate");
        for (final Optional<TrustLevel> cur : Arrays.asList(Optional.<TrustLevel>empty(), Optional.of(access))) {
            assertEquals(PublicLadder.Kind.NONE, PublicLadder.click(aliaslessBuild, customCands, cur).kind(),
                    "click on an alias-less level is a no-op, never a crash");
            assertEquals(PublicLadder.State.LOCKED, PublicLadder.state(aliaslessBuild, customCands, cur));
        }
    }

    @Test
    @DisplayName("Clicking INCLUDED Access while Container is public revokes all (ladder behaviour)")
    public void clickingIncludedAccessWhenContainerPublicRevokesAll() {
        assertEquals(PublicLadder.State.INCLUDED, PublicLadder.state(access, cands, Optional.of(container)));
        final PublicLadder.Action a = PublicLadder.click(access, cands, Optional.of(container));
        assertEquals(PublicLadder.Kind.REVOKE, a.kind());
        assertEquals(Optional.empty(), PublicLadder.after(a, Optional.of(container)));
    }

    private static String describe(@org.jetbrains.annotations.NotNull Optional<TrustLevel> cur) {
        return cur.map(TrustLevel::getId).orElse("(none)");
    }
}
