# TalesLoader → Create-Integration (Plan)

Ziel: TalesLoader sieht und fühlt sich an wie ein Create-Block. GUI auf Creates eigener
GUI-Library, dazu Goggles-Overlay, Wrench-Support und eine Ponder-Szene.

Getroffene Entscheidungen:

- **Harte Create-Abhängigkeit** (Create 6.0.11 für MC 1.21.1 / NeoForge 21.1)
- **Fenster als Brass-Frame Nine-Slice** (`AllGuiTextures.BRASS_FRAME_*`, keine eigene Textur)
- **Features:** Goggles-Overlay, Ponder-Szene, Wrench

## Verifizierte Grundlagen

Maven `https://maven.createmod.net` (am 2026-09-04 geprüft):

| Artefakt | Version |
| --- | --- |
| `com.simibubi.create:create-1.21.1` | `6.0.11-300` |
| Catnip | kein eigenes Artefakt — steckt gebündelt in `ponder-neoforge` |
| `net.createmod.ponder:ponder-neoforge` | `[1.0.85+mc1.21.1,)` (transitiv) |
| `dev.engine-room.flywheel:flywheel-neoforge-1.21.1` | `1.0.6` (transitiv) |
| `com.tterrag.registrate:Registrate` | `MC1.21-1.3.0+67` (transitiv, Maven `mvn.devos.one/snapshots`) |

Verwendbare Klassen (aus den Sources-Jars bestätigt):

- Catnip `net.createmod.catnip.gui`: `AbstractSimiScreen`, `NavigatableSimiScreen`,
  `ConfirmationScreen`, `ScreenOpener`, `UIRenderHelper`,
  `element.BoxElement`, `element.GuiGameElement`, `widget.BoxWidget`, `widget.ElementWidget`
- Create `com.simibubi.create.foundation.gui`: `menu.AbstractSimiContainerScreen`,
  `AllGuiTextures`, `AllIcons`, `widget.IconButton`, `widget.ScrollInput`,
  `widget.SelectionScrollInput`, `widget.Indicator`, `widget.Label`, `widget.TooltipArea`
- Create stabile API: `com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation`,
  `com.simibubi.create.content.equipment.wrench.IWrenchable`
- Ponder: `PonderPlugin` + `PonderIndex.addPlugin(...)` (siehe `CreatePonderPlugin`)

Referenz-Implementierungen zum Abschauen:

- `SchematicannonScreen` – Fuel-Gauge + Slots + Spielerinventar + IconButtons (fast 1:1 unser Layout)
- `ValueSettingsScreen#renderBrassFrame` – Nine-Slice-Fenster beliebiger Größe (~10 Zeilen)
- `StationScreen` – IconButton-Reihe, Label, Textfeld im Create-Look

Status: Phasen 0–5 sind umgesetzt (Build grün), Phasen 6 (Ponder) und 7 (Feinschliff) offen.

## Phase 0 – Build-Setup ✅

- `gradle.properties`: `create_version=6.0.11-300`
- `build.gradle`: Repos `maven.createmod.net`, `maven.tterrag.com`;
  `implementation "com.simibubi.create:create-1.21.1:${create_version}"`
- `src/main/templates/META-INF/neoforge.mods.toml`: `[[dependencies.talesloader]]` auf `create`,
  `type="required"`, `versionRange="[6.0,)"`, `ordering="AFTER"`
- Gegencheck: `./gradlew build` und Client-Run starten, bevor Code umgestellt wird.

Abweichung: Registrate `MC1.21-1.3.0+67` liegt **nicht** auf `maven.tterrag.com` (dort endet 1.21
komplett), sondern auf `https://mvn.devos.one/snapshots`. Dieses Repo ist auf die Gruppe
`com.tterrag.registrate` eingegrenzt.

## Phase 1 – GUI-Fundament ✅

Neu: `client/CreateStyle.java` (ersetzt `GuiStyle`)

- `window(graphics, x, y, w, h)` – Brass-Frame Nine-Slice (Portierung aus `ValueSettingsScreen`)
- `slot(...)` – Create-Slot statt selbstgemaltem Bevel
- `gauge(...)` – Fuel-Balken im Create-Farbschema (Kupfer/Brass-Gradient statt `0xFFD8AF35`)
- `GuiStyle` bleibt vorerst als Fallback, wird am Ende gelöscht.

`client/ChunkLoaderScreen.java`

- `extends AbstractSimiContainerScreen<ChunkLoaderMenu>` statt `AbstractContainerScreen`
- `setWindowSize(...)` + `getLeftOfCentered(...)`; Spielerinventar über
  `renderPlayerInventory(graphics, x, y)` mit `AllGuiTextures.PLAYER_INVENTORY`
  → `ChunkLoaderMenu.INVENTORY_X/Y`, `HOTBAR_Y` an Creates Inventar-Textur (176 px) angleichen
- Buttons `Map` / `Trusted` → `IconButton` mit `AllIcons` (z. B. `I_VIEW_SCHEMATIC`, `I_CONFIG_OPEN`)
  plus Tooltip; kein Vanilla-`Button` mehr
- Aktiv-Zustand als `Indicator` (grüne LED) neben dem Fuel-Gauge
- Restzeit als `Label` im Create-Font-Stil; `TooltipArea` für Gauge und Fuel-Slot
  ersetzt die manuelle Hitbox-Rechnerei in `renderFuelTooltip`
- `GuiGameElement.of(ModBlocks.CHUNK_LOADER)` als 3D-Blockvorschau im Header

