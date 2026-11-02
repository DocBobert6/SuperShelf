# Third-party notices

Super Shelf itself is licensed under the GNU General Public
License v3.0 — see [LICENSE](LICENSE). This file reproduces the
notices required by the third-party code the app ships or
downloads on demand. The emulator core is **not in the APK**; it
is built from pinned source and downloaded on demand from the
project's `cores` release, SHA-256 verified before it is loaded.

## Emulator core

### Snes9x — non-commercial licence

- Used for: Super Nintendo (the app's only console).
- Source: `snes9xgit/snes9x` at
  `1bcc369e89f08243e0a462882fb1f3e42e51de3a`.
- Licence: Snes9x's licence is non-commercial — the app must
  stay free, and no commercial use of the core is permitted.
  See the Snes9x repository for the full licence text.

## CRT shader chain

- The CRT rendering chain is a port of crt-guest-advanced-fast
  by guest(r), licensed GPLv2-or-later. The adapted chain's
  parameters (scan, blur, curve, roundness) are exposed as the
  built-in "Violet" preset and as the base for the adapted
  Mega Bezel preset values.

## Mega Bezel (adapted presets)

- Preset names are Mega Bezel's own, credited to upstream
  (HyperspaceMadness/Mega_Bezel, GPLv3). Super Shelf's
  downloadable preset pack contains preset *values* only —
  no upstream shader code or bezel artwork is re-hosted —
  and its output is an adaptation, not RetroArch output.
  If any upstream shader code or artwork is ever copied into
  this project, the GPL terms for that material apply.

## Other libraries

- **oboe** (Google) — audio, Apache-2.0.
- **libretro API** — core interface, as used by Snes9x.
- **Android Jetpack / Compose** — Apache-2.0.
