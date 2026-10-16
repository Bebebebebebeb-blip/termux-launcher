# Glass controls: why Tint is dead after Clear or Mist, one parameter space, and safe new knobs (2026-10-16)

**Status (2026-10-17): built in phase B** on `feat/glass-controls-unify`, with the developer's
decisions overriding sections 4.3 and 5 where they differ: Tint is the accent amount (4.1) with
the hue as a menu pill (rank 4); the ink check measures the tinted base and the Shade (4.2); no
control is hidden or greyed by another's value (4.3 not built; Depth and the Edge pill appear only
where the device and wallpaper can draw the edge, as Depth always did; Contrast no longer greys); Glass replaces Blur and Grain on its own curve
(`blur = R(4 + 32 t^1.5)`, `grain = R(2 + 6 t)`, not the material curves of rank 2); Shade is a
flat theme-aware layer, not the K-2 film (rank 1); Vibrancy is baked into the shared frame
(rank 3); Dispersion is Off, Glint 40, Prism 80 (rank 5); the gathered edge's gain follows the
cover over it, capped at 2. Margin and Corner radius left the Look page's Custom row (approved;
seven columns fit 360dp at the 36dp floor, eight do not). Shade is black or white by each surface's
own tint, is counted in the ink vote, and is held back where it alone would break a band's target. 4.4 (clarity follows blur) is not built. The rest of this note is the research as
written.

Read-only research for the developer's report: "in the Appearance editor, Clear or Mist, then Custom: the Tint
slider does nothing; it only works after the Tint Look." No code changed, nothing built or run on a device.
Builds on `glass-refraction-controls-2026-10-15.md` and `glass-refraction-references-2026-10-15.md` (not
repeated). Paths are under `app/src/main/java/com/termux/app/` unless they start with another top-level
directory. Arithmetic marked (calc) is mine, from the cited lines. Nothing here was measured on a screen.

Settings pages are fragile (agents have broken the settings look more than once). Every recommendation here
keeps the Custom row's footprint: no new row, no new page. Where a knob is proposed it goes into an existing
row or behind an existing switch, and that is stated.

## Answers in brief

1. **The source agrees with the report.** The Tint slider is a *strength of the Look's tint family*, not an
   amount of colour. Each family is a hidden enum that only a Look sets (scheme for Clear, Obsidian for Mist,
   Material for Tint), and Custom inherits it from the Look on screen with no control to change it. Strength
   below 100 moves the family's colour toward its own luminance-matched grey (`chrome/GlassLook.java:157-175`).
   The scheme and Obsidian colours are already nearly grey, so the slider moves a few RGB levels at most. Only the
   Material family blends toward the accent, and that blend is what the slider scales (`GlassLook.java:177-188`).
   The slider also starts at its top, 100 (`TermuxPreferenceConstants.java:1500`, `surfaces/SurfacePresets.java:238-239`),
   so from Clear or Mist it can only remove colour that is barely there. It can never add any.
2. **Custom's seeding is not the bug.** Custom already starts from the Look on screen and writes nothing
   (`surfaces/SurfaceEditorController.java:2145-2167`). The fault is that one slider means three different things,
   depending on an enum the user cannot see.
3. **Unification.** Make Tint an *additive accent amount* on one axis that every family shares: 0 is the family's
   own base, untouched, and 100 is today's Material blend. Clear and Mist then ship at 0, Tint ships at 100, every
   shipped Look renders exactly as it does now, and the slider adds visible colour from any start. Two smaller
   holes need the same treatment. Depth shows under Solid's 92% slab, and the terminal's Tint shows while its pane
   is transparent. Each gets the rule the code already applies to Depth: a knob that would change nothing is not
   offered (`surfaces/AppearanceLooks.java:186-191`).
4. **The contrast check must measure what is drawn.** The ink and veil check measures the *untinted* base colour
   (`chrome/ChromeInk.java:549, 570-572, 994-1003`), not `GlassLook.tintBase`. Once Tint can be raised from any Look,
   this check has to measure the tinted base, or tint becomes a way past the 4.5:1 promise.
