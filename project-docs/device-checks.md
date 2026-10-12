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

- 2026-10-12 · PR #64 · A contact chip with a photo set in Contacts: the initial swaps to the photo
  with no shift, and a reopened palette shows the photo at once · Waydroid; set the photo in the
  Contacts app, open the contact twice from the palette.
- 2026-10-12 · PR #64 · App chips on a light chip (light theme or a light Look): the icon's ring is
  the dark one, not the white one · Waydroid, night mode off.
- 2026-10-12 · PR #65 · The widget picker opens without closing the launcher on Android 10 and 11
  (#62) · an API 29 or 30 emulator, or the reporter on the next drop.
- 2026-10-12 · PR #67 · A restored model works and reads like a fresh download: `tai speak` with
  KittenTTS after Import AI models, and its Model centre row shows the same size a fresh download
  shows (Waydroid read "32.3 MB" after a restore; the catalog says 90 MB) · Waydroid.
- 2026-10-12 · PR #67 · Export AI models with a model over 1 GB: leave Settings mid-copy, Cancel
  from the notification removes the half-written file and keeps finished models; Export again
  skips them · Waydroid.

## Developer

- 2026-10-12 · PR #67 · Export settings, then Import settings on pong as the default home: the
  launcher comes back as home within a few seconds with the same look, layout and terminal font
  size, and nothing asks to pick a home app.
- 2026-10-12 · PR #64 · The chip strip with your real usage on pong: Shizuku, Solid Explorer and
  the keyboard types as named chips, and typing "s" pins Shizuku.
