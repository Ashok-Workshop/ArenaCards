# ArenaCards asset inventory

Updated: 2026-11-14

## Current original visuals

| Asset / visual | Implementation | Provenance |
|---|---|---|
| Standard card faces | Compose shapes, gradients, text glyphs, and action labels in `core/designsystem` | Original in-app drawing; no commercial card-game artwork |
| Wild card mark | Compose `Canvas` four-shard color emblem | Original ArenaCards design |
| Card back | Compose gradients, border, and ArenaCards emblem | Original ArenaCards design |
| ArenaCards logo mark | Compose `Canvas` four-shard emblem | Original ArenaCards design |
| Android launcher icon | XML vector drawable used by the app launcher | Original ArenaCards geometric card mark; no external artwork |
| Android splash | Dark native splash theme using the launcher mark | Native Android theme; no raster splash asset |
| Player avatars | Distinct Compose `Canvas` emblems for ember, tide, grove, sun, comet, crown, spark, and wild | Original geometric artwork selected by the existing avatar ID; no profile schema change or bundled portrait art |
| Game table | Lightweight Compose-drawn oval felt rings and ambient radial shading | Original procedural drawing; no image texture or game-engine dependency |
| Card selection and reveal | Compose graphics-layer tilt/lift and event-keyed 3D-style card flip | Lightweight native animation; flip is skipped when reduced motion is enabled |
| Splash card fan | Existing card composables with transforms | Uses the same original in-app card visuals |

No external raster artwork, font, or recorded audio files are bundled. The launcher mark is an original local vector drawable; card/table/avatar visuals are drawn at runtime with Compose.

## Sound and motion

- Optional game feedback tones use Android `ToneGenerator`; no third-party or copyrighted sound recording is bundled.
- Background music, recorded sound effects, and notification audio are not implemented.
- Reduced-motion setting disables card selection lift/tilt and skips the new card flip; the setting is device-local.

## Asset follow-up

- Review card legibility, color contrast, glyph clarity, and scaling with TalkBack and large font settings on physical devices.
- If recorded audio or external artwork is added later, record its author/source, license, attribution requirements, file location, and redistribution rights here before release.
- Confirm final app icon, store screenshots, and marketing artwork separately; release branding and Play Store assets are not complete.
