# Device checks owed

Built, merged, not yet seen on a device. The list a drop or a cut is checked against first.

**Agent** items can be driven over adb: Waydroid, or pong with the developer's go-ahead.
**Developer** items need a hand on the phone: feel, frame timing, a lock screen, taste.

One line per item: date · commit or PR · what to look at · what passes · how (device, route).
Verified items are **deleted**, never ticked; the PR, the commit or a bug is the record. An item
lands here in the same commit or PR as the change it belongs to, whenever a hand-over still owes
a device check. Before a drop or a cut, work through the Agent list and hand the Developer list
over; an item two releases old is re-judged and dropped if nothing rides on it. The file stays
under twenty items; past that, the oldest are re-judged first.

## Agent

- 2026-10-12 · PR #63 · Floating style: the keyboard capsule and the dock card catch the Shine
  light on their top-left and bottom-right corners and not the other two, at Shine 100% · Waydroid,
  managed wallpaper set; Layout tab → Floating, then Look → Custom → Edge.

## Developer

- 2026-10-12 · PR #63 · The resting look at Clear and Mist on pong: the outer ~2 dp of every rim
  now mirrors the wallpaper (quarter-circle bevel along the true normal); it should read as a
  thick glass edge, not a double line. If it reads wrong, the profile in `GlassRefraction` is the
  dial, not the Looks.
- 2026-10-12 · PR #63 · Frame timing with Bend 30 and Dispersion 100 on pong: page slides and
  keyboard up/down at 90 Hz, no frame over 11 ms · `scripts/perf-baseline.sh`.
- 2026-10-12 · PR #63 · Dispersion fringing shows on the city wallpaper between 50 and 100%; on
  a blue test picture it was only measurable, not visible.
- 2026-09-27 · parked list `active/device-checks-pending-2026-09-27.md` (voice redesign, keyboard
  off, tai speak): fold what still applies into this file, drop the rest.
