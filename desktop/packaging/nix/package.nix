{ lib, stdenv, fetchurl, makeWrapper, openjdk21, gtk3, webkitgtk_4_1, libsecret
, dbus, glib-networking, release }:
let
  pname = "muzik";
  version = release.version;
  # The portable JAR is architecture-independent, so both x86_64 and aarch64
  # consume the same release asset pinned by packaging/release.json.
  asset = release.linux.${stdenv.hostPlatform.system};
  src = fetchurl { inherit (asset) url hash; };
  runtimeLibs = [ gtk3 webkitgtk_4_1 libsecret dbus glib-networking ];
in
stdenv.mkDerivation {
  inherit pname version src;

  nativeBuildInputs = [ makeWrapper ];
  buildInputs = runtimeLibs;

  dontUnpack = true;

  installPhase = ''
    runHook preInstall

    install -Dm644 ${src} $out/share/muzik/muzik-${version}.jar
    makeWrapper ${openjdk21}/bin/java $out/bin/muzik \
      --add-flags "-jar $out/share/muzik/muzik-${version}.jar" \
      --prefix LD_LIBRARY_PATH : "${lib.makeLibraryPath runtimeLibs}" \
      --prefix GIO_EXTRA_MODULES : "${glib-networking}/lib/gio/modules"

    runHook postInstall
  '';

  meta = {
    description = "YouTube Music desktop client";
    homepage = "https://github.com/MuzikGroup/Muzik";
    license = lib.licenses.gpl3;
    platforms = [ "x86_64-linux" "aarch64-linux" ];
    mainProgram = pname;
  };
}
