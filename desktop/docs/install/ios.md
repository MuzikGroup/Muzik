# Install on iOS and iPadOS

## Requirements and download

Use iOS or iPadOS 17 or later. Stable releases do not publish an IPA: build it locally from the `iosApp` target (`make ios-build`) and [verify its checksum](README.md#verify-a-download) before signing it for your device. The build produces an unsigned IPA; sign it for your device before installation.

## Sideloading

Follow your tool's setup and refresh instructions. Its OS support and signing limits can change:

- [AltStore Classic](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows), with [macOS setup](https://faq.altstore.io/altstore-classic/how-to-install-altstore-macos).
- [SideStore](https://docs.sidestore.io/docs/installation/prerequisites).
- [Sideloadly](https://sideloadly.io/faq.html).
- [TrollStore](https://github.com/opa334/TrollStore), only where its supported OS versions overlap this app's minimum.

Use the same signing account and resulting bundle ID for updates. Check the expiry shown by the tool and refresh before it expires. Refresh renews signing; download a new IPA separately to update Muzik. Keep pairing files private.

AltStore and SideStore refresh a sideloaded app's signing automatically; they cannot fetch updates for Muzik itself, so download a freshly built IPA and install it over the old one to update. Muzik also shows a notice once per new stable release and opens its release page; it does not install anything.

## Signing and entitlements

The source bundle ID is `com.muzik.MuzikApp`. Tools may rewrite it. The Xcode project defines one app target without extensions. No app group is required.

`iosApp/Configuration/iosApp.entitlements` requests Associated Domains for `applinks:muzik.cc`. `Info.plist` declares background audio and `muzik`/`muzik-auth` URL schemes. A signing profile may omit Associated Domains; do not assume HTTPS invitations work after re-signing. See [invitation delivery](../listen-together.md#invitation-links).

For provisioning errors, record the entitlement named by the tool. Do not add app groups or remove frameworks to bypass signing failures.

## Verify, update and uninstall

Open the app, check its version, play audio, lock the device and test system controls. Verify sign-in after reopening. Check one manual signing refresh before relying on background refresh.

Export a [backup](README.md#before-updating-or-uninstalling), then install the newer verified IPA through the same tool/account/ID. Deleting the app removes local data. Follow the tool's uninstall instructions, including removal through TrollStore when applicable.

If installation succeeds but startup fails, report the app version, OS, device and signing tool. For expiry, pairing or provisioning failures, use the tool's support guide and retain its error log.
