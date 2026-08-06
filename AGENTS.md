# Muzik

Muzik is a 3rd-party YouTube Music client for Android. It follows Material 3 design guidelines closely.

## Building

```bash
./gradlew :app:assembleFossDebug
```

The generated APK is located at `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk`.

## Project structure

- `app` — the Android application (Compose, Material 3)
- `innertube` — YouTube/YouTube Music API client
- Other modules: lyrics sources, scrobbling, music recognition, etc.
- `muzzproto` — protobuf definitions for listen-together

## Contributing

Muzik is maintained by its lead developer, [Ignatius](https://github.com/Motius).

Please make sure changes compile and tests pass before opening a pull request.