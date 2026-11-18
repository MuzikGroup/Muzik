<div align="center">

# Muzik for Desktop

**Your entire music world on your computer — Windows, macOS and Linux. Built from scratch, made for everyone.**

Search any song, stream in high quality, follow synced lyrics, manage playlists and downloads, cast to your TV — wrapped in a polished, native-feeling app.

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
| **Windows** | `Muzik-*-windows-x64.msi` (portable ZIP in assets) | Windows 10 & 11, Desktop + Start Menu shortcuts included |
| **macOS (Apple Silicon)** | `Muzik-*-macos-arm64.dmg` | M1 / M2 / M3 / M4 Macs |
| **Linux (Debian/Ubuntu)** | `muzik-*-linux-amd64.deb` | Ubuntu, Debian, Mint, Pop!\_OS |
| **Linux (RPM)** | `muzik-*-linux-x64.rpm` in assets | Fedora, openSUSE, RHEL |
| **Anything else** | portable `muzik-*-portable.jar` | Any OS with Java 21+ — including Linux ARM64 and Intel Macs |

> **First-run notes (unsigned builds):** macOS: right-click → *Open* the first time. Windows SmartScreen: *More info → Run anyway*. Signed builds are planned.

## What you get

Muzik Desktop is an original **Kotlin Multiplatform + Compose** app, designed and engineered by Ignatius (lead developer) together with the Pipo Team contributors. Here is what ships in the box:

- **Search the full YouTube Music catalog** — songs, videos, albums, artists, playlists and podcasts, with live suggestions as you type
- **Real streaming pipeline** — SABR and direct streams via the battle-tested `innertubex` client, with PO-token attestation (in-app WebView mint or the bundled `bgutil` provider) so playback keeps working when YouTube tightens bot checks
- **FFmpeg audio engine** — gapless playback, crossfade, speed & pitch control, loudness normalization, 15-frame prebuffer pipeline, everything decoded natively for Windows, macOS and Linux
- **Synced lyrics** — word-by-word karaoke lyrics from YouTube and multiple community providers (LRCLIB, KuGou, BetterLyrics and more), with translation and romanization
- **Your library, locally** — favorites, playlists, history and subscriptions stored in a real SQLite database, synced to your YouTube Music account when you sign in
- **Downloads & offline cache** — transcode and keep songs as tagged M4A files, or let the smart cache keep your recent music instantly replayable
- **Native desktop integration** — system media keys (SMTC on Windows, Now Playing on macOS, MPRIS on Linux), tray icon with playback controls, Windows taskbar thumbnail buttons, Discord Rich Presence
- **Queue that never stops** — endless radio, drag-to-reorder queue, play-next, sleep timer
- **Cast & Listen Together** — cast to Chromecast/DLNA/FCast devices and build shared listening sessions
- **Last.fm & ListenBrainz scrobbling**, SponsorBlock integration, backup & restore, 60+ interface languages
- **Auto-updates** — Muzik checks GitHub releases and points you straight at the right installer for your OS

## How it works

Muzik Desktop is a **Kotlin Multiplatform** app: a shared module (UI, database, YouTube repository, FFmpeg playback engine) plus a desktop shell, packaged into native installers with `jpackage` (bundled Java 25 runtime — nothing to install).

- **Linux**: playback uses your system's ALSA/PipeWire/Pulse setup; WebKitGTK (`webkit2gtk-4.1`) is needed for the in-app sign-in browser. On Debian/Ubuntu: `sudo apt install libwebkit2gtk-4.1-0`.
- **Windows**: WebView2 (preinstalled on Windows 10/11) powers sign-in; the login helper is bundled.
- **macOS**: system WebKit is used for sign-in.

> **Honest note on networks:** YouTube gates unauthenticated playback by IP reputation. On home or office Wi-Fi it just works. On VPN or datacenter exits, YouTube may answer *"Sign in to confirm you're not a bot"* — that's YouTube's policy, not a bug, and the app will tell you plainly when it happens.

## Building from source

```bash
# Prerequisites: JDK 25 (Temurin recommended)
./gradlew :desktopApp:run                      # run from source
./gradlew :desktopApp:packageReleaseDmg        # macOS DMG
./gradlew :desktopApp:packageReleaseMsi        # Windows MSI
./gradlew :desktopApp:packageReleaseDeb :desktopApp:packageReleaseRpm   # Linux packages
./gradlew :desktopApp:packageReleaseUberJarForCurrentOS                 # portable JAR
```

## Credits & license

**Lead developer:** Ignatius. **Contributors:** the Pipo Team. Streaming internals come from the `innertubex` library.

Licensed under **GPL-3.0** — see [LICENSE](LICENSE).

<div align="center">

[![Sponsor](https://img.shields.io/badge/Support_Muzik-Buy_Me_a_Soda-FF5C8A?style=for-the-badge&logo=buymeacoffee)](https://buymesoda.com/lllcuDCr25N1a1peiKOCl22Oaf13)

</div>
