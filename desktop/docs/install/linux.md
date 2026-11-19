# Install on Linux

## Requirements and download

Stable releases ship `muzik-VERSION-linux-amd64.deb` and `muzik-VERSION-linux-x64.rpm` for x86_64, plus the architecture-independent `muzik-VERSION-portable.jar` for any distribution with Java 21 or later. The packaging workflow builds on Ubuntu 24.04; that does not establish compatibility with every older distribution. The DEB and RPM packages include Java and FFmpeg.

Check your architecture with `uname -m` (`x86_64`). Get the matching package through [the download channels](README.md#get-the-package).

Use X11 or XWayland. Login needs GTK3/WebKitGTK 4.1. Saved credentials need Secret Service, libsecret, `secret-tool` and `dbus-send`. Audio needs ALSA integration with your audio server. See [Linux setup](../desktop.md#linux-login) and [audio setup](../desktop.md#linux-audio-setup). Startup rejects Omarchy, detected from its packages, install directories, environment, pacman repository or `os-release`, and links to [stopomarchy.org](https://stopomarchy.org/).

## Install and verify

1. [Verify the checksum](README.md#verify-a-download).
2. Install the system package with your distribution's tooling:

```sh
sudo apt install ./muzik-VERSION-linux-amd64.deb
```

or on RPM-based distributions:

```sh
sudo dnf install ./muzik-VERSION-linux-x64.rpm
```

3. Launch Muzik from your application menu, or run the portable JAR directly:

```sh
java -jar muzik-VERSION-portable.jar
```

Check playback, sign-in and retained credentials after restart.

## Nix

The repository flake builds the architecture-independent release JAR with OpenJDK 21 for x86_64 and aarch64 Linux, pinned by `packaging/release.json`. Run it once, or install it into your profile:

```sh
nix run github:MuzikGroup/Muzik
nix profile install github:MuzikGroup/Muzik
```

To use it from a NixOS or Home Manager configuration, add `github:MuzikGroup/Muzik` as an input and include `inputs.muzik.packages.${pkgs.stdenv.hostPlatform.system}.default` in your packages. The flake follows the latest stable release; run `nix flake update muzik` or `nix profile upgrade` to update.

## Update and uninstall

Back up, quit, then install the new DEB or RPM over the old one, or replace the portable JAR with a verified download. Removing the packages leaves profile data and music outside them; the portable JAR keeps everything next to itself. Sign out first to remove saved sessions, and preserve backups before clearing data.

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
