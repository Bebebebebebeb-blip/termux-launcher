# Performance baselines

The floor every change holds. AGENTS.md ("Memory discipline", "It has to feel right") says why; this
file says how much, and how to tell. A **baseline** is a number measured on pong that a change must
not move the wrong way. A **budget** is a limit the code enforces and a test asserts.

**Pong** is the device of record (Nothing Phone (2) A065, Android 16, 120 Hz panel, so an 8.3 ms
frame). Older documents say 90 Hz; name the refresh rate behind any frame number you write.
Emulator and Waydroid numbers count for memory and start-up trends only. They do not count for
frame time or jank, because `gfxinfo` on ANGLE or lavapipe reports every frame as janky. Ask before
measuring on pong; AGENTS.md's rule on touching a real phone applies.

## The rules

1. **At rest, nothing draws.** A new animation ends. A new clock, sampler or poller slows down in
   lazy mode (`STATS_LAZY_MULTIPLIER`) and stops when nobody is watching. A place that is not on
   screen draws nothing (`707920f71`).
2. **Every pixel has a budget.** Anything that keeps bitmaps, frames or decoded images goes in a
   store with a byte budget. The budget is a constant beside the store's code, and a test asserts
   it. The stores that exist, with their tests as the pattern to copy:

   | Store | Budget | Test |
   |---|---|---|
   | `KittyImageStore` | 32 MB / 256 images stored; frames total RAM ÷ 48, between 64 and 256 MB | `KittyImageStoreTest` |
   | `LauncherIconStore` | heap ÷ 16, between 4 and 16 MB; artwork kept at 192 px | `LauncherIconStoreTest` |
   | `DockIconCache` | heap ÷ 8, between 8 and 32 MB | `DockIconCacheTest` |
   | `WallpaperBlurCache` (the shared frame) | 72 MB, at most 6 radii, about 1 MB per radius at quarter resolution | `WallpaperBlurCacheTest` |

   Work out the cost from the pixels a drawable actually holds (AGENTS.md has the
   `AdaptiveIconDrawable` trap). A store gives memory back on `clear`, when a pane closes and under
   pressure; `ChromePolicy` and `TermuxActivity.onTrimMemory` show which pressure level drops what.
3. **A frame does only the frame's work.** Keep allocation, I/O, decoding and layout requests out
   of `onDraw`, touch handlers and per-frame callbacks. A transition runs one geometry pass
   (`GeometryScheduler`, `KeyboardGeometryChoreographer`, and their tests). Glass samples the shared
   frame, which is blurred once per radius (ADR 0002, 0004). Blurring per surface or per frame is
   what these rules exist to stop.
4. **Heavy work runs off the main thread.** That covers wallpaper decode, blur, file and network I/O
   and package queries. Native model work runs in the `:tai_runtime` process.
5. **Prefer many thin frames to a few fat ones.** On a 120 Hz panel, capping the clock or throttling
   the live blur made the median frame worse and tripled jank (`e40f8bc7b`). To get a cost down,
   make each frame cheaper; lowering the frame rate is the wrong fix.
6. **Fail-safe gates.** A gate on drawing carries a backstop (AGENTS.md, "It is the home screen").

## Baselines on pong

| What | Baseline | Where it was measured | How to measure |
|---|---|---|---|
| Idle PSS, live terminal | 185–200 MB (±10 MB between readings) | `project-docs/verification/x11/results.md`, 2026-09-05 | `dumpsys meminfo <pkg>`, TOTAL PSS, three readings |
| Memory over repeated transitions | keyboard shown and hidden 10 times: native heap +0.7 MB; idle native heap about 108 MB | `c37c48778` | `dumpsys meminfo` before and after the loop |
| Typing in the terminal | frame p50 15 ms, p90 19 ms | `c37c48778` | `terminal.state` → `performance` |
| Heavy terminal output | median frame 5 ms, under 3% janky | `ac27b88a9`, `e40f8bc7b` | `terminal.state` → `performance` |
| Glyph-heavy render, slice 10a | window p95 22 ms; pane render median 2.4 ms; about 336 KB/s allocated, 0 GCs | `project-docs/reference/terminal/kitty-protocol-features.md` | `terminal.state` → `performance` |
| At rest, lazy mode on | 1.2 fps, about 2% of a core | `ac27b88a9` | `dumpsys gfxinfo <pkg>` over a minute, plus `top` |
| At rest, lazy mode off | about 21 fps, from the seconds flip (an open item; any change should lower it) | `project-docs/active/perf-home-terminal-idle-handoff-2026-09-27.md` | as above |
| Sliding between places | median 15–16 ms; worst page-change frame 47 ms | `707920f71`, `c6223ec76` | perfetto, `Wall.*` trace sections |
| Pre-roll and settle frames | 30–77 ms now. Target: none above 16.7 ms | `project-docs/active/fancier-glass/SPEC.md` | perfetto |
| Cold start | about 2 s of main thread to first frame (pong trace); `am start -W` 4.0 s on a release build under Waydroid | `project-docs/active/release-build-type-2026-10-06.md` | `am force-stop`, then `am start -W`, three runs |
| Release APK, universal | about 100 MB | same | file size |
| On-device AI runtime | 330 MiB resident at rest; E4B on GPU: first token 1.11 s, decode about 11 tok/s | `TaiResidency.java`, `project-docs/reference/voice-ai/gallery-gpu-loading-comparison.md` | `tai benchmark --compare` |

Read `performance` from the `terminal.state` action. Pass `{"resetPerformance": true}` first so
that the window, allocation and pane counters all start from zero. The fields are explained in
`project-docs/reference/terminal/kitty-protocol-features.md`. Trace sections (`Trace.beginSection`)
already exist for chrome, frost, accessory geometry, terminal append and the wall slide. Add one for
any new hot path.

The 601 → 171 MB figure in older notes came from a degraded session: 115 apps and a 170-frame GIF.
The RAM work capped how far memory can grow and made it come back. It did not lower the idle floor.
Compare against the idle row above.

## Done

You are done with a change that touches the rules above when your report covers each of these:

- **Rules.** Which rules applied, and what the change does about each.
- **Budget test.** Each new or resized store has its budget constant and a test that asserts it.
- **Baselines.** Each baseline the change could move has a before and an after, measured on the
  same build type in the same state. If you reasoned a number from the code, mark it as reasoned:
  it still owes a device check. A move bigger than the noise in the wrong direction makes the
  change **red**. Stop and report it. Do not tune it away.

Nothing here is automated yet. There is no benchmark module and no scripted probe; CI checks neither
APK size nor lint, and StrictMode is off. The checks are the ones you run.
