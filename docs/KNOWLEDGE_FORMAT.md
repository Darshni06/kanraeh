# knowledge.json format

```json
{
  "version": "2026-11-01",
  "entries": [
    {
      "id": "kb-skipped-meal",
      "factor": "skipped_meal",
      "title": "Skipped meals",
      "whyPlain": "One or two plain sentences, no jargon.",
      "whyScience": "The mechanism in scientific words.",
      "evidence": "strong | moderate | limited",
      "sources": ["Author, Journal, Year"],
      "tryThis": ["One concrete suggestion", "Another"],
      "safetyNote": "When to tell a doctor (optional)",
      "placeholder": false
    }
  ]
}
```
- `factor` must be one of the keys in `docs/FACTOR_KEYS.md`.
- Several entries may share a factor. Required fields: `id`, `factor`, `title`. Others are optional.
- The app ignores an invalid file and keeps the last good one. Import: Me > Knowledge > Import knowledge file.
