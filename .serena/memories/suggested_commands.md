# Suggested Commands

- Build debug APK on Windows: `./gradlew.bat :app:assembleDebug`.
- Run JVM unit tests when present: `./gradlew.bat :app:testDebugUnitTest`.
- Run Android instrumented tests with a connected emulator/device: `./gradlew.bat :app:connectedDebugAndroidTest`.
- Generate Room/KSP artifacts during build: same Gradle build/test commands; schemas configured under `app/schemas`.
- Inspect files quickly: `rg --files` and `rg -n "pattern" "path"` from repo root.
- Check git state: `git status --short`. Do not create commits/branches unless user explicitly asks.