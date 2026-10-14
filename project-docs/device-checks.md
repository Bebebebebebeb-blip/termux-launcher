# Device checks owed

Built, merged, not yet seen on a device. The list a drop or a cut is checked against first.

**Agent** items can be driven over adb: the emulator, or pong with the developer's go-ahead.
**Developer** items need a hand on the phone: feel, frame timing, a lock screen, taste.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Verified items are **deleted**, never ticked; the PR, the commit or a bug is the record. An item
lands here in the same commit or PR as the change it belongs to, whenever a hand-over still owes
a device check. Before a drop or a cut, work through the Agent list and hand the Developer list
over; an item two releases old is re-judged and dropped if nothing rides on it. The file stays
under twenty items; past that, the oldest are re-judged first.

## Agent

- 2026-10-14 · commit "fix(launcher): an icon pack's round icons stay whole at the bigger size" ·
  Arcticons round icons (Clock, Chrome, Google) are whole circles in the dock and drawer, as big as
  the build before 860f1a67c or bigger, never cut · emulator, Arcticons from
  `https://mirror.cyberbits.eu/fdroid/repo/com.donnnno.arcticons_1570.apk`.
- 2026-10-14 · commit "fix(icons): the pinned-only switch keeps its position after System" · Icon pack: switch off, System → Done, reopen: the switch is still off; Arcticons then applies to every app. (emulator)
- 2026-10-12 · PR #64 · A contact chip with a photo set in Contacts: the initial swaps to the photo
  with no shift (the reopened palette was seen showing the photo at once, 2026-10-14) · emulator;
  the device map has the adb route to a contact with a photo.
