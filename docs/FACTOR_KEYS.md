# Factor keys (the contract between patterns, knowledge and stories)

`patterns` detects these in the 24 hours before an attack (or other issue). `knowledge.json` entries attach explanations to them via `"factor"`.

| Key | Meaning | How it's detected |
|---|---|---|
| skipped_meal | She tapped "I skipped it" | MEAL event key=skipped |
| long_food_gap | >5 h since last logged meal | only if she logs meals regularly |
| short_sleep | Slept under 6 h | SLEEP event value<6 |
| low_water | <3 glasses in the 12 h before | only if she logs water regularly |
| sweet_food, caffeine, salty_food, fried_food, refined_carb, tyramine_food, chocolate, alcohol | Food tags | from `assets/foods.json` tags |
| mood_low, irritable, anxious | Mood logged | MOOD events |
| craving_sweet, craving_salt | Craving logged | CRAVING events |
| high_screen | Day total >= max(240 min, 1.3x her average) | SCREEN_DAY events |
| late_screen | >=30 min of use between 23:00 and 05:00 | SCREEN_DAY events |
| perimenstrual | Within 2 days before to 3 days after a logged period start | PERIOD_START events |

Adding a new factor: add the key in `core/Types.kt` (`FactorKeys`), detect it in `patterns/Patterns.kt > FactorExtractor`, then add `knowledge.json` entries for it.
