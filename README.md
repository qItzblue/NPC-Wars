# NPC-Wars

A Paper plugin for staging NPC battles and running an SMP full of NPCs: real player NPCs (bodies made by Citizens), kits
(EssentialsX, CMI and a built-in fallback), `/massaction`, numbered teams, waypoint routes, a dupe stick that turns players
into NPCs, and fights where the NPCs really use their items (wind charge + mace slams, shields, pearls, bows, ...).

- **Server:** Paper **1.21.11**, Java 21
- **Needs Citizens 2.0.43 build 4250.** NPC-Wars downloads exactly that build into `plugins/` on first start if it is
  missing (then restart once). Citizens' newest build refuses to run on 1.21.11, so a newer one is never fetched.
- **Soft dependencies:** EssentialsX, CMI (optional)
- **The command is `/npcwars`** (alias `/nw`). `/npc` belongs to Citizens.
- **Quiet by default:** the plugin prints nothing to chat (no "NPC spawned", no "team 2 wins", no countdowns) until you
  turn it on with `/npcwars debug on`. Errors, usage hints and answers to list/info/status always show. With debug on, the
  console also traces every decision of the combat AI (which tactic, what it hit, why it died).

## Build

```bash
mvn clean package          # compiles, runs the unit tests, writes target/NPC-Wars-1.0.0.jar
```

Drop the jar into `plugins/` and start the server.

## How the NPCs work

