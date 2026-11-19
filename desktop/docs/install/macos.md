# Install on macOS

## Download

Choose the ARM64 DMG for Apple Silicon Macs from [the download channels](README.md#get-the-package). Intel Macs run the portable JAR with Java 21 or later instead — no DMG is published for Intel. Packaging runs on macOS 15; compatibility with older systems needs platform verification. The DMG includes Java.

## Install and verify

1. [Verify the checksum](README.md#verify-a-download).
2. Open the DMG and drag Muzik to Applications.
3. Eject the image and open Muzik.
4. If macOS blocks the unsigned app, use its per-app Open Anyway option only after verifying the download. Follow [Apple's instructions](https://support.apple.com/en-us/102445).
5. Check playback, login and credential restoration after restart.

Packages are unsigned and unnotarized. Do not disable Gatekeeper globally.

## Homebrew

No official tap is published yet. `packaging/homebrew/muzik.rb.in` is the generator's cask template for the Apple Silicon DMG; when a `MuzikGroup/homebrew-tap` repository exists, render the cask with `scripts/generate_distribution.py` and submit it there. Until then, install the DMG by hand as above.

Intel Macs follow the portable JAR path from the [download table](README.md#get-the-package) and update by replacing the JAR with a verified download.

## Update and uninstall

Back up and quit before replacing the app in Applications. Deleting the app leaves profile data, Keychain credentials and music outside it. Sign out before removing credentials and preserve backups before clearing data.

## Common problems

For architecture or launch failure, confirm the DMG matches the processor and collect the [desktop log](../desktop.md#logs-and-profiling). For credential problems, check [Keychain recovery](../desktop.md#credentials-and-settings-files) before deleting files.