- 2026-10-12 · PR #65 · The widget picker opens without closing the launcher on Android 10 and 11
  (#62) · an API 29 or 30 emulator, or the reporter on the next drop.
- 2026-10-12 · PR #67 · Export AI models with a model over 1 GB: leave Settings mid-copy, Cancel
  from the notification removes the half-written file and keeps finished models; Export again
  skips them · pong with the go-ahead (the emulator's smallest download is 2.4 GB).
- 2026-10-12 · commit "feat(keyboard): paste a fresh screenshot from the paste key" · With the
  storage permission granted and text copied first: take a screenshot, open the keyboard; the "v"
  key's paste corner shows the photo where the paste glyph sat; paste types a path to a copy of
  the screenshot under `$PREFIX/tmp/clipboard`; paste again gives the clipboard text and the glyph
  is back to paste; take another screenshot and paste with a modifier: the clipboard text, while
  the photo still shows · pong with the go-ahead.
- 2026-10-13 · commit "feat(voice): edit the dictation card's text in place" · Tap the waiting
  text: a blinking caret where tapped, the keyboard up under the card and the card above it
  (docked and floating); type, backspace, arrows; ✓ inserts the edit into the shell, never into
  the card; Undo shows the text before the edit, Redo the edit. Tap while listening pauses; tap
  while "Cleaning up…" keeps the text as heard and the cleanup never lands. A finger scroll of 7
  lines and a sideways swipe do not open the editor. On the Display place ✓ types into the
  display afterwards. With the in-app keyboard off the phone's keyboard comes up and types; at
  font scale 1.3 the caret sits on the line · pong with the go-ahead (the emulator has no speech model or microphone).
- 2026-10-13 · commit "fix(voice): keep a Polished cleanup's line breaks to the card and the
  insert" · A Polished list or two paragraphs show on their own lines on the card; ✓ in a shell
  with bracketed paste keeps them, in a bare shell they arrive on one line and nothing runs ·
  emulator, Polished cleanup on.
- 2026-10-13 · commit "fix(launcher): scale an icon pack as one, not icon by icon" · Seen right
  on the palette, dock, drawer and a pinned override, 2026-10-14. Still owed: an app's notification
  icon with a pack (needs a real app posting one; `cmd notification post` shows a system icon); a
  pack with no margin looks as it always did · emulator, icon packs from F-Droid
  (`me.iacn.mbestyle_27.apk`; Arcticons from `https://mirror.cyberbits.eu/fdroid/repo/com.donnnno.arcticons_1570.apk`).
- 2026-10-13 · `eff06de21`, `c111b7ed5` · Dictation cleanup from context on the phone's E2B: the
  anchor dictation and the "translate this into French" case from
  `project-docs/reference/voice-ai/voice-cleanup-context-rework-2026-10-13.md`, Light and Polished;
  no "pinned app console" or "100%", the French request stays English, nothing falls back as
  `refusal_or_answer`; cleanup's feature check passes its 500-word dictation; first-token time
  within about 0.2 s of the old prompt's · pong with the go-ahead, the same request through
  `/v1/chat/completions`.
- 2026-10-13 · commit "feat(voice): clean dictation at phrase pauses while the user speaks" · A
  90-second dictation with E2B on the GPU, Polished then Light: logcat `VoiceSessionCleanup` shows
  `cleanup early` lines while listening and a `cleanup session` line with `cleanupMs` about 1–2 s
  after Pause (was about 8 s); phrases keep transcribing at their usual speed while a pass runs; the
  card shows the text as heard until it swaps once to the cleaned text with marks; the joined text
  reads as one passage (no repeated sentence at a part boundary, a counted list on its own lines);
  no `fallback:context_echo`. Then: edit the text mid-dictation (no cleanup lands), Pause and resume
  (only the new speech is cleaned), and a CPU-only cleanup model (no `cleanup early` lines) · pong
  with the go-ahead.
- 2026-10-13 · commit "feat(tai): plan a load on the typical measured drop, not the largest" · E4B
  dictation cleanup on the GPU at 2048 is admitted with about 3.8–4 GB free (`tai logs`: a
  `load_ok … gpu ctx=2048`, no `load refused`), both with this version's own samples and right
  after an install that cleared them (the prior); any refusal reads `needs X MB + 768 MB floor = Y
  MB free, Z MB available` with the GPU's figure, not the CPU seed · pong with the go-ahead.
- 2026-10-13 · commit "feat(tai): stop a model that runs the phone under 448 MB free" · Start an
  E4B GPU load at about 3.7 GB free with a memory hog opened beside it: `tai logs` shows an
  `oom_guard … stopped the model to keep the phone responsive` line, the runtime goes away, the
  launcher keeps answering throughout, and dictation keeps the text as heard; the same while a
  long chat answer generates (cancelled and unloaded, the runtime stays) · pong with the go-ahead,
  `adb logcat -s TaiMemory`.
- 2026-10-13 · commit "fix(tai): book a memory stop as a stop, not a crash" · After the guard ends
  an E4B GPU load mid-dictation: the card keeps the phrases already heard and says "Voice input
  stopped: the speech model closed", the pill settles; within 2 min the next dictation's cleanup
  logs `load held back on gpu` and keeps the text, `tai load gemma-4-e4b` goes ahead at its window;
  after 2 min cleanup loads E4B on the GPU at 2048 again (no half window) · pong with the go-ahead.
- 2026-10-13 · commit "feat(voice): detect the spoken language with Voice language on Auto" ·
  Multilingual Whisper (Base, then Small), Voice language Auto-detect, an English keyboard layout:
  dictate a Spanish clip through the fake mic; the strip reads "Listening… · Spanish" (or
  "Transcribing… · Spanish") and the text is Spanish, not English; `logcat -s VoiceInputSession`
  shows `language=es/detected heard=es@…` on the first kept phrase and `language=es/forced` after.
  A fresh dictation of an English clip: no language on the strip, `language=en/detected`. Note each
  phrase's `detectMs` against its `decodeMs`/`decodeSteps`, and the probability a one-word command
  scores (the 0.8 bar) · pong; the emulator has no working microphone.
- 2026-10-13 · commit "feat(voice): send only the sentences that need it to the cleanup model" ·
  A real English dictation with "uh"/"um" in clean sentences and one stutter or "no wait": no
  filler is left, the clean sentences come back word for word, and logcat `VoiceSessionCleanup`
  shows `lang=en sent=N/M` with N below M (0 for a part with nothing to fix, `fallback:unchanged`
  for a clean dictation). Then a Spanish (or German) dictation with "eh"/"äh": the fillers go,
  the text stays in that language (`lang=es`), never English. No `VoiceCleanupGate` crash in the
  first cleanup (2026-10-14: ICU rejected its `(?U)` regex, so every cleanup on pong crashed until
  "fix(voice): a sentence split Android's regex engine accepts") · pong with the go-ahead, Light and
  Polished.
- 2026-09-27 · voice panel (rounds 3 and 4) · With cleanup on, dictate a passage with an "um": while
  listening and cleaning the text is italic and dim, the cleaned text is upright with changed words
  in the accent and removed words struck through, and the marks stay until the panel closes; Undo
  gives the italic as-heard text, Redo the marks; ✓ inserts the plain cleaned text. Readable on the
  light, dark and glass looks · pong with the go-ahead (the emulator has no microphone).
- 2026-09-27 · voice panel · The screen stays on while listening, cleaning and while text waits, and
  sleeps after about 3 minutes untouched (a touch on the pill restarts them); ×, ✓, Copy, a swipe
  and leaving the app let it time out at once · pong, screen timeout 30 s.
- 2026-09-27 · voice panel · The grab handle under the card: dragging moves pill and panel inside
  the place (under the status bar, above the keyboard), a sideways swipe on it never discards; the
  next dictation starts where it was left, landscape keeps its own place, a double tap returns to
  the top-right corner · pong with the go-ahead (the emulator cannot put text on the card).
- 2026-10-14 · commit "feat(keyboard): edit, reset and create layouts in Settings › Layouts" ·
  Rows, icons, add/remove/reorder, search and the removed "Custom layout" row were seen on the
  emulator, 2026-10-14. Still owed: TalkBack reads the 48dp icons; the cycle rows leave the title
  readable at 360dp; the 16-layout cap · emulator.
- 2026-10-14 · same commit · Editor: the error line, Save greyed and off until a change, the
  discard question and rotation were seen, 2026-10-14. Still owed: typing with the system keyboard
  and the field shrinking above it (the emulator never drew the IME), horizontal and vertical
  scrolling of the XML field · emulator, or pong with the go-ahead.
- 2026-10-14 · commit "feat(keyboard): slide a layout row's buttons open beneath it" · Opening,
  closing, repeated ↑ and + on a catalogue row were seen, 2026-10-14. Still owed: about 200 ms and
  a snap with animations off (judge on pong, the emulator renders on the CPU); TalkBack reads
  "name, second line, actions hidden" and offers Show/Hide · pong with the go-ahead.
- 2026-10-14 · commit "fix(keyboard): readable rebuilt layouts, no fallback flash on a first
  swap" · Readable slot names were seen, 2026-10-14. Still owed: right after creating a user layout
  that differs from the launcher layout and adding it to the cycle, the first swap shows it at once
  with no launcher-layout flash · emulator.

## Developer

- 2026-10-12 · PR #64 · App chips on a light chip (light theme or a light Look): the icon's ring is
  the dark one, not the white one; on the emulator the light chip's ring was only faintly pale
  (2026-10-14) · pong, by hand, night mode off.
- 2026-09-27 · voice panel · Keyboard → Voice input → Mic sensitivity (Normal, High): soft speech at
  arm's length is picked up on both; High lets in more of a nearby voice or video, Normal fewer;
  keyboard clicks, a fan or air conditioning alone never open a phrase on either · pong, by hand.
- 2026-10-12 · fix(appearance): Custom starts from the Look on screen · On pong, apply Clear,
  Done, reopen the editor, tap Custom: the sliders show Clear's numbers and the look does not
  change. Tweak Blur, tap Mist, tap Custom: the tweak is back. Undo: Clear again · pong, by hand.
- 2026-10-12 · PR #63 · The resting look at Clear and Mist on pong: the outer ~2 dp of every rim
  now mirrors the wallpaper (quarter-circle bevel along the true normal); it should read as a
  thick glass edge, not a double line. If it reads wrong, the profile in `GlassRefraction` is the
  dial, not the Looks.
- 2026-10-12 · PR #63 · Frame timing with Bend 30 and Dispersion 100 on pong: page slides and
  keyboard up/down at 90 Hz, no frame over 11 ms · `scripts/perf-baseline.sh`.
- 2026-10-12 · PR #63 · Dispersion fringing shows on the city wallpaper between 50 and 100%; on
  a blue test picture it was only measurable, not visible.
- 2026-10-12 · PR #67 · Export settings, then Import settings on pong as the default home: the
  launcher comes back as home within a few seconds with the same look, layout and terminal font
  size, and nothing asks to pick a home app.
- 2026-10-12 · PR #64 · The chip strip with your real usage on pong: Shizuku, Solid Explorer and
  the keyboard types as named chips, and typing "s" pins Shizuku.
- 2026-10-12 · commit "feat(agent-skills): pack the launcher and speech skills for tlstore" · With
  both tarballs from `scripts/pack-agent-skills.sh` unpacked into `~/.agents/skills/` and each
  linked from `~/.claude/skills/`, Claude Code and Codex each: open a pane beside the shell and
  wait on it; answer "how do I change the terminal font" from the docs, naming the settings path;
  with the speech skill, read one answer aloud and stop when told · pong, by hand.
- 2026-10-13 · commit "fix(dock): let a lone pinned-apps row's icons fill its bar" · The pinned
  apps alone on their dock or plank (no A–Z row, no extra keys), Docked and Floating, Icon size at
  its smallest, the default and its largest, with 5 and 10 apps, keyboard up and down: the bar is
  as tall as before and the icons fill it, centred between the ticks and the far rim; 10 apps
  never touch; an A–Z scrub's matches in that row are the same size; the Icon size slider reads
  the bigger icon, and adding an A–Z row in Layout shrinks the icons back to today's size · pong,
  by hand.