Each NPC is a real player entity created through the Citizens API (in its temporary registry, so Citizens saves nothing
itself). That gives a proper player model with skin, name tag, held items, armor, shield and swing animations. NPC-Wars
keeps everything else: `data.yml` is the single source of truth (id, name, skin, home, 41-slot inventory, team,
behavior), and bodies are re-created on startup, world load and chunk load. Movement is driven by the plugin (velocity,
jumps, rotation, its own A* pathfinder); damage rules, fights and items are the plugin's too, because a Citizens body
lacks a few things a real player gets from the game (fall distance, ender pearl teleport, shield blocking), which the
plugin fills in.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/npcwars spawn [label] [skin=<player>] [team=<n>]` | `npcplugin.npc` | Spawn an NPC where you stand |
| `/npcwars spawnmany <count> [radius] [skin=..] [team=..]` | `npcplugin.npc` | Spawn up to 200 NPCs spread around you |
| `/npcwars remove <targets>` / `/npcwars removeall confirm` | `npcplugin.npc` | Delete NPCs |
| `/npcwars list [page]`, `/npcwars info <id\|look>`, `/npcwars status` | `npcplugin.use` | Inspect NPCs and the fight state |
| `/npcwars tp <id>`, `/npcwars tphere <targets>` | `npcplugin.npc` | Teleport to / bring NPCs |
| `/npcwars select [add] <targets>`, `select clear`, `select list` | `npcplugin.npc` | Select NPCs (used by `/kitall`) |
| `/npcwars skin <targets> <player\|reset>`, `/npcwars rename <id> <label>`, `/npcwars heal <targets>` | `npcplugin.npc` | Style and heal NPCs |
| `/npcwars equip <id\|look>` or **Shift + Right-click** an NPC | `npcplugin.equip` | Equipment GUI (helmet, chest, legs, boots, main hand, off hand) |
| `/npcwars team add <team> <npc\|player>...` | `npcplugin.team` | Add NPCs or players to a team (created on demand) |
| `/npcwars team massadd <team>` | `npcplugin.team` | Put **every** NPC into the team |
| `/npcwars team remove <npc\|player>...`, `list`, `info <team>`, `rename <team> <name\|clear>`, `delete <team>` | `npcplugin.team` | Manage teams |
| `/npcwars fight` | `npcplugin.fight` | Start the fight now |
| `/npcwars timefight <time>` | `npcplugin.fight` | Start after `30s`, `5m`, `1h`, `1h30m`, ... (countdown is announced) |
| `/npcwars stopfight` | `npcplugin.fight` | End the fight or cancel a scheduled one |
| `/npcwars kit create <name> [icon]`, `delete`, `list`, `providers` | `npcplugin.kit` | Built-in kits; see all kit sources |
| `/kitall [all\|selected\|team <n>\|npc <id>]` | `npcplugin.kit` | Menu of every kit from every provider; click to apply |
| `/massaction <action> [args] [for=<time>] [team=<n>] [npc=<ids>]` | `npcplugin.massaction` | Every NPC does the same thing |
| `/npcwars deps [install [citizens\|essentialsx\|all]]` | `npcplugin.deps` | Show / download the optional plugins |
| `/npcwars import citizens [all\|<ids>]` | `npcplugin.import` | Copy player NPCs from Citizens into NPC-Wars |
| `/npcwars life <targets> <on\|off>`, `life status` | `npcplugin.npc` | Life mode: NPCs live like SMP players (see below) |
| `/npcwars randomize <targets> [names\|skins\|both]` | `npcplugin.npc` | New random names / skins from the pools |
| `/npcwars pool <names\|skins> <list\|add\|remove> [value]` | `npcplugin.npc` | Edit `pools.yml` |
| `/npcwars path create\|add\|remove\|clear\|delete\|list\|info <name> ...` | `npcplugin.route` | Build waypoint routes |
| `/npcwars path run <name> [targets] [speed=walk\|run] [spread=<n>] [fight=now\|<time>]` | `npcplugin.route` | Send NPCs along a route; optionally start the fight when the last one arrives |
| `/npcwars path stop [name]` | `npcplugin.route` | Stop NPCs walking routes |
| `/npcwars say <id> <text>` | `npcplugin.npc` | Make an NPC say something in chat |
| `/npcwars give <targets> <ITEM [amount] [enchant:level]>`, `give <targets> clear` | `npcplugin.npc` | Put items in NPC inventories |
| `/npcwars move <targets> <x> <y> <z> [world]`, `/npcwars clone <id> [count]` | `npcplugin.npc` | Teleport NPCs (works from the console); duplicate an NPC with its inventory |
| `/npcwars stick [mode copy\|fill\|single] [then stand\|walk]` | `npcplugin.stick` | The dupe stick (see below) |
| `/npcwars debug [on\|off\|<id>]` | `npcplugin.debug` | Plugin messages on/off, or one NPC's state |
| `/npcwars reload`, `/npcwars save` | `npcplugin.reload` | Reload config / write data now |

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
- The optional team name (`/npcwars team rename`) is **admin-only**: it is shown only in `team list/info` output and never
  as a nametag, in chat, on a scoreboard or in announcements (the winner message says "Team 3").
- During a fight an NPC attacks members of other teams, players without a team, and (by default) NPCs without a team.
  It never attacks its own team. Unteamed NPCs are each their own side (`fight.unteamed-npcs: IDLE` makes them passive).
- Combat AI: nearest-enemy search through a spatial grid (staggered across NPCs), sticky target with switching when a
  clearly closer enemy appears or the target dies, retaliation, pathfinding, sprinting when far, weapon-based attack
  cooldown, damage from the held weapon and enchantments (Sharpness, Knockback, Fire Aspect), crits when falling,
  knockback, and vanilla armor/protection on the victim.
- A fight ends with `/npcwars stopfight` or automatically when one side is left. On death an NPC stays down until the fight
  ends (`fight.on-death`: `RESPAWN_ON_FIGHT_END`, `RESPAWN_DELAY` or `REMOVE`).
- NPCs outside of fights are invulnerable (`npc.invulnerable-when-idle`). NPCs only act while their chunks are loaded.

## Kits

`/kitall` lists kits from every available provider, each labelled with its source:

- **EssentialsX** - read from Essentials' `kits.yml`. Kit command lines are skipped and no cooldown is touched.
- **CMI** - read through reflection (CMI has no public API jar); best effort, see Limitations.
- **Built-in** - `plugins/NPC-Wars/kits.yml`; create from your own armor/hotbar with `/npcwars kit create <name>`.

Armor goes to the matching slot, the strongest weapon to the main hand, a shield to the off hand. To add another kit
plugin, implement `KitProvider` and call `plugin.kits().register(...)`.

## Auto-download and Citizens

- **Auto-download** (`auto-download` in config.yml, on by default): at startup, a missing **Citizens** and **EssentialsX**
  (the kit plugin) are downloaded into `plugins/`. They load on the **next restart**; jars are never hot-loaded.
  Citizens is **off** by default in `auto-download.plugins` (its newest build may not support your Minecraft version and it takes over `/npcwars`; use `/npcwars` then). Citizens has no API key; it is fetched from its public build server (`ci.citizensnpcs.co`). EssentialsX comes from
  Modrinth and its SHA-512 is verified. Only those hosts are accepted (also after redirects), the file must be a jar whose
  plugin.yml names the right plugin, and nothing already installed is replaced. Turn it off with
  `auto-download.enabled: false`; `/npcwars deps install` still downloads on demand. CMI is paid and is never downloaded.
- **Citizens import:** `/npcwars import citizens [all|<ids>]` creates NPC-Wars NPCs from Citizens player NPCs (position,
  name, skin, armor and hands). The Citizens NPCs are left alone; re-running skips NPCs already imported.

## An SMP with NPCs: life mode, names, routes

- **Life mode** (`/npcwars life all on`, or `life.default-for-new-npcs: true`): while nothing else controls an NPC (no fight,
  mass action or route) it wanders near its home, looks at nearby players, and fidgets (jump, sneak, swing). In life mode
  NPCs take damage like survival players (`life.vulnerable`), respawn at home, and never fight on their own, so they can
  live in survival with no fighting at all. Fights, mass actions and routes take over and hand control back afterwards.
- **Random and settable names and skins:** `/npcwars spawn` and `spawnmany` give every NPC a random name and skin from
  `pools.yml` (`appearance.random-on-spawn`); give your own with `/npcwars spawn <name> skin=<player>`, change them with
  `/npcwars rename <id> <name>` and `/npcwars skin <ids> <player>`, or re-roll with `/npcwars randomize all`. Edit the pools by hand or
  with `/npcwars pool`. Skins are Minecraft account names (the server downloads them). Names are shown above NPCs
  (`npc.show-nametag`); team names never are.
- **Routes:** `/npcwars path create arena`, stand at each point and `/npcwars path add arena` (or `add arena <x> <y> <z>` from the
  console), then `/npcwars path run arena fight=10s`. NPCs fan out around each waypoint, walk the sequence with the pathfinder,
  and when the last one has arrived the staff is told and the fight starts (`fight=now` or after a delay). Without `fight=`
  the NPCs just stop at the end and you start the fight yourself with `/npcwars fight` or `/npcwars timefight`. An NPC that cannot
  reach a waypoint within `routes.waypoint-timeout-seconds` skips it, so the sequence always completes.
- **Fighting feel:** most swings connect (`fight.hit-chance`), some miss, NPCs sometimes pause before the next swing
  (`fight.hesitate-*`), they take a moment to react when a fight starts or they pick a new enemy
  (`fight.reaction-*-ticks`), pause after using an item (`fight.tactic-pause-*-ticks`), stop briefly while running at an
  enemy (`fight.approach-pause-*`) and stand a moment at each route waypoint (`routes.pause-*-ticks`), the arm swings on every attempt, and an empty-handed NPC picks up `fight.default-weapon` when a
  fight starts so it visibly holds something.

## Fighting with items

Fighting NPCs use what they carry, like a player would. Give items with a kit, the dupe stick, `/npcwars give`, or the
equipment GUI. Each behavior below only happens if the NPC has the item, and each can be switched off under `combat:` in
config.yml.

| Item | What the NPC does |
|---|---|
| Mace + wind charges | Throws a wind charge at its feet to launch itself, steers over the enemy, switches to the mace and smashes down. Damage grows with the height fallen (real mace formula, Density counts); no fall damage after the smash |
| Axe vs. a raised shield | Knocks the shield out for 5 seconds; the mace slam is preferred while the enemy is stunned |
| Shield | Raises it between swings; blocks hits from the front, axes disable it |
| Ender pearls | Throws one to cross a gap to a far enemy, or to escape when nearly dead (pearl damage applies) |
| Golden apples, food, potions | Eats or drinks when hurt (backing away), drinks strength/speed/resistance before a fight |
| Totem of undying | Moves it into a hand when low, and it pops as in the game |
| Bow, crossbow, trident | Draws, aims with a lead and gravity compensation, shoots (Power, Quick Charge, Infinity, Loyalty are respected) |
| Splash potions, fire charges, snowballs, eggs | Throws harming potions at the enemy, healing ones at its own feet; fire charges set the target alight but not the world |
| Cobweb | Traps the enemy's feet (removed after `placed-block-seconds`) |
| Water bucket | Places water below itself on a long fall |
| TNT, end crystals | Lights TNT or pops a crystal at the enemy's feet, then runs; explosions hurt entities but only break blocks if `explosions-break-blocks` is on |
| Lava bucket | Off by default (`lava: true` to allow); temporary like the cobweb |

Not done: elytra flying, riding, and building structures. Swings are not perfect: `fight.hit-chance`,
`fight.hesitate-*` and `fight.attack-jitter-ticks` make NPCs miss now and then and pause between swings. NPCs also take
fall damage and sometimes hop to land a critical hit.

## The dupe stick

`/npcwars stick` gives you a stick that turns players into NPCs with their skin, name, team and **whole inventory** (the
item they hold ends up in the NPC's hand). Its name and lore show its state, so it needs no chat.

- **Left-click a block** = corner 1, **right-click a block** = corner 2 (the area is outlined with particles).
- **Right-click in the air** = use it. **Sneak + left-click** = change mode. **Sneak + right-click** = what the copies do.
- **Modes:** *Copy players in the area* (every real player standing in it becomes an NPC, in place and facing the same
  way) · *Fill the area with copies of you* (a grid, `stick.fill-spacing` apart, all facing the way you face) · *Place
  one copy of you* (where you point, inside the area if one is set).
- **Then:** *Stand still* or *Walk forward* (they stop when a fight starts).

## Performance notes (100+ NPCs)

One tick loop drives everything on the main thread. Idle NPCs on dry land cost nothing per tick. Target searches use a
spatial grid and are staggered; path searches share a per-tick time budget (`pathfinding.budget-micros-per-tick`) with
cached terrain lookups and never load chunks. The only asynchronous work is writing `data.yml`, from a string built on
the main thread, so the Bukkit API is never touched off-thread.

## Limitations (please read)

Verified on a real Paper 1.21.11 server with Citizens 2.0.43 build 4250, driven from the console (no client): spawning and
persistence, life mode, routes that end in a fight, team fights, the mace + wind charge slam, shield blocking and axe
stuns, pearls, eating and totems, bows/crossbows/tridents, splash potions, fire charges, cobwebs, TNT, end crystals, the
water clutch, the dupe stick's fill/single modes and walking (called through the plugin API, as there was no player to
click), `clone`, `give`, `move`, and the quiet-by-default messages. Plus unit tests for the pure logic.

**Not verified, because it needs a client or real accounts:** how everything *looks* (held items, the swing animation,
skins, name tags), real clicks with the dupe stick and its "copy players" mode, the GUIs, hits between NPCs and real
players, and combat balance. Everything that affects feel is configurable (`movement.*`, `fight.*`, `combat.*`,
`life.*`), and `/npcwars debug on` plus `/npcwars debug <id>` show what an NPC is thinking. NPCs only act while their
chunks are loaded (a player nearby, or a force-loaded chunk).

- The CMI hook is reflection-based and unverified against a real CMI jar.
- Citizens owns `/npc`; NPC-Wars is `/npcwars` (alias `/nw`).
- EssentialsX creates a user record for each NPC body (its own behavior for player-type entities).
- NPCs cannot climb ladders or open doors; closed doors and fences count as walls for pathfinding.
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
  stick/                   StickManager (dupe stick), Area, modes and behaviors
  life/                    LifeAi (peaceful SMP behaviour), LifePlanner
  route/                   Route, RouteManager, RouteStorage, RouteRunner
  dependency/              DependencyInstaller, Citizens / EssentialsX download sources, checked downloader
  integration/             CitizensImporter (the only class that uses the Citizens API)
  kit/                     KitProvider, KitRegistry, Built-in / Essentials / CMI providers, ItemParser, KitApplier
  action/                  NpcAction, ActionRegistry, ActionRunner;  action/builtin/ (attack, move, walk, ...)
  combat/                  FightManager, TargetSelector, SpatialGrid, AttackExecutor, DamageCalculator, Ballistics, Loadout
  combat/brain/            CombatBrain and the tactics (WindMace, UseItem, Ranged, Pearl, Throw, Place, Explosive, ...)
  gui/                     EquipmentGui, KitMenu, GuiListener
  command/                 /npcwars (sub-command router), /kitall, /massaction
  listener/                interaction, damage rules, lifecycle (death, chunk and world load)
```