5. **New knobs, ranked by value against breakage risk:** (1) a darken-only Shade, which is the planned K-2 film;
   (2) a Glass macro slider along the existing material curves; (3) backdrop Vibrancy, baked into the shared frame;
   (4) a Tint hue source (primary, secondary or tertiary); (5) Dispersion as an opt-in step. Edge light, Specular
   and Clarity should stay off the UI (section 4).

## 1. How a Look becomes the glass

| Look | Material, intensity, blur, opacity, grain | Tint key (`surface_glass_tint`) | Tint strength stored | Rim | Motion | Edge (bend/width/light/spec/disp/clarity) | Source |
|---|---|---|---|---|---|---|---|
| Clear (`minimal`) | glass 50, 4, 10, 4 | `scheme` (default) | 100 | hairline | classic | 27/18/10/0/40/76 | `SurfacePresets.java:88-90, 238-239, 251-255` |
| Mist (`frost`) | frost 50, 25, 60, 8 | `obsidian` | 100 | gradient | mist | 24/16/10/0/0/0 | `SurfacePresets.java:99-105` |
| Tint (`stock`) | glass 50, 10, 46, 10 | `material` | 100 | hairline | classic | 21/14/10/0/0/0 | `SurfacePresets.java:109-111` |
| Solid | solid 78, 0, 92, 0 | `scheme` | 100 | hairline | classic | 0/1/0/0/0/0 | `SurfacePresets.java:113-115` |

- The tint enum is read by `GlassLook.of(prefs)` → `ofRequested(tint, rim)` (`GlassLook.java:121-142`). Material
  is only *requested* there. Its colours (`colorPrimaryContainer`, `colorPrimary`) are resolved only when the
  family is Material (`chrome/GlassSurfaceFactory.java:59-70`).
- The Tint slider (`Control.TINT`, 0..100, `AppearanceLooks.java:134, 364-367`) writes `surface_base_tint`, or a
  surface's detached cell (`SurfaceEditorController.java:2092-2094`, `surfaces/GlobalGlass.java:41-43`). Every glass
  surface reads it through `GlassSurfaceFactory.look(int)` → `GlassLook.withStrengthPercent` (`GlassSurfaceFactory.java:73-76`,
  `GlassLook.java:74-88`). The panes read it the same way, through `flatTint` (`TermuxActivity.java:3616-3623`).
- The Custom row offers Blur, Grain, Opacity, Tint, Margin, Corners and, while the edge is drawn, Depth. Per element
  it offers Blur, Grain, Opacity, Tint plus that element's layout knobs (`AppearanceLooks.java:193-214`). No control
  in Custom reaches the tint enum, the rim, the motion, or the edge's light, specular, dispersion or clarity
  (`surfaces/GlassEdgeControls.java:10-17`).

## 2. Root cause of the dead Tint slider

What strength does, per family (`GlassSurfaceFactory.surface`, `:292-314`):

| Family | Base colour at strength t | Sheen | What the slider can change |
|---|---|---|---|
| scheme (Clear, Solid) | `neutralised(glassBaseColor)`: mixed toward its luminance grey by `1 - t` (`GlassLook.java:152, 162-175`) | accent sheen alphas × t (`DockGlassRendering.java:52-58`; factory `:313-314`) | the base's chroma, and a sheen of at most 16 × opacity / 255 (`DockGlassRendering.java:120-127`) |
| obsidian (Mist) | `neutralised(#161822)` dark, `neutralised(#FFFFFF)` light (`GlassLook.java:153-154`, `chrome/GlassTokens.java:9-10`) | **none**: Obsidian draws a flat wash in place of the light model (factory `:310-312`) | the chroma of #161822 alone; nothing in light mode, because white's grey is white |
| material (Tint) | `materialBase`: base + (primaryContainer − base) × 0.35t, then + (primary − that) × 0.14t (`GlassLook.java:177-188`) | × t | a real hue shift toward the accent |

