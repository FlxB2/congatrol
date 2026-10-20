# AGENTS.md

Orientation for AI agents working in this repo. Pair with `README.md`.

## What this is

**Congatrol** is a phone app for robot vacuums running Congatudo or Valetudo: the map and the
usual controls, spoken straight to the robot's own REST API over the home network. It is small on
purpose — a remote control, not a home automation hub.

This repository is public. Nothing from anybody's own network belongs in it: no addresses, host
names, MAC addresses or real maps. The robot's address has **no default**; the invented robot
(`data/DemoRobotApi.kt`) is what previews, tests and screenshots use.

## Layout

```
congatrol/
├── shared/       # Kotlin Multiplatform: all logic and all UI (Compose Multiplatform)
├── androidApp/   # Android entry point and the home screen widget
├── iosApp/       # Xcode project, consumes :shared as a static framework
├── bin/          # Repo scripts (import-icons)
├── maestro/      # End-to-end flows
├── licenses/     # Licence texts of the fonts, icons and libraries
└── Justfile      # The canonical way to run anything — check it before inventing a command
```

- **Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin (DI), navigation-compose, Haze,
  Ktor client (CIO) and kotlinx-serialization's JSON trees. No database.
- **Package root:** `xyz.felixb.congatrol`.
- **Targets:** Android, iOS (arm64 device + arm64 simulator), and a `desktop` JVM target that exists
  only for Compose Hot Reload (`just hot`).

## Talking to the robot

- `data/RobotApi.kt` moves JSON to and from `/api/v2` on the robot and knows nothing of what it
  means. `HttpRobotApi` is the real thing; `DemoRobotApi` is an invented flat with three rooms.
- `data/RobotRepository.kt` is the only door to the robot. `connection` is a shared flow that asks
  the robot every two seconds **while somebody collects it** and stops five seconds after the last
  screen looked away; the map, the big answer, is only re-read while the robot moves. `details`
  (consumables, volume, version) is asked every twenty seconds. Commands are `suspend` functions
  returning `Result<Unit>`; each one asks for a fresh reading when it is done.
- The robot is also listened to: `RobotApi.events` reads its event streams
  (`robot/state/attributes/sse`, `robot/state/map/sse`) and whatever arrives first, a push or an
  answer, is shown. Polling stays, because Congatudo holds its pushes back to one a second and was
  measured to push later than it answers. A command only returns once the robot has carried it
  out — a stop took fifteen seconds on a Conga — hence the long timeout on `put`.
- The app shows what the robot reports and nothing else: no guessing ahead at the state a command
  will lead to.
- Parsing is pure and tested: `parseAttributes` (`data/RobotState.kt`) and `parseMap`
  (`data/RobotMap.kt`). The JSON is read as trees, without the serialization compiler plugin,
  because robots differ in what they send and a missing field must not be an error.
- The app shows only what the robot says it can do (`RobotSnapshot.capabilities`, Valetudo's
  capability names).
- `SettingsStore` keeps the two things the app remembers — the address and the name the user gave
  the robot — and has one implementation per platform, bound in that platform's Koin entry point.
  The name never leaves the device.

## The map

`RobotMap` is in **cells**: the map's own pixels, x right and y down. The robot's units are turned
into cells on the way in and back (`cellSize`) on the way out. `data/MapViewport.kt` is the one
place that goes between cells and the screen; `screens/map/RobotMapView.kt` draws the layers as
points on a `Canvas` and turns touches into cells. What a touch means is the `MapMode`.

Some robots send only part of a room's cells, so `RobotMap.roomAt` falls back to the room's
outline.

The pencil on the map is `MapMode.Edit`: one room picked can be renamed or split
(`MapMode.Split`, where a drag draws the cut), two can be joined. These go to the robot
(`MapSegmentEditCapability`, `MapSegmentRenameCapability`), which redraws its map; the repository
re-reads the map for a few polls afterwards. The invented robot really splits, joins and renames,
and drives a round through the flat while it "cleans".

The map is a drawing, so its semantics carry a description listing the rooms — which is also what
the end-to-end flows read.

## Screens

