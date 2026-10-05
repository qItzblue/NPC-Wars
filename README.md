# NPC-Wars

A Paper plugin for staging NPC battles: equippable player-like NPCs, a combined kit menu (EssentialsX, CMI and a
built-in fallback), `/massaction`, numbered teams and team-versus-team fights with real combat AI.

- **Server:** Paper **1.21.9 or newer** (built against 1.21.11), Java 21
- **Soft dependencies:** EssentialsX, CMI (both optional; missing plugins never cause errors)

## Build

```bash
mvn clean package          # compiles, runs the unit tests, writes target/NPC-Wars-1.0.0.jar
```

Drop the jar into `plugins/` and start the server. To build against another Paper version:
`mvn clean package -Dpaper.version=1.21.10-R0.1-SNAPSHOT` (the Mannequin entity needs 1.21.9+).

## Design choice: NPCs are Paper `Mannequin` entities

A Mannequin is a real server-side entity with a player model, skin and equipment slots, and no AI, sounds or despawn
rules. That gives player-looking NPCs without NMS or per-viewer packets (version-proof, light on the server).
Because it has no AI, the plugin supplies movement itself: each tick it sets velocity, jump flag, pose and rotation
(gravity, collisions, step-ups and water stay vanilla) and plans routes with its own A* pathfinder when the straight
line is blocked.

