# Install Muzik

Choose your platform: [Android](android.md), [macOS](macos.md), or [Linux](linux.md). iOS and iPadOS builds are not published in stable releases; the iOS target builds locally ([ios.md](ios.md)).

## Get the package

Use [Muzik releases](https://github.com/MuzikGroup/Muzik/releases) for downloads. The [release workflow](../../.github/workflows/release.yml) publishes these packages together in one release. `VERSION` below means the version in the download's filename.

| Platform | Release asset |
| --- | --- |
| Android | `Muzik.apk` (universal FOSS build), `Muzik-with-Google-Cast.apk`, `Muzik-izzy.apk` |
| Windows x64 | `Muzik-VERSION-windows-x64.msi` plus a portable `Muzik-VERSION-windows-x64.zip` |
| macOS (Apple Silicon) | `Muzik-VERSION-macos-arm64.dmg` |
| macOS (Intel) | portable `muzik-VERSION-portable.jar` with Java 21+ |
| Linux x64 | `muzik-VERSION-linux-amd64.deb`, `muzik-VERSION-linux-x64.rpm`, portable `muzik-VERSION-portable.jar` |
| Any OS with Java 21+ | portable `muzik-VERSION-portable.jar` |

Every release also publishes `SHA256SUMS` covering all assets. Flatpak and AppImage files are not release downloads; AppImage packaging remains available locally for development (`make build-appimage`). See the [desktop reference](../desktop.md#native-packages) for local packaging.

## Verify a download

Download `SHA256SUMS` from the same release. Calculate the package's SHA-256 digest before opening it. Enter the full path when prompted.

On Linux:

```sh
printf 'Downloaded file path: '
read -r package
sha256sum -- "$package"
```

On macOS:

```sh
printf 'Downloaded file path: '
read -r package
shasum -a 256 -- "$package"
```

On Windows, use PowerShell:

```powershell
$package = Read-Host 'Downloaded file path'
Get-FileHash -Algorithm SHA256 -LiteralPath $package
```

Compare all 64 hexadecimal characters with the matching entry in the checksum file. Letter case does not matter.

A mismatch means you should download the file again. Do not install a mismatched file. If the publisher supplies no checksum, you cannot perform this comparison. A checksum confirms that the file matches the publisher's copy; it is not a code-signing certificate.

For Android, you can verify the APK on a computer before transferring it unchanged to the device. After installation, check the version in Muzik's settings, open a song and confirm audible playback. Platform guides add checks for login and signing.

## Before updating or uninstalling

Export a backup from Muzik's settings and keep it outside the installation directory or mobile app storage. Keep downloaded music separately if you need it. Do not assume a backup includes every downloaded audio file.

Use the same platform, architecture and installation method for updates. Avoid downgrading a database created by a newer version; see [downgrade safety](../crash-recovery.md). Uninstalling a mobile app deletes its local data. Removing a desktop application can leave settings, credentials, caches and downloaded music behind.

If installation or startup still fails, include the package filename, app version, OS version, CPU architecture and exact error in a report to the [issue tracker](https://github.com/MuzikGroup/Muzik/issues). For desktop failures, collect the [application log](../desktop.md#logs-and-profiling). Remove account details and tokens before sharing logs.
