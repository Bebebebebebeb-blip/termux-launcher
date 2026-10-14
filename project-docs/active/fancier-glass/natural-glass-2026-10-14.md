# Natural glass — working note (2026-10-14, overnight)

Status: in progress on branch `feat/glass-natural` (worktree `.claude/worktrees/glass-natural`, based on
`pre` at a35f6ef93). The developer asked, before going to sleep: natural, beautiful glass that uses the
AGSL program; new edge and refraction effects wired into the shipped Looks; a few high-value extra
effects and touch feedback on glass with little movement; production quality (what Apple or Samsung
would ship); verify on the emulator (never pong); keep these docs current; file a PR for the work and a
PR for every bug or design inconsistency met on the way.

## What changed and why

Read with SPEC.md §4 (the five Edge knobs). The program is still `chrome/GlassRefraction.AGSL`, one
compile per surface, uniforms written only when something moved; idle cost stays zero.

| Effect | What it does | Knob | Where |
|---|---|---|---|
| Lit rim | The hairline rim is lit from above and a little to the left (`kLightUp` = (-0.33, -0.944)) with a weaker light answering from below. Top edge bright, bottom softer (0.6×), sides faint (floor 0.40 of Edge light), never gone, since the hairline is what reads as the edge. Replaces the one-brightness hairline, which read as a stroke. | Edge light (same knob, new distribution) | AGSL `rimK` |
| Thickness | Across the bend band the picture brightens toward the lit rim (up to +12%) and dims toward the shaded one (up to −6%), multiplicative, fading to nothing at the band's inner edge. The bend alone looked like a smear; this is the bevel's shading. | None: scales with Bend, full from 8 dp (`Look.thickness`) | AGSL `thick`, uniform `uThick` |
| Edge clarity | A second input, `sharp`: a clearer frame of the same wallpaper rect. Inside the band the bent edge shows it in place of the frost, on x² (near the frost over the inner half, the knob's value at the rim). One extra sample, only in the band, only when the Look asks. Bound to the content itself until a surface hands one in; under a RenderEffect (the dock) the term stays 0, so an unbound shader is never read. | Clarity, `fancier_glass_clarity`, 0–100, default 0 (sixth Edge slider) | `Program.setClarityInput`, `SharedFrameDrawable.setClearFrame`, clear frame = cache radius `CLARITY_RADIUS_DP` (2 dp) |
| Touch light | A finger on the glass: the rim nearest it catches 1.5× more light, a faint glow (0.045) scatters under the fingertip, the picture is drawn a tenth of the way toward it (`TOUCH_LENS`). Rises in 110 ms, falls in 240 ms, follows the finger. Light, not motion. | None (off under reduced motion, lazy mode, battery saver) | `Program.setTouch`, `SharedFrameDrawable.setTouch`, `chrome/GlassTouchLight` (driver), `GlassPress` |

Kept as they were: Shine (the diagonal bevel light) and Dispersion stay 0 in every Look; the key lens;
every seam rule; the plain draw below API 33.

New Look recipes (bend, edge width, edge light, shine, dispersion, clarity):
Clear 6/14/24/0/0/70 · Mist 9/20/18/0/0/45 · Tint 6/14/22/0/0/55 · Solid unchanged, clarity 0.
Preset format version 4; a look stored earlier reads clarity 0.

## Progress

- [x] Shader, `Look.clarityPercent`, pref key, `Program.setClarityInput/setTouch`, unit + instrumentation tests (fc28efa6f)
- [x] `SharedFrameDrawable.setClearFrame/setTouch` (48c80eef7)
- [ ] Clear-frame plumbing (frost painter, keyboard host, pane slabs), presets v4, Clarity slider — worker, in flight
- [ ] Touch light driver and wiring (sheet card rows, dock icons) — worker, in flight
- [ ] Emulator review: each Look at rest (dock, keyboard, sheet, panes, status band), the Edge sliders, a press on a sheet row and a dock icon; zoom crops; a fresh reviewer agent on the crops
- [ ] Instrumentation test on the emulator: `GlassRefractionAgslInstrumentationTest`
- [ ] Update SPEC.md §4 and the ADR; PR

## Open questions for the developer

1. The lit rim changes the resting look of every Look (and the dock in the default mode): sides are
   fainter than before at the same Edge light. If that reads as too faint on pong, raise the floor
   (`rimK` 0.40) or the Looks' Edge light.
2. The thickness shading is tied to Bend rather than a seventh knob. A knob would need a seventh Edge
   slider slot.
3. A clear frame at 2 dp is held at full size (about 15.5 MB for a 1.5×-wide frame on pong). If that
   is too much next to the radii in use, store it at half size (the renderer's downsample factor).

## How to pick this up

```
cd .claude/worktrees/glass-natural && git log --oneline pre..HEAD
./gradlew -q :app:testDebugUnitTest --tests 'com.termux.app.chrome.*' --tests 'com.termux.app.surfaces.*'
```
Emulator: `ad -s emu lease acquire`, `ad -s emu emu start`, install
`app/build/outputs/apk/debug/termux-app_apt-android-7-debug_x86_64.apk`, set a wallpaper from the
launcher (device-map.md), Appearance editor → Look → Edge.
