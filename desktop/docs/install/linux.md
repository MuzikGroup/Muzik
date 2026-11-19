# Install on Linux

## Requirements and download

Release AppImages target x64 and ARM64 on glibc-based distributions. The packaging workflow builds on Ubuntu 24.04; that does not establish compatibility with every older distribution. Packages include Java and FFmpeg.

Choose x64 for `uname -m` output `x86_64`, or ARM64 for `aarch64`. Get the matching package through [the download channels](README.md#get-the-package).

Use X11 or XWayland. Login needs GTK3/WebKitGTK 4.1. Saved credentials need Secret Service, libsecret, `secret-tool` and `dbus-send`. Audio needs ALSA integration with your audio server. See [Linux setup](../desktop.md#linux-login) and [audio setup](../desktop.md#linux-audio-setup). Startup rejects Omarchy, detected from its packages, install directories, environment, pacman repository or `os-release`, and links to [stopomarchy.org](https://stopomarchy.org/).

## Install and verify

1. [Verify the checksum](README.md#verify-a-download).
2. Move the AppImage to a directory you own and keep its filename for the commands below.
3. Mark it executable and run it:

```sh
chmod u+x -- ./Muzik.AppImage
./Muzik.AppImage
```

Substitute the downloaded filename. Check playback, sign-in and retained credentials after restart.

On Arch Linux, install the community-maintained [`muzik-desktop-bin`](https://aur.archlinux.org/packages/muzik-desktop-bin) package, which wraps the x86_64 release AppImage, for example with `yay -S muzik-desktop-bin`. Update it through your AUR helper instead of replacing the AppImage.

## Homebrew

On x86_64 or ARM64 Linux with [Homebrew](https://brew.sh), the [Muzik tap](https://github.com/MuzikGroup/homebrew-tap) unpacks the release AppImage, so FUSE is not needed:

```sh
brew trust muzikgroup/tap
brew install muzikgroup/tap/muzik
muzik
```

Homebrew only installs from trusted taps, so `brew trust` is needed once.

`brew info muzikgroup/tap/muzik` shows how to add the desktop entry to your application menu. Update with `brew upgrade muzik`.

## Nix

The repository flake wraps the release AppImage for x86_64 and aarch64 Linux. Run it once, or install it into your profile:

```sh
nix run github:MuzikGroup/Muzik
nix profile install github:MuzikGroup/Muzik
```

To use it from a NixOS or Home Manager configuration, add `github:MuzikGroup/Muzik` as an input and include `inputs.muzik.packages.${pkgs.stdenv.hostPlatform.system}.default` in your packages. The flake follows the latest stable release; run `nix flake update muzik` or `nix profile upgrade` to update.

## Flatpak

[flatpark](https://flatpark.org/apps/com.muzik.desktop/) repackages the release AppImage as the Flatpak `com.muzik.desktop` for x86_64 and aarch64:

```sh
flatpak --user remote-add --if-not-exists flatpark https://dl.flatpark.org/flatpark.flatpakrepo
flatpak --user install flatpark com.muzik.desktop
flatpak run com.muzik.desktop
```

Inside the sandbox, the download folder can only be Downloads or Music. Update with `flatpak update`.

## Update and uninstall

Back up, quit, then replace the AppImage with a verified download of the same architecture. Deleting an AppImage leaves profile data and music outside it. Sign out first to remove saved sessions, and preserve backups before clearing data.

## Common problems

For silent playback, check [ALSA routing](../desktop.md#linux-audio-setup). For blank login, check WebKitGTK and XWayland. For forgotten sign-in, check the unlocked Secret Service collection and [credential recovery](../desktop.md#credentials-and-settings-files). Use the [application log](../desktop.md#logs-and-profiling) to report remaining failures.

### Sonos and DLNA with firewalld

Discovery sends SSDP searches to `239.255.255.250:1900`. Speakers reply from UDP port 1900 to a temporary local UDP port. Allowing only incoming destination port 1900 does not allow these replies. Muzik serves cast audio on TCP port `57643` for the entire session, including song changes. This applies to Google Cast and FCast too. The app reports an error if it cannot bind this port or if the receiver has not requested the stream after 15 seconds of requested playback. A missing request can indicate a firewall, routing or receiver problem.

Check the Wi-Fi or Ethernet interface's zone with `firewall-cmd --get-active-zones`. For a speaker at `192.168.8.50` in the `public` zone, allow its discovery replies and audio requests:

```sh
sudo firewall-cmd --zone=public --add-rich-rule='rule family="ipv4" source address="192.168.8.50/32" source-port port="1900" protocol="udp" accept'
sudo firewall-cmd --zone=public --add-rich-rule='rule family="ipv4" source address="192.168.8.50/32" port port="57643" protocol="tcp" accept'
```

Replace the example IP and zone with your speaker and network. Repeat for each speaker, including members of a Sonos group that fetch audio. Reopen the cast picker after changing the rules. Once discovery and playback work, repeat the same commands with `--permanent` to keep the rules after a restart. See the [firewalld rich rule reference](https://firewalld.org/documentation/man-pages/firewalld.richlanguage.html) for address and port matching. mDNS alone does not discover DLNA or Sonos devices in Muzik.
