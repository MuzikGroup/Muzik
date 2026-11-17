<div align="center">

# Muzik for Desktop

**The premium YouTube Music client for your computer — Windows, macOS and Linux, from one codebase.**

Search any song, build playlists, keep the queue alive with endless radio — all wrapped in a clean, fast, native-feeling app.

[![Release](https://img.shields.io/github/v/release/MuzikGroup/MuzikDesktop?color=7C4DFF&label=latest&logo=github)](https://github.com/MuzikGroup/MuzikDesktop/releases/latest)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-9D7BFF)](#-install-in-one-click)
[![License](https://img.shields.io/github/license/MuzikGroup/MuzikDesktop?color=FF5C8A)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/MuzikGroup/MuzikDesktop/release.yml?label=build&logo=githubactions)](../../actions)

[![Sponsor](https://img.shields.io/badge/Support_Muzik-Buy_Me_a_Soda-FF5C8A?style=for-the-badge&logo=buymeacoffee)](https://buymesoda.com/lllcuDCr25N1a1peiKOCl22Oaf13)

**Every download keeps this project alive.** If Muzik saves you money on music subscriptions, a soda is the perfect thank-you.

</div>

---

## Install in one click

Grab the installer for your system from the [latest release](../../releases/latest) — no Java, no dependencies, no terminal:

| Platform | Download | Works on |
| --- | --- | --- |
| **Windows** | `Muzik-*-windows-x64.msi` | Windows 10 & 11, installs per-user, Start-menu shortcut included |
| **macOS (Apple Silicon)** | `Muzik-*-macos-arm64.dmg` | M1 / M2 / M3 / M4 Macs |
| **macOS (Intel)** | `Muzik-*-macos-x64.dmg` | 2018–2020 Intel Macs |
| **Linux (Debian/Ubuntu)** | `muzik-*-linux-amd64.deb` | Ubuntu, Debian, Mint, Pop!\_OS |
| **Linux (RPM)** | `muzik-*-linux-*.rpm` in assets | Fedora, openSUSE, RHEL |
| **Anything else** | portable `Muzik-*.jar` | Any OS with Java 21+ — including Linux ARM64 (Raspberry Pi 5-class hardware) |

> **First-run notes (unsigned builds):** macOS: right-click → *Open* the first time. Windows SmartScreen: *More info → Run anyway*. Signed builds are planned.

## What you get

- **Search the full YouTube Music catalog** — songs, videos, albums and artists, with live suggestions as you type
- **Album & artist pages** — open any album tracklist or artist page straight from search results
- **Endless radio** — when your queue runs out, Muzik seeds a YouTube Music radio so the music never stops (toggleable)
- **Queue control** — play next, jump around, see what's coming
- **Your library, locally** — favorites, playlists and history live on your machine, always yours
- **System tray** — play/pause/skip from the tray on Windows, macOS and Linux
- **Auto-updates** — Muzik checks GitHub releases and points you straight at the right installer for your OS
- **Theme that fits** — dark, light, or follow your system, in the Muzik violet you know from mobile
- **Audio that just works** — native AAC decoding, ~128 kbps Normal or up to 256 kbps High quality

## How it works

Muzik Desktop is a **Kotlin + Compose Multiplatform** app packaged into native installers with
`jpackage` (bundled runtime — nothing to install). Playback requests use **tokenless YouTube Music
client profiles** with automatic fallback, following the measured, benchmarked approach documented by
[MetrolistGroup/innertubex](https://github.com/MetrolistGroup/innertubex) (GPL-3.0).

> **Honest note on networks:** YouTube gates unauthenticated playback by IP reputation. On home or
> office Wi-Fi it just works. On VPN or datacenter exits, YouTube may answer *"Sign in to confirm
> you're not a bot"* — that's YouTube's policy, not a bug, and the app will tell you plainly when it
> happens.

## Versioning

Muzik Desktop follows the suite-wide version (`7.8.4` and up). Every push to `main` bumps the patch
by `0.0.1` automatically and ships a fresh release with installers for all platforms — same pipeline
as [Muzik for Android](https://github.com/MuzikGroup/Muzik).

## Roadmap

- [ ] Signed installers (code signing certificates for Windows + macOS)
- [ ] Lyrics view and synced lyrics
- [ ] Linux ARM64 + AppImage packages
- [ ] Watch position sync with Muzik for Android
- [ ] iOS & Android-TV companions via Kotlin Multiplatform shared core

## Contributing

Issues and pull requests are welcome. Build locally with:

```bash
./gradlew :app:run          # run from source
./gradlew packageDmg        # macOS installer
./gradlew packageMsi        # Windows installer
./gradlew packageDeb        # Linux installer
```

## Support Muzik

<div align="center">

[![Buy Me a Soda](https://img.shields.io/badge/☕_buymesoda.com-lllcuDCr25N1a1peiKOCl22Oaf13-FF5C8A?style=for-the-badge)](https://buymesoda.com/lllcuDCr25N1a1peiKOCl22Oaf13)

**Muzik is free and open source, forever. Sodas fund signing certificates, build runners and new features.**

Questions or ideas? [Open a Discussion](https://github.com/orgs/MuzikGroup/discussions) — we answer.

</div>

## Team

Built with care by **Ignatius (Motius)** & **pipo.ai** for [MuzikGroup](https://github.com/orgs/MuzikGroup).

## Disclaimer

Muzik is an unofficial client. It is not affiliated with, endorsed by, or connected to YouTube, Google, or any of their subsidiaries. All catalog content is streamed from YouTube Music; please support the artists you love. Licensed under [GPL-3.0](LICENSE). Architecture and playback-profile design informed by Metrolist (GPL-3.0).
