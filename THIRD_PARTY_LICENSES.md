# Third-Party Licenses

HuskClaimsPlus builds on and redistributes the following third-party software.
Licences were verified 2026-10-18/19 against the artifacts in the local Gradle
cache (POM `<licenses>` blocks or LICENSE files inside the jars) and, where the
jar carries no licence text, against the project's source repository.

| Component | Version(s) in use | License | How it ships |
|---|---|---|---|
| HuskClaims (upstream, William278) | 1.5.11 (pin 51e80fd) | Apache-2.0 (repo `LICENSE`) | Source base of this fork |
| ClopLib fork (fabric nodes; William278's ClopLib) | 2.0.13.1+26.2, 2.0.13.1+26.3 | Apache-2.0 | Built from the HuskClaimsPlus ClopLib fork, nested jar-in-jar only, not published to any public repository |
| Fabric API | 0.154.0+26.2, 0.161.0+26.3 | Apache-2.0 (FabricMC) | Declared dependency (user installs; not bundled) |
| adventure-platform-fabric (KyoriPowered) | 7.1.0+26.2, 7.2.0+26.3 | MIT (POM) | Fabric: nested |
| fabric-permissions-api (lucko) | 0.7.0 | MIT (POM) | Fabric: nested |
| placeholder-api (eu.pb4) | 3.1.0-beta.1+26.2, 3.2.0+26.3 | LGPL-3.0 (`LICENSE_placeholder-api` in jar) | Fabric: nested, unmodified |
| Impactor API economy (NickImpact) | 5.3.5 | MIT (github.com/NickImpact/Impactor `LICENSE.txt`) | Fabric: nested |
| Toilet (William278) | toilet-fabric 1.1.2+26.2, toilet-bukkit 1.0.15 | Apache-2.0 (`LICENSE` in jar) | Fabric: nested; Paper: relocated |
| configlib (de.exlll) | configlib-core 4.6.1 (Fabric), configlib-yaml 4.6.1 (Bukkit) | MIT (POM) | Fabric: nested; Paper: relocated |
| snakeyaml-engine (org.snakeyaml) | 2.10 | Apache-2.0 | Fabric: nested; Paper: relocated |
| sqlite-jdbc (org.xerial) | 3.49.1.0 | Apache-2.0 | Fabric: nested |
| MySQL Connector/J (com.mysql) | 9.3.0 | GPL-2.0-with-FOSS-Exception | Fabric: nested |
| MariaDB Connector/J (org.mariadb.jdbc) | 3.5.3 | LGPL-2.1 | Fabric: nested |
| MongoDB driver (org.mongodb) | 5.1.1 | Apache-2.0 | Fabric: nested |
| Jedis (redis.clients) | 6.0.0 | MIT | Fabric: nested |
| redis-authx-core (redis.clients) | 0.1.1-beta2 | MIT (Maven Central POM; source github.com/redis/redis-authx-core) | Fabric: nested |
| commons-pool2 (org.apache.commons) | 2.13.0 | Apache-2.0 | Fabric: nested |
| DesertWell (William278) | 2.0.4 | Apache-2.0 (William278 libs) | Paper: relocated; Fabric: via toilet/common |
| bStats (org.bstats) | bstats-bukkit 3.1.0 | MIT | Paper: relocated; metrics disabled (id 0 — no bStats id is registered for the fork) |
| MineDown (de.themoep), vendored | 1.8.2 (vendored under `common/src/main/java/de/themoep/minedown/adventure`) | MIT | Inlined + relocated |
| FabricPluginMessaging (PokeSkies), vendored | f621c4fcf6 | MPL-2.0 (github.com/PokeSkies/FabricPluginMessaging `LICENSE`) | Inlined (26.2 and 26.3 nodes); MPL header and attribution kept; source available in the PokeSkies repo |
| sgui (eu.pb4) | 2.1.0+26.2 / 2.2.1+26.3 (pins in fabric/26.x/gradle.properties) | LGPL-3.0 (verified 2026-10-18: `LICENSE_sgui` inside the nested jar) | Fabric: nested, unmodified, separate jar-in-jar |

## Rules honored by this list

- LGPL components (placeholder-api, MariaDB driver, sgui) ship
  as **separate, unmodified, replaceable jars** (Fabric jar-in-jar satisfies
  this). Never shade or relocate them into our classes.
- MPL-2.0 (FabricPluginMessaging) is file-level copyleft: the vendored files
  (and any changes to them) stay MPL-2.0 with their notice, and their source
  remains available.
- MySQL Connector/J ships under the GPL-2.0 Universal FOSS Exception; we
  bundle it unmodified, exactly as upstream HuskClaims does.
- Paper-side relocations (shadow `relocate` in `bukkit/build.gradle` /
  `paper/build.gradle`) are declaration-only notices here; the jar contents
  match the build files.
- ClopLib comes from the HuskClaimsPlus fork of William278's ClopLib; it is
  nested jar-in-jar only and never published under `net.william278`
  coordinates.
