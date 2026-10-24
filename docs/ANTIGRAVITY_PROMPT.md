# Prompt for Antigravity (paste this, then attach the project)

You are polishing the UI of **Kanraeh**, an offline Android app (Kotlin + Jetpack Compose) for women with PCOS who have migraines.
The logic is finished and must not change. Read `README.md`, `docs/ARCHITECTURE.md` and `docs/SCREEN_CONTRACTS.md` first.

## You may edit
- `app/src/main/java/com/kanraeh/app/ui/screens/*`
- `app/src/main/java/com/kanraeh/app/ui/components/*`
- `app/src/main/java/com/kanraeh/app/theme/Palette.kt` (colours, shapes, typography only; keep the `Palette` data class and the six palette ids)
- `MainActivity.kt` > `Tabs()` layout only (bottom bar styling)
## You must NOT edit
core, pain, cycle, food, meds, screentime, songs, patterns, knowledge, stories, safety, privacy, report, `MainViewModel`, `Routes.kt`, or any screen/state/action parameter types. If you need new data on a screen, add it to its State class and tell me instead of wiring it yourself.

## Who it is for
People in pain, light-sensitive, often unable to read, emotionally worn down. Every interaction should feel calm, kind and effortless.

## Design brief
- Calm, comforting, stylish. Soft rounded shapes, generous spacing, gentle motion only. No heavy or saturated colours, no pure white or pure black.
- Six built-in palettes (already defined: Dusk Lavender, Sage Calm, Warm Sand, Ocean Mist, Rose Quartz, Midnight Teal) plus a custom palette. Keep all six and make each look polished.
- **Attack mode** (`AttackScreen`) is always dark, nearly text-free, with very large icon-led buttons: 7 pain levels with faces/icons, a simple head outline for where it hurts, and "Took medicine", "Something is different", "Undo". Nothing flashy.
- **Today**: the avatar's question card is the hero; one giant "I have a migraine" button always in the same place; quick log row (water, food, mood, sleep) with icons.
- **Patterns / Story Cards**: a story reads like a gentle timeline (what happened, what it may mean, what to try). Science is hidden behind "See the evidence".
- Replace emoji placeholders with a consistent icon style if you wish (add icons without new network dependencies).
- Accessibility: min 52dp targets, scalable text, contrast that is soft but readable, content descriptions.
- The app must stay fully offline. Do not add any network library or analytics.

## Deliverable
A buildable project (Android Studio, `Run` works) with only the allowed files changed, plus a short list of what you changed.