Gemessen an `create:textures/gui/player_inventory.png` (176x108): die Slot-Rahmen sitzen bei x=7/y=17,
die Item-Flächen also bei **(8, 18)** relativ zur Texturecke, Hotbar bei y=76. `ChunkLoaderMenu`
rechnet damit; Create selbst ist an der Stelle stellenweise 1 px inkonsistent (LinkedController).

## Phase 2 – Chunk-Grid im Create-Look ✅

- 3×3-Zellen (`CELL_SIZE = 32`) in eine versenkte Brass-Fläche setzen; Zellrahmen aus
  `BRASS_FRAME_*` statt `renderOutline`
- Tints an Creates Palette angleichen (Grün/Amber/Rot mit Gradient via `UIRenderHelper`)
- Hover-Highlight über `UIRenderHelper.breath(...)`-Pulsieren statt statischem Weiß-Overlay
- Klick-Sound auf Creates UI-Sounds umstellen (`playUiSound`, `AllSoundEvents`)
- Mittelzelle (Loader) mit `GuiGameElement` als Miniblock markiert

## Phase 3 – Neben-Screens ✅

- `ChunkMapScreen` → `NavigatableSimiScreen` mit `ScreenOpener.transitionTo(...)`,
  damit der Wechsel Loader ↔ Karte die Create-Übergangsanimation nutzt
- `TrustedScreen` → `AbstractSimiScreen`; Spielerzeilen als `BoxWidget` mit Kopf-Icon;
  Entfernen eines Spielers über Catnips `ConfirmationScreen`
- Zoomstufen der Karte über `ScrollInput` / `SelectionScrollInput`

Abweichungen: die Trusted-Zeilen sind ein dunkles `CreateStyle.field` plus `IconButton(I_TRASH)`
statt `BoxWidget` — BoxWidget ist für dunkle Ponder-Overlays gedacht und beißt sich mit dem hellen
Container-Panel. Der Kartenzoom läuft über das Mausrad direkt auf der Karte (4 Stufen: 8/12/16/24 px
pro Chunk) statt über einen `ScrollInput`; die Minimap-Textur bleibt dabei gleich, nur der Blit
skaliert.

## Phase 4 – Goggles-Overlay ✅

- `ChunkLoaderBlockEntity implements IHaveGoggleInformation`
- `addToGoggleTooltip(...)`: Restlaufzeit (`TimeFormat`), aktive Chunks `n/9`,
  Verbrauchsrate, bei Sneak zusätzlich Owner + Trusted-Liste
- Rot eingefärbte Zeile unter 1 Minute Restlaufzeit (passt zu `BAR_FILL_LOW`)
- Sichtbarkeit respektiert die vorhandene `canUse`-Prüfung

Nötige Zusatzarbeit: die Block-Entity wurde vorher gar nicht zum Client synchronisiert (die GUI
lief allein über `ContainerData`). Für das Overlay gibt es jetzt `getUpdateTag` +
`getUpdatePacket`, einen Push alle 40 Ticks **solange Brennstoff verbraucht wird**, und einen
sofortigen Push bei Besitzer-, Trusted- und Auswahländerungen.

## Phase 5 – Wrench ✅

- `ChunkLoaderBlock implements IWrenchable`
- `onSneakWrenched`: Abbau in Item-Form, aber nur wenn `canBreak(player)` → bestehende
  Owner-/Admin-Prüfung bleibt maßgeblich; sonst `InteractionResult.FAIL` + Meldung
- `onWrenched`: Blockrotation (Facing) ohne Chunk-Tickets zu verlieren
- Beides über `LoaderTickets`/`releaseEverything` sauber abräumen

## Phase 6 – Ponder

- `client/ponder/TalesLoaderPonderPlugin implements PonderPlugin`, Registrierung in `ClientSetup`
  via `PonderIndex.addPlugin(...)`
- Szene 1 „chunk_loader": Platzieren → GUI öffnen → Chunks auswählen → Fuel einwerfen
- Szene 2 „fuel": Verbrauch skaliert mit Anzahl aktiver Chunks
- Ponder-Tag `talesloader:chunk_loading`; Texte nach `en_us.json` + `de_de.json`

## Phase 7 – Feinschliff

- Item-Tooltip im Create-Stil (`item.talesloader.chunk_loader.tooltip.*`-Keys)
- Creative-Tab-Icon und `de_de.json`/`en_us.json` vollständig nachziehen
- `GuiStyle` entfernen, wenn nichts mehr darauf zeigt

## Risiken

- `foundation.gui.*` ist Create-**intern**, keine API-Garantie → Version pinnen und bei
  Create-Updates gegenprüfen. `api.equipment.goggles` und `IWrenchable` sind stabiler.
- Harte Abhängigkeit: Server und Client brauchen Create + Catnip + Ponder + Flywheel + Registrate.
- Creates `PLAYER_INVENTORY` ist 176 px breit; unser Fenster ist 220 px → Inventar zentriert
  setzen (`getLeftOfCentered`), Slot-Koordinaten in `ChunkLoaderMenu` müssen mitwandern.
- Ponder-Szenen brauchen Struktur-NBT (`.nbt` unter `data/talesloader/ponder/`) — separater
  Bau-/Exportschritt im Spiel.

## Reihenfolge

Phase 0 → 1 → 2 sind der Kern (sichtbarer Create-Look). 4 und 5 sind klein und unabhängig.
3 und 6 danach. Nach jeder Phase Client-Run zur Sichtprüfung.
