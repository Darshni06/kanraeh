# Architecture

Package root: `app/src/main/java/com/kanraeh/app/`. Every package has a `MODULE.md` and a header comment saying what it owns.

| Module (package) | Owns | Depends on |
|---|---|---|
| core | DB (SQLCipher + Room), entities, DAOs, Clock/Time, event types, mood, **factor keys**, `Graph` (wiring) | - |
| pain | Attack sessions, 7 pain levels, head regions, undo, stale-attack closing, time-at-level | core |
| cycle | Irregular-cycle summary, perimenstrual window, hair areas | core |
| food | Offline food list + tags, custom foods, favourites, meal/water logging | core |
| meds | Medicine log, painkiller-day counts (overuse), labs | core |
| screentime | Usage-access reader, daily summary logging | core |
| songs | Song memories, now-playing listener, suggestions | core |
| patterns | `FactorExtractor` + `PatternEngine` (stats on HER data) | core, food |
| knowledge | Loads the replaceable `knowledge.json` | - |
| stories | Story Cards (what happened / may mean / try next) | core, patterns, knowledge, cycle, food, pain |
| safety | Red flags, aura+estrogen note, overuse notes. Overrides everything | meds |
| companion | Settings, Brain (when to ask what), PromptHandler, MessageWriter, Worker, OverlayService, Permissions | core, cycle, screentime, songs |
| avatar | AvatarSpec, procedural walking frames, store, creation (offline + optional online) | - |
| privacy | App lock, encrypted backup | core |
| report | Doctor PDF | core, patterns, cycle, meds |
| theme | 6 palettes + custom, attack-mode dim | companion (settings) |
| ui | `MainViewModel`, `MainActivity`, `Routes`, `screens/` (pure), `components/` | everything via `Graph` |

## Rules
- Screens in `ui/screens` are PURE: state in, actions out. Only `ui/Routes.kt` and `MainViewModel` talk to modules.
- No medical text in code except `safety` (generic, flagged for review). All explanations come from `knowledge.json`.
- Pattern detection never names science; it only reports factor keys with evidence counts.
- Times are never typed: every tap is stamped by `core/Clock`.
- Only `avatar/OnlineAvatarClient` may use the network.

## Data flow
tap -> ViewModel -> module -> encrypted DB -> `observeRecent` flow -> ViewModel.refresh() ->
PatternEngine -> StoryBuilder (+KnowledgeBase) -> Story Cards on the Patterns screen.
Background: `CompanionWorker` (~15 min) -> `CompanionBrain.next()` -> `OverlayService` shows the walking avatar -> `PromptHandler.apply()`.