The base the scheme family starts from is Material `colorSurface` in dark wallpaper mode. Otherwise it is the
terminal scheme's `termuxColorSurfacePanelHigh` (`TermuxActivity.java:4175-4191`). Both are low-chroma by design.
How much each Look can move, at its shipped opacity (base alpha = opacity × 255, `DockGlassRendering.java:241-244`)
(calc, with the M3 baseline dark colours #141218 surface, #4F378B primary container and #D0BCFF primary assumed;
dynamic colour shifts them):

| Look | Base at t = 100 | Base at t = 0 | Largest channel change | After the base alpha | Sheen change |
|---|---|---|---|---|---|
| Clear (opacity 10) | 20,18,24 | 19,19,19 | 5 | about 0.5 level | 2/255 → 0 |
| Mist (opacity 60) | 22,24,34 | 25,25,25 | 9 | about 5 levels, all on blue | none drawn |
| Mist, light mode | 255,255,255 | 255,255,255 | 0 | 0 | none drawn |
| Tint (opacity 46) | about 64,53,91 | 20,18,24 | about 67 | about 31 levels | 7/255 → 0 |

So the report holds on every path the source shows: after Clear or Mist the slider spans less than one to about
five 8-bit levels, under blur and grain. After Tint it spans about 31. The one user who would see the slider work
after Clear is a scheme-chrome user whose terminal scheme has a saturated panel colour. Even then it only
desaturates.

A second, smaller dead case: the terminal's Tint draws nothing while wallpaper mode leaves the pane at opacity 0,
because the pane tint is then transparent (`TermuxActivity.java:3618-3623`, `terminal/TerminalSurfacePolicy.java:24-27`).

## 3. Every Custom control from every Look

"No-op" means no visible change across the slider's whole travel. "Misleading" means it moves something, but not
what its name says, or it moves it differently depending on which Look Custom was seeded from.

| Control | Clear | Mist | Tint | Solid | Why |
|---|---|---|---|---|---|
| **Tint** | no-op | no-op (and in light mode exactly nothing) | works | no-op | section 2 |
| **Opacity** | low end held | works | works | works | the ink check raises the base to `max(userAlpha, veil)`, up to 0.55 (`chrome/OnGlass.java:85, 99-108`; factory `:322-328`). On a mid or bright wallpaper, Opacity below the veil changes nothing. That is the legibility promise working, but the slider does not say so |
| **Blur** | works, but also switches clarity off at ≤ 2 dp | works | works | weak: 92% slab, so about 8% of the frost shows | Clarity's sharp frame exists only when blur > 2 dp (`chrome/WallpaperFrostPainter.java:349`). Clear's clarity 76 vanishes there, but the face is then nearly sharp anyway, so it is benign |
| **Grain** | works | works | works | works | its own layer, alpha = grain × 0.6 (`DockGlassRendering.java:26-28`; factory `:338-340`). Above about 40 it reads as sand (`SurfacePresets.java:93-96`) |
| **Depth** | works (bends the sharp picture, clarity 76) | works (bends the frost, clarity 0) | works (frost) | near no-op: drawn under a 92% base | offered whenever refraction is on (`SurfaceEditorController.java:1824`, `TermuxActivity.java:3788-3797, 11800-11802`). Depth writes only bend and width (`GlassEdgeControls.java:42-54`), so the same Depth reads differently, depending on the clarity the seeding Look left |
| **Contrast** (terminal) | | | | | greyed with wallpaper colours off (`SurfaceEditorController.java:1821, 1827`). Already handled |
| Margin, Corners, Key radius, Key spacing, Icon size, App icons | | | | | Layout's own and named by no Look (`SurfacePresets.java:43-46, 264-287`). Not affected |

Hidden switches the Looks set and Custom cannot reach: the tint family, the rim (gradient or hairline; the gradient
reaches only the panes, `terminal/PaneRim.java:112`), the motion profile, and the edge's dispersion and clarity.
Edge light and specular are stored, but the program ignores them (`chrome/GlassRefraction.java:166-176`).

## 4. A unified model

The principle: the Looks become points in one shared parameter space, and every knob in Custom is an axis of that
space with the same meaning from every point. Where an axis cannot show anything at the current point, the knob is
not offered, which is the rule `AppearanceLooks.controls` already applies to Depth (`AppearanceLooks.java:186-191`).
Custom keeps seeding from the Look on screen (`SessionCustom`, `SurfaceEditorController.java:2171-2205`). That part
is right and stays.

