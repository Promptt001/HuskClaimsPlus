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

package net.william278.huskclaims.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Scans the bukkit GUI sources for
 * locale key usage and pins GUI/source invariants that are easier to verify by
 * source scan than by unit test.
 */
@DisplayName("GUI Locale Coverage (source scan)")
public class GuiLocaleCoverageTests {

    private static final Path BUKKIT_GUI_DIR = Paths.get(
            "..", "bukkit", "src", "main", "java", "net", "william278", "huskclaims", "gui");
    // The five platform-free classes (GuiText, PickTarget, ClaimNames,
    // ClaimRef, GuiDiagnostics) live in common/gui; scans cover both dirs.
    private static final Path COMMON_GUI_DIR = Paths.get(
            "src", "main", "java", "net", "william278", "huskclaims", "gui");
    private static final Path LOCALES_DIR = Paths.get("src", "main", "resources", "locales");

    private static final Map<String, Map<String, String>> ALL_LOCALES = new HashMap<>();

    static void loadFiles() throws IOException {
        if (ALL_LOCALES.isEmpty()) {
            assumeTrue(Files.isDirectory(BUKKIT_GUI_DIR),
                    "bukkit GUI sources not present — skipping (expected outside repo)");
            assumeTrue(Files.isDirectory(LOCALES_DIR), "locale resources not present — skipping");
            ALL_LOCALES.put("en-gb", loadLocale("en-gb"));
            try (Stream<Path> files = Files.list(LOCALES_DIR)) {
                for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".yml"))
                        .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                        .toList()) {
                    ALL_LOCALES.put(baseName(p), loadLocale(p));
                }
            }
        }
    }

    private static String baseName(Path p) {
        final String name = p.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static Map<String, String> loadLocale(Path file) throws IOException {
        final Map<String, String> map = new HashMap<>();
        final Pattern kv = Pattern.compile("^\\s*([A-Za-z0-9_]+):\\s*(.*)$");
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            final Matcher m = kv.matcher(line);
            if (m.matches()) {
                map.put(m.group(1), m.group(2));
            }
        }
        return map;
    }

    private static Map<String, String> loadLocale(String name) throws IOException {
        return loadLocale(LOCALES_DIR.resolve(name + ".yml"));
    }

    private static List<Path> guiSources() throws IOException {
        final List<Path> all = new ArrayList<>();
        for (Path dir : new Path[]{BUKKIT_GUI_DIR, COMMON_GUI_DIR}) {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.walk(dir)) {
                    s.filter(f -> f.getFileName().toString().endsWith(".java")).forEach(all::add);
                }
            }
        }
        return all;
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("No 1-arg hasPermission(\"huskclaims.…\") in gui/ - every GUI permission node must declare a default")
    public void testNoDefaultlessGuiPermissionChecks() throws IOException {
        // A 1-arg hasPermission on an unregistered node is OP-only by accident in
        // Bukkit; every GUI permission check must pass an explicit default.
        // Also flag literal nodes that skip the registered command. prefix
        // (e.g. huskclaims.claimwarp.other — the real node lives under
        // huskclaims.command.*; ask the command instead of spelling a literal).
        // String concatenation like ("huskclaims.gui." + x) is fine as long as the
        // 2-arg overload with a default is used.
        final Pattern oneArg = Pattern.compile(
                "hasPermission\\s*\\(\\s*\"huskclaims\\.(?!command\\b)[^\"]*\"\\s*\\)");
        final List<String> offenders = new ArrayList<>();
        for (Path src : guiSources()) {
            final Matcher m = oneArg.matcher(read(src));
            while (m.find()) {
                offenders.add(src.getFileName() + ": " + m.group().trim());
            }
        }
        assertTrue(offenders.isEmpty(),
                "1-arg (defaultless) hasPermission on huskclaims.* literal nodes found ("
                        + "they are OP-only by accident or point at an unregistered node): "
                        + String.join("; ", offenders));
    }

    @Test
    @DisplayName("Trust level picker only offers levels that have a command alias")
    public void testPickerAliasFilterPinned() throws IOException {
        final String picker = read(COMMON_GUI_DIR.resolve("menu/TrustLevelPicker.java"));
        assertTrue(picker.contains("getCommandAliases().isEmpty()"),
                "TrustLevelPicker must filter out alias-less levels (P6)");
    }

    @Test
    @DisplayName("Claim tool handouts are permission-gated and their cooldown survives reconnects")
    public void testClaimToolHandoutSafeguardsPinned() throws IOException {
        final String delivery = read(BUKKIT_GUI_DIR.resolve("ClaimToolDelivery.java"));
        assertFalse(delivery.contains("forget("),
                "Handout cooldowns must survive a quit (players could reconnect to reset them)");
        assertTrue(delivery.contains("PERMISSION"),
                "Claim tool handouts must be permission-gated (huskclaims.gui.claim_tool)");
        final String manager = read(BUKKIT_GUI_DIR.resolve("BukkitClaimGuiManager.java"));
        assertTrue(manager.contains("PrepareItemCraftEvent") && manager.contains("markAsHandout("),
                "Combining two handout tools in a crafting grid must not launder the handout marker");
    }

    @Test
    @DisplayName("The public trust ladder has one definition (TrustMenu reuses PublicLadder.candidates)")
    public void testPublicLadderSingleSourceOfTruthPinned() throws IOException {
        final String trustMenu = read(COMMON_GUI_DIR.resolve("menu/TrustMenu.java"));
        final String ppm = read(COMMON_GUI_DIR.resolve("menu/PublicPermissionsMenu.java"));
        assertTrue(trustMenu.contains("PublicLadder.candidates("),
                "TrustMenu.candidateLevels must delegate to PublicLadder.candidates so the alias filter cannot diverge");
        // The flag action re-runs every check, so a confirm screen that stays open cannot skip them
        assertTrue(ppm.contains("mayToggleFlag(") && ppm.indexOf("private void setFlag(") > ppm.indexOf("boolean mayToggleFlag("),
                "PublicPermissionsMenu.setFlag must start with a mayToggleFlag check");
        // A confirm screen passes the state the player asked for, so it can't flip a change made elsewhere
        assertTrue(ppm.contains("setFlag(u, op, true)"),
                "The confirm callback must enable the action, not flip whatever the state is");
    }

    @Test
    @DisplayName("Claim references never resolve into another world")
    public void testClaimRefOwnWorldOnlyPinned() throws IOException {
        final String claimRef = read(COMMON_GUI_DIR.resolve("ClaimRef.java"));
        assertFalse(claimRef.contains("elsewhere") || claimRef.contains("!k.equals(worldName)"),
                "ClaimRef.resolve must look in the reference's own world only: a same-corner claim in "
                        + "another world is a different claim, and acting on it would change the wrong land");
    }

    @Test
    @DisplayName("Player-chosen text never enters an item name unneutralised")
    public void testNoRawPlayerTextInItemNames() throws IOException {
        for (Path src : guiSources()) {
            final String code = read(src);
            assertFalse(code.contains("nameRaw(\"\u00a7f\" +"),
                    src.getFileName() + " builds an item name from player-chosen text without "
                            + "neutralising section signs (use GuiItem.nameLiteral)");
        }
    }

    @Test
    @DisplayName("No raw-locale item text in bukkit gui sources")
    public void testNoRawItemText() throws IOException {
        // Item names/lore must be built via GuiItem (translate + hex handling), not
        // raw locale strings; only GuiItem itself is allowed to set them.
        final List<String> offenders = new ArrayList<>();
        for (Path src : guiSources()) {
            final String code = read(src);
            if (src.getFileName().toString().equals("GuiItem.java")) {
                continue;
            }
            if (code.contains("getRawLocale(") && code.contains("setDisplayName(")) {
                offenders.add(src.getFileName() + " sets item names outside GuiItem");
            }
            if (code.contains("getRawLocale(") && code.contains("setLore(")) {
                offenders.add(src.getFileName() + " sets lore outside GuiItem");
            }
        }
        assertTrue(offenders.isEmpty(), String.join("; ", offenders));
    }

    @Test
    @DisplayName("Menus never ask a claim world for its name through ClaimWorld.getName")
    public void testNoEqualsBasedWorldNameLookups() throws IOException {
        // Claim worlds are matched by identity, never by re-deriving a world from
        // its name, because names can be ambiguous across servers.
        final Pattern worldNameLookup = Pattern.compile(
                "\\.getName\\(\\s*(plugin|manager\\.getPlugin\\(\\)|getPlugin\\(\\))\\s*\\)");
        final List<String> offenders = new ArrayList<>();
        for (Path src : guiSources()) {
            if (worldNameLookup.matcher(read(src)).find()) {
                offenders.add(src.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "equals()-based world-name lookups found in gui sources: " + String.join("; ", offenders));
    }

    @Test
    @DisplayName("Every literal gui_* key referenced in bukkit GUI sources exists in en-gb")
    public void testLiteralKeysPresent() throws IOException {
        loadFiles();
        final Map<String, String> enGb = ALL_LOCALES.get("en-gb");
        final Pattern keyRef = Pattern.compile("\"(gui_[a-z0-9_]+)\"");
        final List<String> missing = new ArrayList<>();
        for (Path src : guiSources()) {
            final String code = read(src);
            for (Matcher m = keyRef.matcher(code); m.find(); ) {
                final String key = m.group(1);
                // Prefixes built by concatenation are covered by testDynamicFamilies
                if (key.endsWith("_")) {
                    continue;
                }
                // Not a locale key: the NamespacedKey of the GUI tool item
                if (key.equals("gui_item")) {
                    continue;
                }
                if (!enGb.containsKey(key)) {
                    missing.add(key + "  (in " + src.getFileName() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing from en-gb:\n" + String.join("\n", missing));
    }

    @Test
    @DisplayName("Every enum Type constant has a gui_<x>_title key unless the class overrides getTitle()")
    public void testMenuTitleKeys() throws IOException {
        loadFiles();
        final Map<String, String> enGb = ALL_LOCALES.get("en-gb");
        final Pattern enumDecl = Pattern.compile(
                "enum\\s+Type\\s+implements\\s+ClaimGuiMenu\\.Type\\s*\\{([^}]*)}");
        final Pattern constant = Pattern.compile("^\\s*([A-Z][A-Z0-9_]*)\\s*[,(]");
        final List<String> missing = new ArrayList<>();
        for (Path src : guiSources()) {
            final String source = read(src);
            if (!source.contains("enum Type implements ClaimGuiMenu.Type")) {
                continue;
            }
            final boolean overridesTitle = Pattern.compile("getTitle\\s*\\(").matcher(source).find();
            if (overridesTitle) {
                continue;
            }
            final Matcher typeDecl = enumDecl.matcher(source);
            if (!typeDecl.find()) {
                continue;
            }
            for (Matcher c = constant.matcher(typeDecl.group(1)); c.find(); ) {
                final String titleKey = "gui_" + c.group(1).toLowerCase(Locale.ROOT) + "_title";
                if (!enGb.containsKey(titleKey)) {
                    missing.add(titleKey + "  (enum in " + src.getFileName() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing title keys:\n" + String.join("\n", missing));
    }

    @Test
    @DisplayName("Dynamic families expand from real defaults (sort, resize, trust level, flags)")
    public void testDynamicFamilies() throws IOException {
        loadFiles();
        final Map<String, String> enGb = ALL_LOCALES.get("en-gb");
        final List<String> missing = new ArrayList<>();
        for (String sort : List.of("world_name", "name", "size", "newest")) {
            if (!enGb.containsKey("gui_claim_list_sort_" + sort)) {
                missing.add("missing gui_claim_list_sort_" + sort);
            }
        }
        if (!enGb.containsKey("gui_trust_confirm_grant")) {
            missing.add("generic grant confirm key must exist (P6 replacement)");
        }
        for (String dir : List.of("north", "south", "west", "east", "all")) {
            if (!enGb.containsKey("gui_resize_direction_" + dir)) {
                missing.add("missing gui_resize_direction_" + dir);
            }
            if (!enGb.containsKey("gui_resize_direction_name_" + dir)) {
                missing.add("missing gui_resize_direction_name_" + dir);
            }
        }
        for (String flag : List.of("player_damage_monster", "player_damage_entity", "player_damage_player",
                "block_interact", "redstone_interact", "container_open", "entity_interact",
                "farm_block_interact", "farm_block_break", "farm_block_place", "ender_pearl_teleport",
                "place_vehicle", "break_vehicle")) {
            if (!enGb.containsKey("gui_flag_" + flag)) {
                missing.add("missing gui_flag_" + flag);
            }
            if (!enGb.containsKey("gui_flag_" + flag + "_desc")) {
                missing.add("missing gui_flag_" + flag + "_desc");
            }
        }
        // Retired per-id keys must not linger (P6: generic keys replaced them)
        for (Map.Entry<String, String> e : enGb.entrySet()) {
            if (e.getKey().startsWith("gui_action_trust_level_") && !e.getKey().equals("gui_action_trust_level_")) {
                if (e.getKey().endsWith("_lore") || !e.getKey().contains("_lore")) {
                    missing.add("retired per-id key still present: " + e.getKey());
                }
            }
        }
        assertTrue(missing.isEmpty(), String.join("; ", missing));
    }

    @Test
    @DisplayName("Every gui_* key in en-gb is present in all 7 locale files (parity)")
    public void testSevenLocaleParity() throws IOException {
        loadFiles();
        assertEquals(7, ALL_LOCALES.size(), "expected exactly 7 locale files");
        final Set<String> enKeys = new TreeSet<>();
        ALL_LOCALES.get("en-gb").keySet().stream().filter(k -> k.startsWith("gui_")).forEach(enKeys::add);
        for (Map.Entry<String, Map<String, String>> e : ALL_LOCALES.entrySet()) {
            final Set<String> other = e.getValue().keySet();
            assertTrue(other.containsAll(enKeys),
                    () -> e.getKey() + " gui key set differs from en-gb");
            assertTrue(e.getValue().containsKey("gui_locale_version"),
                    () -> e.getKey() + " lacks gui_locale_version (K1 refresh would never fire)");
        }
    }

    @Test
    @DisplayName("Every icon_materials default key is referenced as an icon(\"…\") literal in gui/")
    public void testEveryIconDefaultKeyIsReferenced() throws IOException {
        assumeTrue(Files.isDirectory(BUKKIT_GUI_DIR), "bukkit GUI sources not present — skipping");
        // Parse the default iconMaterials map out of Settings.java itself
        final Path settings = Paths.get("src", "main", "java", "net", "william278",
                "huskclaims", "config", "Settings.java");
        assumeTrue(Files.isRegularFile(settings), "Settings.java not present — skipping");
        final Pattern entry = Pattern.compile("Map\\.entry\\(\"([^\"]+)\",");
        final List<String> defaultKeys = new ArrayList<>();
        for (Matcher m = entry.matcher(read(settings)); m.find(); ) {
            defaultKeys.add(m.group(1));
        }
        assertFalse(defaultKeys.isEmpty(), "could not find iconMaterials defaults in Settings.java");
        final StringBuilder guiSources = new StringBuilder();
        for (Path src : guiSources()) {
            guiSources.append(read(src)).append('\n');
        }
        final List<String> unreferenced = new ArrayList<>();
        for (String key : defaultKeys) {
            // trust.level.<id> keys are referenced by concatenation: icon("trust.level." + id)
            final String prefix = key.substring(0, key.lastIndexOf('.') + 1);
            // Menus read configured icons through either call form:
            // manager.icon("key", ...) in the legacy manager/menus, or
            // controller.item("key", ...) in the shared model menus.
            final boolean referenced = guiSources.indexOf("icon(\"" + key + "\"") >= 0
                    || guiSources.indexOf("icon(\"" + prefix) >= 0
                    || guiSources.indexOf("item(\"" + key + "\",") >= 0
                    || guiSources.indexOf("item(\"" + prefix) >= 0;
            if (!referenced) {
                unreferenced.add(key);
            }
        }
        assertTrue(unreferenced.isEmpty(),
                "icon_materials default keys never read by the GUI (dead config): "
                        + String.join(", ", unreferenced));
    }

    @Test
    @DisplayName("No non-exempt gui_* item key value contains MineDown syntax ](")
    public void testNoMineDownInItemKeys() throws IOException {
        loadFiles();
        final List<String> exempt = List.of("gui_prompt_", "gui_input_", "gui_claim_tool_",
                "gui_error_", "gui_public_perm_enabled", "gui_public_perm_disabled",
                "gui_public_warp_enabled", "gui_public_warp_disabled", "gui_claim_renamed",
                "gui_claim_gone", "gui_claim_renamed_warp_kept", "gui_claim_warp_not_inside",
                "gui_trustee_unknown", "gui_trustee_name_changed");
        final List<String> offenders = new ArrayList<>();
        for (Map.Entry<String, String> e : ALL_LOCALES.get("en-gb").entrySet()) {
            final String key = e.getKey();
            final String value = e.getValue().replace("\\'", "'");
            if (!key.startsWith("gui_")) {
                continue;
            }
            if (exempt.stream().anyMatch(key::startsWith)) {
                continue;
            }
            if (value.contains("](")) {
                offenders.add(key + " = " + value);
            }
        }
        assertTrue(offenders.isEmpty(),
                "MineDown syntax in item key values:\n" + String.join("\n", offenders));
    }


    @Test
    @DisplayName("The claim warp switch in Public Permissions is an icon and status pair, not a loose button")
    public void testWarpToggleIsAPair() throws IOException {
        assumeTrue(Files.isDirectory(COMMON_GUI_DIR), "common GUI sources not present — skipping");
        final String ppm = read(COMMON_GUI_DIR.resolve("menu/PublicPermissionsMenu.java"));
        assertFalse(ppm.contains("WARP_SLOT"), "the warp switch must not have a fixed slot of its own");
        assertTrue(ppm.contains("WarpVisibility"), "the warp switch is laid out as one more pair");
    }

    @Test
    @DisplayName("The Public Claims browser checks the visit command's permission before every teleport")
    public void testPublicClaimsChecksVisitPermission() throws IOException {
        assumeTrue(Files.isDirectory(COMMON_GUI_DIR), "common GUI sources not present — skipping");
        final String controller = read(COMMON_GUI_DIR.resolve("GuiController.java"));
        final String pcm = read(COMMON_GUI_DIR.resolve("menu/PublicClaimsMenu.java"));
        assertTrue(controller.contains("mayUse(user, \"visits\")")
                        && controller.contains("mayUse(user, \"visit\")"),
                "openPublicClaimsMenu must check both warp commands' permissions");
        assertTrue(controller.indexOf("void openPublicClaimsMenu")
                        < controller.indexOf("mayUse(user, \"visits\")"),
                "the check must sit inside openPublicClaimsMenu");
        final int gate = pcm.indexOf("mayUse(");
        final int visitCall = pcm.indexOf("ClaimWarpVisits.visit(");
        assertTrue(gate >= 0 && visitCall >= 0 && gate < visitCall,
                "PublicClaimsMenu must re-check the visit permission before teleporting");
    }

    @Test
    @DisplayName("Player-chosen names passed into item names and lore are always neutralised")
    public void testNoUnneutralisedPlayerTextInItems() throws IOException {
        assumeTrue(Files.isDirectory(BUKKIT_GUI_DIR), "bukkit GUI sources not present — skipping");
        // A player-chosen warp or claim name can contain a section sign; passing it
        // straight into item text would let that name inject colour codes
        final Pattern raw = Pattern.compile(
                "(getWarp\\(\\)\\.getName\\(\\)|warp\\.getName\\(\\)|claimName\\(\\)|displayName\\()"
                        + "[^;\\n]{0,80}?(\\.name|\\.lore)\\(");
        final List<String> offenders = new ArrayList<>();
        for (Path src : guiSources()) {
            for (String line : read(src).split("\n", -1)) {
                final Matcher m = raw.matcher(line);
                if (m.find() && !line.contains("literal(")) {
                    offenders.add(src.getFileName() + ": " + line.strip());
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Warp or claim names passed to .name(/.lore( without GuiText.literal:\n"
                        + String.join("\n", offenders));
    }

}