Three tabs — Map, Drive, Robot — in `screens/`. Every screen follows the same pattern, and it is
not optional:

- `XxxScreen` gets the ViewModel from Koin and collects its state; `XxxScreenContent` is stateless
  and takes the state and callbacks; a `@PreviewCongatrol` preview renders the content with sample
  state.
- A ViewModel exposes one `StateFlow<XxxUiState>`, a sealed interface with `Loading`, derived with
  `combine`/`stateIn(WhileSubscribed)` — never set by hand.
- While there is no robot, every tab shows `screens/common/Disconnected.kt`.
- Strings live in `composeResources/values/strings.xml`. A literal percent sign is written `%`,
  not `%%`.

The round button beside the tab bar is `ActionViewModel`: start, pause or resume, from anywhere.

## Look

`theme/Theme.kt` is the design system and the only place that decides how things look: one accent
and a three-colour glow, light and dark following the system. Screens have no background of their
own; they stand on `CongatrolBackground`. Cards are `components/CongatrolCard.kt`. Headings are
Bricolage Grotesque, everything else Manrope. The tab bar (`components/FloatingTabBar.kt`) floats
over the screens, which are each handed a `bottomInset` to keep free below their content.

Motion lives in `components/Motion.kt`: the two springs everything uses, and `pressScale`,
`appear` (staggered entrance) and `halo`. On the map the robot's marker is moved between readings
rather than jumped (`RobotMapView`). Reach for these before writing a new animation spec.

Icons are a subset of [Phosphor](https://phosphoricons.com) checked in as vector drawables; add
one to the list in `bin/import-icons` and run it.

## Licences

Robot › Open source licences (`screens/licenses/`) lists every component the build ships, each
with its licence text. The list is not written by hand: the AboutLibraries Gradle plugin reads the
dependencies' POMs and `just licenses` exports one report per platform into
`composeResources/files/licenses/` (`android.json`, `ios.json`), which are checked in and read by
`data/LicenseRepository.kt`. **Run `just licenses` after adding, removing or bumping a dependency.**
What no dependency declares — the two fonts and the Phosphor icons — has a hand-written entry under
`shared/licenses/`; their texts are also in the top-level `licenses/` for whoever reads the repo.

## The widget

The widgets are Jetpack Glance, in `androidApp/.../widget/`. `RobotWidget.kt` has a receiver and
a `GlanceAppWidget` per look (Clear, Ring, Floorplan; one class each, as Glance updates widgets by
class) and the actions; `WidgetContent.kt` draws each `WidgetLook` from the size it is given.
Every look shows the name, the status, the battery, a start button and buttons for the rooms the
robot has a name for (`RobotMap.namedRooms`), each cleaning its room and drawn in that room's
colour on the map (`seriesColor`). They ask the robot once per update (`RobotRepository.readState`
and `readMap`) — every half hour, when the app is left, and after a button — and never poll.
Their words come from the shared strings via `shared/src/androidMain/.../widget/WidgetSnapshot.kt`.

A widget cannot ask the theme, so the colours are copies of `theme/Theme.kt`'s. Shapes are white
drawables tinted where they are used; a tint drops alpha, so tint colours are opaque, and it fills
a stroke-only shape in, so Clear's outlines take their colour from `res/values*/colors.xml`
instead. The Ring's battery ring is a bitmap wedge, since Glance has no determinate circle.

## End-to-end flows

`maestro/` holds the Maestro flows, run with `just e2e android` (or `ios`) against an emulator;
`maestro/config.yaml` lists the directories that run. Every flow starts with
`common/launch_demo.yaml`, which wipes the app's data and launches it on the invented robot.
Elements are found by `Modifier.testTag`. Two things to know when writing one:

- A tag on a button is not on its label: ask for the id and the text separately.
- The floating bar lies over the bottom of every screen and takes the taps there, and a drag over
  the map moves the map. Scroll what is tapped clear of the bar first
  (`common/reveal_controls.yaml`, or `scrollUntilVisible` with `centerElement`).

## Working here

- `just test`, `just lint` and `just format` before calling something done.
- `just install android demo` or `just hot` to look at a change without a robot.
- Do not send a real robot cleaning or driving commands from a test.