### 4.1 Tint becomes an additive accent amount (the fix)

| Today | Proposed |
|---|---|
| `surface_base_tint` = how much of the family's tint is worn, default 100 | `surface_base_tint` = how much accent is mixed into the family's base, 0..100, default **0** |
| family ∈ {scheme, obsidian, material}, and material is the only one with a hue | family ∈ {scheme, obsidian} names the *base ink* only. Material becomes "scheme at amount 100" |
| strength < 100 desaturates | 0 = the base untouched; 100 = today's Material blend (0.35 toward primary container, then a 0.14 wash toward primary) |

Changes, concretely:

1. `GlassLook`: replace `mStrength` with `mAmount`. `tintBase(base)` = family ink (scheme base, or Obsidian ink), then
   `materialBase` applied to *that* ink with blend 0.35 × amount and wash 0.14 × amount. `materialBase` already takes
   any base (`GlassLook.java:177-188`). `flatTint` does the same, then composites the Obsidian wash as now
   (`:201-217`). `neutralised` goes, or survives only behind the chromatic-scheme case in the last bullet below.
   `isMaterialRequested` becomes `amount > 0` (`:115-128`).
2. `GlassSurfaceFactory.look()` resolves `colorPrimaryContainer` and `colorPrimary` whenever the amount is above 0, not
   only for the Material family (`GlassSurfaceFactory.java:59-70`).
3. Separate the sheen from tint: pass 1, not `look.strength()`, to `lightModelSlice` (`GlassSurfaceFactory.java:313-314`).
   Every shipped Look draws its sheen at 1 today, so nothing visible moves.
4. `SurfacePresets`: `preset()` takes the tint amount instead of hard-writing `DEFAULT_SURFACE_BASE_TINT`
   (`SurfacePresets.java:238-239`). Clear 0, Mist 0, Tint 100 with family `scheme`, Solid 0. `DEFAULT_SURFACE_BASE_TINT`
   becomes 0 (`TermuxPreferenceConstants.java:1500`), and the four per-surface getters clamp as now
   (`TermuxAppSharedPreferences.java:418-457`).
5. Migration (format 5, bump `FORMAT_VERSION`, `SurfacePresets.java:58`): an old (family, strength s) becomes
   scheme or obsidian at amount 0, and material at amount s. This applies to the base, each detached per-surface
   cell, the saved Custom blob (`custom()`/`deserialize`, `:304-308`) and theme imports. The default fill for a blob
   without the tint key, now 100 (`:415-417`), must become "0 unless the family is material". The shipped Looks
   then match their stops again unchanged, because stored and preset values go through the same mapping.
6. Rendering identity (calc): Clear, Mist and Solid at amount 0 equal today's strength 100 for their family. Tint
   at amount 100 on the scheme base equals today's Material at strength 1. No Look moves.
7. What is lost: desaturating a *chromatic* scheme panel colour (scheme-chrome users with a saturated terminal
   scheme). If the developer wants it back, keep one rule: when the base's chroma is above a threshold, the slider's
   0 end desaturates. That is a second meaning, so the default recommendation is to drop it.

### 4.2 The ink check measures the tinted glass (needed before 4.1 ships)

`ChromeInk` resolves veil and ink against `mBaseColor = glassBaseColor()` (`ChromeInk.java:994-1003`). It uses that
for the flat backdrop and the worst light-model stop (`:549, 570-580, 597-618`), but the base actually drawn is
`look.tintBase(glassBaseColor)` (`GlassSurfaceFactory.java:292-293`). Today that gap exists only at the Tint Look.
Once 4.1 makes tint reachable from every Look at every opacity, the measured colour has to be the tinted one.
`GlassLook` is pure ints, so `ChromeInk.readMode` can take `look.tintBase(base)` and add the amount to its memo key.
With that, no tint value can break the 4.5:1 body-text target (`OnGlass.java:52`). The veil makes up the
difference, up to its 0.55 cap, and above the cap the ink moves instead (`OnGlass.java:73-85`).

