# Updraft Anti-Cheat

A packet-level Minecraft anti-cheat plugin for **Paper 1.21.x** (Java 21) with Spigot fallback.
Built on [PacketEvents](https://github.com/retrooper/packetevents) for packet-level detection,
[HikariCP](https://github.com/brettwooldridge/HikariCP) for storage (SQLite/MySQL),
[LuckPerms](https://luckperms.net/) for permission/track integration, and Discord webhooks for alerts.

## Features

- **27 checks** across 5 categories (combat, movement, world, player, client)
- **Command logger & flagger** — persists executed commands and flags exploit/admin-command usage
- **Packet-level detection** via PacketEvents 2.x
- **Config-driven** — every check's thresholds, VL, decay, and action tiers live in `checks.yml`
- **Per-check action tiers** (CANCEL / WARN / ALERT / KICK / BAN_COMMAND / LP_DEMOTE)
- **Storage** — SQLite (zero-config) or MySQL with async batched writes
- **Staff alerts** — rolling bossbar + hotbar (action bar), per-player toggle, color-coded by severity
- **Discord webhook** — async HTTP POST with rate-limiting and per-check cooldowns
- **LuckPerms integration** — track demotion on high VL (soft-depend)
- **Client mod detection** — channel-signature registry driven by `checks.yml`
- **Exemption system** — permission-based + automatic context (teleport, damage, flight, ping, TPS)
- **API events** — `PlayerViolationEvent` / `PlayerPunishEvent` for third-party integration

## Requirements

| Dependency | Required? | Notes |
|---|---|---|
| Paper 1.21.x / Spigot | **Required** | Java 21 |
| PacketEvents 2.x | **Required** | Install the Spigot build of PacketEvents |
| LuckPerms 5 | Optional | Enables track demotion |
| MySQL | Optional | Otherwise SQLite is used automatically |

## Installation

1. Download or build the plugin (see [Building](#building)).
2. Install [PacketEvents](https://modrinth.com/plugin/packetevents) on your server.
3. Drop `UpdraftAntiCheat-1.0.0.jar` into your `plugins/` folder.
4. Start the server — the config files are generated in `plugins/UpdraftAC/`.
5. (Optional) Edit `config.yml` to enable Discord webhooks or switch to MySQL.

## Building

```bash
./gradlew shadowJar
```

The shaded jar is output to `build/libs/UpdraftAntiCheat-1.0.0.jar`.

## Commands

All commands are under `/updraft` (alias `/ac`, `/anticheat`).

| Command | Permission | Description |
|---|---|---|
| `/updraft help` | `updraft.command.updraft` | Show help |
| `/updraft reload` | `updraft.command.reload` | Reload all config files |
| `/updraft alerts` | `updraft.alerts` | Toggle staff alerts for yourself |
| `/updraft checks` | `updraft.command.checks` | List all registered checks |
| `/updraft info <player>` | `updraft.command.info` | Show live player info |
| `/updraft violations <player>` | `updraft.command.violations` | Show active VLs |
| `/updraft logs <player>` | `updraft.command.logs` | Show recent DB-stored logs |
| `/updraft commands <player>` | `updraft.command.commands` | Show recent executed commands |
| `/updraft exempt <player> <check>` | `updraft.command.exempt` | Exemption guidance |

## Permissions

```
updraft.*                       # Everything (default: op)
updraft.alerts                  # Receive alerts
updraft.command.*               # All commands
updraft.command.commands        # View players' executed commands
updraft.exempt.<check>          # Exempt from a specific check
updraft.exempt.*                # Exempt from all checks
updraft.bypass.<check>          # Bypass punishment for a check (VL still counted)
updraft.bypass.*                # Bypass all punishments
```

## Configuration

Every config file is documented inline. The key files:

- **`config.yml`** — General settings: storage type, webhook URL, TPS floor, alert colors, cooldowns, command logger.
- **`checks.yml`** — Per-check `enabled`, `max-vl`, `decay`, `cancel-vl`, `action-tiers`, the **mod signature registry**, and flagged commands.
- **`punishments.yml`** — Kick/ban message templates, console commands, LuckPerms track config.
- **`messages.yml`** — All alerts, prefixes, and command responses (color-coded).

### Adding a check

Each check is one Java class extending `Check` plus one YAML entry:

```java
public final class MyCheck extends Check {
    public MyCheck(UpdraftAC plugin) { super(plugin, CheckType.MY_CHECK); }

    @Override
    public void onPacketReceive(PacketReceiveEvent event, PlayerData data) {
        // detection logic...
        fail(data, "detail message");
    }
}
```

Register it in `CheckManager.registerDefaults()`, add an enum entry to `CheckType`, and add a block
in `checks.yml`. Done.

### Adding a mod to detect

Add an entry under the `mods:` list in `checks.yml` — no code changes needed.
Each entry can use any combination of five signals (an entry matches on ANY of them):

```yaml
mods:
  - name: "MyCheat"
    channels:         [ "MYCHEAT_CHANNEL" ]  # plugin-message channels seen/registered
    brand-contains:   [ "mycheat" ]           # substrings in the client brand
    mod-ids:          [ "mycheat" ]           # ids parsed from the Forge/NeoForge FML handshake
    payload-contains: [ "mycheat" ]           # substrings in any plugin-message payload
    client-types:     [ FORGE ]               # VANILLA/FORGE/NEOFORGE/FABRIC/QUILT/LITELOADER
    actions: [ ALERT ]                        # advisory; real tiers come from client.mods
```

## Checks included

| Category | Checks |
|---|---|
| **Combat** | KillAura, Reach, Criticals, AutoClicker, Velocity |
| **Movement** | Fly, Speed, NoFall, Jesus, Step, Spider, Timer, Glide |
| **World** | FastBreak, FastPlace, Nuker, Scaffold, Tower, ReachBlock |
| **Player** | FastEat, FastBow, InventoryMove, Blink, Aim |
| **Client** | ClientBrand, ClientMods, Command |

## Command logger & flagger

Every command a player executes is stored in the `up_commands` table (SQLite/MySQL)
unless it matches `command-logger.ignored` in `config.yml`. Staff can review them
with `/updraft commands <player>`.

The flagger runs the command through the normal violation pipeline when it matches
a prefix in `client.command.flagged-commands` in `checks.yml` (defaults target
exploit/backdoor probes and admin commands like `/dupe`, `/op`, `/give`). It
respects `updraft.exempt.command` / `updraft.bypass.command` and the configured
action tiers (ALERT → KICK).

## API for third-party plugins

```java
// Cancel a violation (e.g. whitelist a player)
@EventHandler
public void onViolation(PlayerViolationEvent event) {
    if (event.player().getUniqueId().equals(myTrustedUuid)) {
        event.setCancelled(true);
    }
}

// Prevent a punishment
@EventHandler
public void onPunish(PlayerPunishEvent event) {
    event.setCancelled(true);
}
```

## Project layout

```
src/main/java/com/updraft/anticheat/
├── UpdraftAC.java              main plugin class
├── api/                        public API + Bukkit events
├── bukkit/listeners/           join/quit/brand-channel/world injection
├── config/                     config/messages/checks/punishments loaders
├── data/                       PlayerData + PlayerDataManager
│   └── storage/                DatabaseManager (Hikari), schema, DAOs
├── checks/
│   ├── api/                    Check, CheckContext, Category, CheckType
│   ├── manager/                CheckManager, ViolationManager
│   ├── combat/ movement/ world/ player/ client/
├── net/                        PacketEvents listener
├── punishment/                 PunishmentManager, Action, ActionExecutor
│   └── lp/                     LuckPermsManager
├── alert/                      AlertManager, BossBar, Hotbar renderers
├── webhook/                    DiscordWebhook
├── command/                    /updraft command + tab completion
├── performance/                LagManager (TPS, ping)
└── util/                       Math, Block, Chat, Permission helpers
```

## License

Provided as-is for your server. Modify and redistribute freely.
