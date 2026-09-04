# TalesLoader

Admin-issued chunk loader for Minecraft **1.21.1** (NeoForge), built on top of **Create 6**.

One block keeps up to **nine chunks** (a 3×3 area) force-loaded while it burns fuel. Every loader belongs to the
player who placed it, chunks are claimed exclusively, and the whole interface — GUI, goggle overlay, wrench
handling, item tooltip and ponder scenes — uses Create's UI so it fits into a Create-based modpack.

---

## Features

- **3×3 chunk selection** — the loader's own chunk is always active; the eight neighbours are toggled
  individually in the GUI. Chunks already claimed by another loader are locked.
- **Fuel driven** — consumption is `baseRate + perChunkRate × activeChunks` per server tick, so the more chunks
  are loaded, the shorter the runtime. Out of fuel the loader shuts down, releases its chunks and messages the owner.
- **Player-only fuel input** — the fuel slot rejects hoppers, droppers and pipes. Fuel goes in by hand.
- **Ownership & trust list** — only the owner (and players on the loader's access list) may open or break it.
  Operators can bypass this via config.
- **Chunk map** — a keybind, an in-GUI button and `/talesloader map` open an overview of the force-loaded chunks
  around you, colour-coded per owner and showing whether a loader is running or idle.
- **Create integration** — Create-styled screens, a goggle overlay with runtime/consumption/access info,
  wrench rotation, wrench-sneak pickup (fuel included) and two ponder scenes.
- **Admin only** — no crafting recipe. The block exists in its own creative tab and is meant to be handed out
  by staff. It is blast resistant, cannot be pushed by pistons and glows while running.
- **Localised** — English and German (`en_us`, `de_de`).

## Requirements

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.249+ (`[21,)`) |
| Create | 6.0.11 (`[6.0,)`) — required on client and server |
| Java | 21 |

## Installation

1. Install NeoForge for 1.21.1 and Create 6 on both the server and the clients.
2. Drop `talesloader-<version>.jar` into `mods/` on the server and every client.
3. Start the server once to generate `config/talesloader-server.toml`.

## Usage

1. Give a player the block: `/give <player> talesloader:chunk_loader`.
2. Place it. The chunk it stands in is force-loaded immediately once it has fuel.
3. Right-click the loader to open the interface:
   - insert fuel in the fuel slot (by hand),
   - toggle the surrounding chunks in the 3×3 grid,
   - open the **chunk map** or the **access list** from the buttons.
4. Wearing Create's **Engineer's Goggles** shows runtime, chunk count and consumption on the block;
   sneaking adds owner and trust list.
5. A **wrench** rotates the housing, sneak + wrench picks the loader up with its stored fuel intact.

### Commands

| Command | Permission | Description |
|---|---|---|
| `/talesloader map` | everyone | Opens the chunk map around the player |
| `/talesloader list` | level 2 | Lists every chunk loader in the current dimension with owner, position, chunk count, remaining runtime and state |

### Default fuels

Values are in abstract fuel units — 15 units ≈ 1 tick with a single active chunk, 1,080,000 units ≈ 1 hour.

| Item | Runtime (1 chunk) |
|---|---|
| Coal / Charcoal | 20 min |
| Block of Redstone | 2 h |
| Block of Coal | 3 h |
| Diamond | 4 h |
| Block of Diamond | 36 h |
| Nether Star | 48 h |

## Configuration

`config/talesloader-server.toml` (server config, synced to clients):

| Key | Default | Description |
|---|---|---|
| `consumption.baseRate` | `10` | Fuel units per tick, independent of chunk count |
| `consumption.perChunkRate` | `5` | Additional units per tick per active chunk (the loader's own chunk counts) |
| `consumption.maxFuel` | `51840000` | Fuel capacity — 48 h with one active chunk. Capped at 2³⁰−1 |
| `fuel.items` | see above | Accepted fuels as `namespace:path=units` |
| `gui.mapRadius` | `4` | Radius in chunks shown by the chunk map (4 → 9×9) |
| `permissions.opsBypassOwner` | `true` | Let level-2 operators use and break loaders they do not own |

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`. Development runs:

```bash
./gradlew runClient
./gradlew runServer
```

## Project layout

```
src/main/java/plugin/talesloader/
├── block/      chunk loader block + block entity (fuel burn, chunk tickets, ownership)
├── chunk/      force-loading tickets
├── client/     screens, goggle overlay, minimap, keybinds, ponder scenes
├── command/    /talesloader
├── data/       saved data index of loaded chunks per dimension
├── event/      break/interact protection
├── fuel/       config-backed fuel lookup
├── item/       block item with stored-fuel tooltip
├── menu/       container menu + fuel slot
├── net/        client/server payloads
├── registry/   blocks, items, block entities, menus, data components, creative tab
└── util/       time formatting
```

## License

All Rights Reserved. See [LICENSE](LICENSE).

Author: **JXSTanix**
