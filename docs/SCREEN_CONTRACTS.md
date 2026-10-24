# Screen contracts (what Antigravity may restyle)

Each screen is a pure composable in `ui/screens/`. Keep the parameter types; change everything visual.

| Screen | Composable | State in | Actions out |
|---|---|---|---|
| Lock | `LockScreen(onUnlock)` | - | unlock |
| Today | `TodayScreen(TodayState, TodayActions)` | message, current avatar prompt (question + option buttons), water/meal counts, last mood, song suggestion, safety notes | start migraine, water, food, mood, sleep, answer prompt |
| Attack mode | `AttackScreen(AttackState, AttackActions)` | support message, tap timeline, emergency text | pain level (7), head region (10), undo, medicine, something-worse, dismiss emergency |
| My body | `BodyScreen(List<TimelineItem>)` | time-ordered items | - |
| Patterns | `PatternsScreen(PatternResult, stories, issueStories)` | patterns with hits/total/confidence, Story Cards | - |
| Story card | `StoryCardView(StoryCard)` | steps, mayMean, tryNext, doctorNote, evidence/science/sources (hidden behind "See the evidence") | - |
| Me | `MeScreen(MeState, MeActions)` | palettes, avatar preview, toggles, permission rows, knowledge info, status line | many (see `MeActions`) |
| Dialogs | `ui/components/Dialogs.kt` | - | choice via lambda |

Hard design rules (non-negotiable):
- Attack mode is dark and quiet: big buttons, almost no text, no bright white, no red except the emergency card.
- Plain sentence first, science one tap deeper ("See the evidence").
- Every colour comes from the active `Palette` (`theme/Palette.kt`). Never hard-code colours in screens.
- Large tap targets (min 52dp), large text, no dense lists in attack mode.
- The walking avatar overlay is plain Android views in `companion/OverlayService.kt` (palette colours are read there too).
