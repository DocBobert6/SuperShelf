# Super Shelf

A standalone SNES library manager for Android. Home screen is the
library — nothing else. Import your own SNES ROMs, find cover art,
download the Snes9x core, and play with a replica SNES touch
controller drawn as flat outlines.

**Beta — first build (0.1.0-beta.1).** Signed beta builds are
published on the
[Releases](https://github.com/DocBobert6/SuperShelf/releases) page
once signing secrets are set (see below); until then, CI builds a
debug APK as an artifact only, which is disposable and cannot
update to a signed release without an uninstall first.

## What it is

- **Library first:** grid or list, search, sort by title / added /
  last played, favourites, recently played, rename, per-game detail.
- **Import:** `.smc`, `.sfc`, `.zip` only in v1. Import copies the
  ROM into app storage; your original file is never moved, modified,
  or deleted. Deleting a game deletes only the app's copy, its
  saves, and its art.
- **Cover art (v1):** libretro thumbnails only, plus your own
  imported cover — server fetches never overwrite a custom cover.
  Multi-source search (SteamGridDB and others) is Phase 2.
- **Core, not bundled:** Snes9x is downloaded on demand from a
  dedicated `cores` release in this repo, built from pinned source
  in CI, and verified (size + SHA-256 + manifest signature) before
  it is ever loaded. No core ships in the APK. Without the core,
  the app says plainly "download the core to play" — never a dead
  launch.
- **Mega Bezel (adapted), not bundled:** reflective presets are
  downloaded as a preset pack from a dedicated `shaders` release
  and verified the same way. These are *adapted* values for this
  app's native CRT chain plus a Compose bezel frame + reflection —
  they are not RetroArch slang presets, and the output is not
  RetroArch-identical. Preset names are Mega Bezel's own, credited
  to upstream (HyperspaceMadness/Mega_Bezel). The pack contains
  preset values only; no upstream shader code or bezel artwork is
  re-hosted. Without a pack, games use the built-in CRT look.
- **Mega Bezel shader selection:** choose a reflective preset
  once as the app-wide default, or per game (per-game wins,
  "Use default" clears the override). Only presets with a
  reflection actually count as reflective — every offered
  preset is labelled "Mega Bezel (adapted) — with reflections".
  Without a pack, the picker offers the pack download instead
  of a dead list, and games use the built-in Violet CRT look.
  A choice applies on the next launch, never mid-session.
- **Bulk covers:** "Find all covers" in Downloads & settings
  searches every game in one run, sequentially, with progress
  ("12 of 47") and Cancel, and a summary at the end (found /
  already had / skipped custom / title screen only / no match /
  failed, with failed titles listed). Custom covers are never
  overwritten; custom-covered games are skipped.
- **Controller:** replica SNES layout (Mariner's `snesDefaults`),
  flat outline only (transparent fill), exactly two themes —
  **SNES** (real controller colours) and **Flat** (white only) —
  with adjustable opacity (stroke + label together) and adjustable
  stroke width. Portrait + landscape layouts, per-game layout and
  overlay overrides, reset-to-replica, multi-touch, diagonals,
  haptics toggle, physical controller support.

## What it never does

- No ROMs bundled, no ROM search, no ROM download.
- No browser, no other consoles, no commercialisation.
  Snes9x's licence is non-commercial, so this app stays free.

## Building

CI (`.github/workflows/android.yml`) builds a debug APK on every
push to `main` and a signed release APK only when the four
`ANDROID_KEYSTORE_*` secrets exist. Tag-gated releases require
the `release` environment with a manual-approval reviewer, and the
tag must exactly match `versionName` (`v0.1.0-beta.1` for the
first build).

Core builds (`.github/workflows/core-build.yml`) build Snes9x from
pin `1bcc369e89f08243e0a462882fb1f3e42e51de3a` and publish it +
`signed cores.json` to the `cores` release. It requires the
`CORES_SIGNING_KEY` secret. The shader pack requires the
`SHADERS_SIGNING_KEY` secret for its manifest signature
(see `shaders-pack/README.md`).

## Credits

- Snes9x (libretro core, non-commercial licence) — core downloaded
  on demand, never bundled.
- Mega Bezel (HyperspaceMadness) — preset names and reflective
  look, adapted; see the credit line in `shaders-pack/shaders.json`.
- CRT chain: a port of crt-guest-advanced-fast by guest(r)
  (GPLv2-or-later), as in the original Mariner code.
- Started from Notscape Mariner's emulator code, stripped to
  SNES-only; Super Shelf is its own app, not a browser.