### 4.3 Offer a knob only where it can show

- **Depth**: drop it from the global set while the base opacity is at least about 85, the way it is dropped while the
  edge is off (`AppearanceLooks.java:193-198`, `SurfaceEditorController.java:1824`). The threshold is a judgement
  (Solid is 92, the highest glass Look is 60), and the exact value is unmeasured.
- **Terminal Tint**: grey it, the way Contrast is greyed (`SurfaceEditorController.java:1827`), while
  `TerminalSurfacePolicy.showsTerminalSurface` is false. Its legend can say "Pane is clear".
- **Opacity under the veil**: no new control. Optionally, mark the band's current veil floor on the slider's track
  (one tick, from `ChromeInk.bandVeil`), so the user can see why the low end holds. Leave it out if the panel cannot
  draw a tick without layout risk.

### 4.4 Optional: clarity follows blur instead of the Look

Clarity is the one remaining hidden per-Look value that changes what Depth looks like. A deterministic
`clarity = 76 × clamp((10 − blur) / 6, 0, 1)` reproduces Clear (blur 4, 76), Tint (10, 0) and Mist (25, 0) (calc).
Solid has bend 0, so it shows no clarity either way. It would be computed in `GlassRefraction.Look.of`
(`GlassRefraction.java:208-218`), with the stored key kept for loading old looks and ignored by `matches`. This is
lower value than 4.1 to 4.3. The references treat clarity as a per-style constant
(`glass-refraction-controls-2026-10-15.md:109-121`), so this step is the developer's call.

## 5. New controls that cannot reach a broken state

The guards that every candidate has to respect are already in the code or the plan: the ink check and its 4.5:1
target (`OnGlass.java:52, 85`); "nothing gets whiter" and no opacity gain on big glass, because that is "the milk"
(`project-docs/active/glass-plan-2026-10-16.md:405-411`, the dropped `uBody` at `:565-569`); new glass terms as opt-in
knobs (`glass-plan:56-57`); no frame above 16.7 ms (`project-docs/active/fancier-glass/SPEC.md:36-38`); uniforms written
only on change and no new samples (`glass-plan:582-588`); `RuntimeShader` work only on Android 13+
(`docs/adr/0005-fancier-glass-refracts-the-shared-frame.md:43-44`); and live wallpapers stay removed. Android's own
note on cost: a `RenderEffect` over a view tree "is more expensive than drawing a custom View"
(https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl).