Bodies are spawned non-persistent; `data.yml` is the single source of truth and bodies are re-created on startup,
world load and chunk load, so there are never orphaned or duplicated NPCs.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/npc spawn [label] [skin=<player>] [team=<n>]` | `npcplugin.npc` | Spawn an NPC where you stand |
| `/npc spawnmany <count> [radius] [skin=..] [team=..]` | `npcplugin.npc` | Spawn up to 200 NPCs spread around you |
| `/npc remove <targets>` / `/npc removeall confirm` | `npcplugin.npc` | Delete NPCs |
| `/npc list [page]`, `/npc info <id\|look>`, `/npc status` | `npcplugin.use` | Inspect NPCs and the fight state |
| `/npc tp <id>`, `/npc tphere <targets>` | `npcplugin.npc` | Teleport to / bring NPCs |
| `/npc select [add] <targets>`, `select clear`, `select list` | `npcplugin.npc` | Select NPCs (used by `/kitall`) |
| `/npc skin <targets> <player\|reset>`, `/npc rename <id> <label>`, `/npc heal <targets>` | `npcplugin.npc` | Style and heal NPCs |
| `/npc equip <id\|look>` or **Shift + Right-click** an NPC | `npcplugin.equip` | Equipment GUI (helmet, chest, legs, boots, main hand, off hand) |
| `/npc team add <team> <npc\|player>...` | `npcplugin.team` | Add NPCs or players to a team (created on demand) |
| `/npc team massadd <team>` | `npcplugin.team` | Put **every** NPC into the team |
| `/npc team remove <npc\|player>...`, `list`, `info <team>`, `rename <team> <name\|clear>`, `delete <team>` | `npcplugin.team` | Manage teams |
| `/npc fight` | `npcplugin.fight` | Start the fight now |
| `/npc timefight <time>` | `npcplugin.fight` | Start after `30s`, `5m`, `1h`, `1h30m`, ... (countdown is announced) |
| `/npc stopfight` | `npcplugin.fight` | End the fight or cancel a scheduled one |
| `/npc kit create <name> [icon]`, `delete`, `list`, `providers` | `npcplugin.kit` | Built-in kits; see all kit sources |
| `/kitall [all\|selected\|team <n>\|npc <id>]` | `npcplugin.kit` | Menu of every kit from every provider; click to apply |
| `/massaction <action> [args] [for=<time>] [team=<n>] [npc=<ids>]` | `npcplugin.massaction` | Every NPC does the same thing |
| `/npc deps [install [citizens\|essentialsx\|all]]` | `npcplugin.deps` | Show / download the optional plugins |
| `/npc import citizens [all\|<ids>]` | `npcplugin.import` | Copy player NPCs from Citizens into NPC-Wars |
| `/npc life <targets> <on\|off>`, `life status` | `npcplugin.npc` | Life mode: NPCs live like SMP players (see below) |
| `/npc randomize <targets> [names\|skins\|both]` | `npcplugin.npc` | New random names / skins from the pools |
| `/npc pool <names\|skins> <list\|add\|remove> [value]` | `npcplugin.npc` | Edit `pools.yml` |
| `/npc path create\|add\|remove\|clear\|delete\|list\|info <name> ...` | `npcplugin.route` | Build waypoint routes |
| `/npc path run <name> [targets] [speed=walk\|run] [spread=<n>] [fight=now\|<time>]` | `npcplugin.route` | Send NPCs along a route; optionally start the fight when the last one arrives |
| `/npc path stop [name]` | `npcplugin.route` | Stop NPCs walking routes |
| `/npc say <id> <text>` | `npcplugin.npc` | Make an NPC say something in chat |
| `/npc reload`, `/npc save` | `npcplugin.reload` | Reload config / write data now |
| `/npc debug <id>` | `npcplugin.debug` | Movement and combat state of one NPC |

`<targets>` accepts an id (`5`), a list (`1,2,7`), a range (`3-9`), `all`, `selected`, `look` (the NPC you look at) or
`team:<n>`. `npcplugin.admin` (default: op) grants everything. Every command has tab completion and error messages.

### Mass actions

`attack` (alias `hit`), `swing`, `move`, `walk`, `run`, `swim`, `jump`, `sneak`, `unsneak`, `look`, `spin`, `follow`,
`stop`. Examples:

```
/massaction jump for=10s              every NPC jumps repeatedly for 10 seconds
/massaction walk north 20 team=2      team 2 walks 20 blocks north
/massaction move me                   everyone paths to you
/massaction attack 5                  swing and hit five times
/massaction sneak on npc=1-10
```

New actions are one small class: implement `NpcAction`, call `plugin.actions().register(...)`. Tab completion and
`/massaction list` pick it up automatically.

## Teams and fights

- Teams are positive numbers, created the first time they are used. Players and NPCs can share a team; a member is in
  one team at a time (adding it elsewhere moves it).
- The optional team name (`/npc team rename`) is **admin-only**: it is shown only in `team list/info` output and never
  as a nametag, in chat, on a scoreboard or in announcements (the winner message says "Team 3").
- During a fight an NPC attacks members of other teams, players without a team, and (by default) NPCs without a team.
  It never attacks its own team. Unteamed NPCs are each their own side (`fight.unteamed-npcs: IDLE` makes them passive).
- Combat AI: nearest-enemy search through a spatial grid (staggered across NPCs), sticky target with switching when a
  clearly closer enemy appears or the target dies, retaliation, pathfinding, sprinting when far, weapon-based attack
  cooldown, damage from the held weapon and enchantments (Sharpness, Knockback, Fire Aspect), crits when falling,
  knockback, and vanilla armor/protection on the victim.
- A fight ends with `/npc stopfight` or automatically when one side is left. On death an NPC stays down until the fight
  ends (`fight.on-death`: `RESPAWN_ON_FIGHT_END`, `RESPAWN_DELAY` or `REMOVE`).
- NPCs outside of fights are invulnerable (`npc.invulnerable-when-idle`). NPCs only act while their chunks are loaded.

## Kits

`/kitall` lists kits from every available provider, each labelled with its source:

- **EssentialsX** - read from Essentials' `kits.yml`. Kit command lines are skipped and no cooldown is touched.
- **CMI** - read through reflection (CMI has no public API jar); best effort, see Limitations.
- **Built-in** - `plugins/NPC-Wars/kits.yml`; create from your own armor/hotbar with `/npc kit create <name>`.

Armor goes to the matching slot, the strongest weapon to the main hand, a shield to the off hand. To add another kit
plugin, implement `KitProvider` and call `plugin.kits().register(...)`.

## Auto-download and Citizens

- **Auto-download** (`auto-download` in config.yml, on by default): at startup, a missing **Citizens** and **EssentialsX**
  (the kit plugin) are downloaded into `plugins/`. They load on the **next restart**; jars are never hot-loaded.
  Citizens is **off** by default in `auto-download.plugins` (its newest build may not support your Minecraft version and it takes over `/npc`; use `/npcwars` then). Citizens has no API key; it is fetched from its public build server (`ci.citizensnpcs.co`). EssentialsX comes from
  Modrinth and its SHA-512 is verified. Only those hosts are accepted (also after redirects), the file must be a jar whose
  plugin.yml names the right plugin, and nothing already installed is replaced. Turn it off with
  `auto-download.enabled: false`; `/npc deps install` still downloads on demand. CMI is paid and is never downloaded.
- **Citizens import:** `/npc import citizens [all|<ids>]` creates NPC-Wars NPCs from Citizens player NPCs (position,
  name, skin, armor and hands). The Citizens NPCs are left alone; re-running skips NPCs already imported.

## An SMP with NPCs: life mode, names, routes

- **Life mode** (`/npc life all on`, or `life.default-for-new-npcs: true`): while nothing else controls an NPC (no fight,
  mass action or route) it wanders near its home, looks at nearby players, and fidgets (jump, sneak, swing). In life mode
  NPCs take damage like survival players (`life.vulnerable`), respawn at home, and never fight on their own, so they can
  live in survival with no fighting at all. Fights, mass actions and routes take over and hand control back afterwards.
- **Random and settable names and skins:** `/npc spawn` and `spawnmany` give every NPC a random name and skin from
  `pools.yml` (`appearance.random-on-spawn`); give your own with `/npc spawn <name> skin=<player>`, change them with
  `/npc rename <id> <name>` and `/npc skin <ids> <player>`, or re-roll with `/npc randomize all`. Edit the pools by hand or
  with `/npc pool`. Skins are Minecraft account names (the server downloads them). Names are shown above NPCs
  (`npc.show-nametag`); team names never are.
- **Routes:** `/npc path create arena`, stand at each point and `/npc path add arena` (or `add arena <x> <y> <z>` from the
  console), then `/npc path run arena fight=10s`. NPCs fan out around each waypoint, walk the sequence with the pathfinder,
  and when the last one has arrived the staff is told and the fight starts (`fight=now` or after a delay). Without `fight=`
  the NPCs just stop at the end and you start the fight yourself with `/npc fight` or `/npc timefight`. An NPC that cannot
  reach a waypoint within `routes.waypoint-timeout-seconds` skips it, so the sequence always completes.
- **Fighting feel:** most swings connect (`fight.hit-chance`), some miss, NPCs sometimes pause before the next swing
  (`fight.hesitate-*`), the arm swings on every attempt, and an empty-handed NPC picks up `fight.default-weapon` when a
  fight starts so it visibly holds something.

## Performance notes (100+ NPCs)

One tick loop drives everything on the main thread. Idle NPCs on dry land cost nothing per tick. Target searches use a
spatial grid and are staggered; path searches share a per-tick time budget (`pathfinding.budget-micros-per-tick`) with
cached terrain lookups and never load chunks. The only asynchronous work is writing `data.yml`, from a string built on
the main thread, so the Bukkit API is never touched off-thread.

## Limitations (please read)

The pure logic (pathfinder, teams, damage math, pacing, life planner, routes, downloader, config
consistency) has automated tests. Spawning, life mode, routes with an automatic fight, fights between teams, the
auto-download and the AI chat call (against a local fake API) were also run on a real Paper 1.21.11 server, driven from
the console. Not verified, because it needs a client or real accounts: how everything *looks* (held items, hand swings,
skins), the GUIs, and combat balance with real players.
Everything that affects feel is configurable (`movement.*`, `fight.*`, `life.*`, `pathfinding.*`), and `/npc debug <id>`
prints an NPC's movement/combat state to help tune it. NPCs only act while their chunks are loaded (a player nearby, or a
force-loaded chunk).

- The CMI hook is reflection-based and unverified against a real CMI jar.
- `/npc` is also Citizens' command name; if both are installed use the alias `/npcwars` (or `/npc-wars:npc`).
- Mannequins cannot climb ladders or open doors; closed doors and fences count as walls for pathfinding.
- Players using Creative mode can have inventory-GUI quirks; use Survival to equip NPCs by hand or use kits.

## Project layout

```
src/main/java/com/npcwars/
  NpcWarsPlugin            lifecycle, tick loop, wiring
  config/                  Settings, Messages (MiniMessage), DataStore (async-safe data.yml)
  npc/                     Npc, NpcManager, NpcSlot, NpcSelection;  npc/control/NpcController (movement)
  path/                    Terrain, PathFinder (A*), PathService (time-budgeted), BukkitTerrain
  team/                    Team, TeamManager (pure Java), TeamStorage
  appearance/              Pools (pools.yml), NamePicker
  life/                    LifeAi (peaceful SMP behaviour), LifePlanner
  route/                   Route, RouteManager, RouteStorage, RouteRunner
  dependency/              DependencyInstaller, Citizens / EssentialsX download sources, checked downloader
  integration/             CitizensImporter (the only class that uses the Citizens API)
  kit/                     KitProvider, KitRegistry, Built-in / Essentials / CMI providers, ItemParser, KitApplier
  action/                  NpcAction, ActionRegistry, ActionRunner;  action/builtin/ (attack, move, walk, ...)
  combat/                  FightManager, TargetSelector, SpatialGrid, AttackExecutor, DamageCalculator, Factions
  gui/                     EquipmentGui, KitMenu, GuiListener
  command/                 /npc (sub-command router), /kitall, /massaction
  listener/                interaction, damage rules, lifecycle (death, chunk and world load)
```