| Rank | Knob | What it does | Maps to | Safe range and clamp | What could break | How it is made impossible |
|---|---|---|---|---|---|---|
| 1 | **Shade** (darken only) | dims the face behind the content, like Apple's 35% dimming layer for clear glass (`glass-refraction-controls-2026-10-15.md:64`) | the planned K-2 film, `uFilm`, cap 0.40, default 0 (`glass-plan:240, 264`). Below 13: a black layer under the veil in `GlassSurfaceFactory.surface` | 0..40%, clamped in the setter, and then `min(shade, 0.40)` in the program | invisible dark ink (dark mode on dark glass); doubling with the veil | darken-only, so it can never go milky; the ink check measures it (as in 4.2), and the plan already turns the film off for dark ink (`glass-plan:250-251`) |
| 2 | **Glass** (one macro, clear to frosted) | moves blur, opacity and grain together along the Look's family curve | `surface_material_intensity`, already stored and applied (`SurfacePresets.java:233-234`, `surfaces/SurfaceMaterials.java:35-44`), with no slider today | 0..100, already clamped (`SurfaceMaterials.java:36`) | the user expects it to keep their hand-tuned triple | the curves are bounded by construction (blur ≤ 48, opacity ≤ 100). It is the only end-user lever Apple ships (`glass-refraction-controls-2026-10-15.md:128, 145`). Put it at the left of the global set, replacing nothing; it rewrites the triple, and Undo covers it |
| 3 | **Vibrancy** | saturates the wallpaper under the glass (Kyant `vibrancy` 1.5, Haze `chromaMultiplier`) | new: a saturation `ColorMatrix` baked once into the shared pre-blurred frame (`docs/adr/0004-every-glass-surface-samples-the-shared-frame.md`), so it adds nothing per frame; `RenderEffect.createColorFilterEffect` is the API 31 path (https://developer.android.com/reference/android/graphics/RenderEffect) | 0.8..1.5, stored in percent; 1.0 default and off | garish colour; the ink check reading the unsaturated frame | measure the same baked frame the glass samples. A wallpaper change already re-bakes it; there are no live wallpapers. Opt-in switch, default 1.0 |
| 4 | **Tint hue** (Accent, Secondary, Tertiary) | picks what 4.1's amount blends toward | `colorPrimaryContainer`/`colorPrimary`, or the secondary or tertiary pair, in `GlassSurfaceFactory.look()` (`:59-70`) | an enum of three; unknown reads as Accent (`TermuxAppSharedPreferences.java:3279-3286` already does this for the family) | a hue that clashes with the ink | the ink check (4.2) measures the tinted base, so legibility holds whichever hue is chosen. As a chip row inside the Tint column it adds no new row |
| 5 | **Dispersion** (Off, Glint) | colour fringe at corners | `uDisp`, already stored, 0..0.05 share (`GlassRefraction.java:142, 236-242`) | two steps, 0 and 40 (Clear's, about Haze Clear's 0.02 share, `SurfacePresets.java:84-86`); no free slider | a rainbow outline; battery | two steps cap it at the reference level. It is "the knob that costs battery, and it is the user's to turn up" (`SPEC.md:102-104`); Android 13+ only |
| 6 | Shadow (C-11) | soft drop shadow, 24 dp, 4 dp, 10% black | planned, built only if the contrast ratio gains at least 1.10 (`glass-plan:543, 551`) | fixed values, switch only | jank: a blurred shadow under a blurred surface | already gated by the plan; a switch, never a slider |
| not offered | Edge light, Specular, Clarity | | stored keys; light and specular are ignored by the program (`GlassRefraction.java:166-176`) | | neon outline, milky rim (`glass-refraction-controls-2026-10-15.md:45-57`) | no reference gives these to end users (`:136-137, 147`); keep them fixed |
| not offered | Lift, white point, "frost brighter" | | | | milky, which was rejected (`glass-plan:405-411, 565-569`) | do not build |

Interactions that remain, and the guard each relies on:

- Shade plus a high Opacity plus a dark scheme can make dark ink unreadable. The ink check moves the ink when the
  veil caps (`OnGlass.java:73-85`). Shade only darkens, so it cannot go milky.
- Vibrancy plus a large Tint amount can look loud, but never illegible, provided 4.2 is in.
- Glass (the macro) after hand-tuning overwrites the triple. That is expected; the stop reads Custom, and Undo
  restores the state at open.
- Reset is the existing one: any Look stop applies a complete Look, and Undo returns to the open state
  (`SurfaceEditorController.java:82-89`). No knob needs its own reset.

Settings footprint: 4.1 to 4.3 add no control. Rank 1 adds one column to the global set. Rank 2 adds one column.
Ranks 3, 5 and 6 are switches or chips behind the existing Fancier Glass row, as the plan's one-switch, one-row
rule wants (`glass-plan:54-58`). Rank 4 is a chip row inside the Tint column.

## Untraced

- Every visibility claim in sections 2 and 3 is arithmetic, not a screenshot. The real `colorSurface`, primary
  container and primary on pong and the emulator were not read. A pong or emulator screenshot pair (Clear then Custom
  with Tint at 0 and 100) would settle it.
- Whether `blur = 0` (Solid) also turns off the backdrop and refraction path was not traced past
  `DockGlassRendering.blurEnabled` (`:246-248`).
- The Opacity value at which Depth stops showing (85 is a guess).
- How large the gap from 4.2 is today at the Tint Look: the colours ChromeInk measures were not compared with the
  colours drawn.
- Exact API levels on the `RenderEffect` reference page: the page was too long to read in full here. The
  `createColorFilterEffect` level (31) is from memory, not from the page. The AGSL page was read for the cost quote only.
- The Apple HIG Materials page did not load. The Apple claims are taken from the 2026-10-15 note, which fetched it.
