# Task Completion

- Before claiming app code changes are done, run `./gradlew.bat :app:testDebugUnitTest` for JVM tests when applicable.
- For UI/Android behavior, run `./gradlew.bat :app:connectedDebugAndroidTest` only when an emulator/device is available; otherwise state that it could not be run.
- Always run `./gradlew.bat :app:assembleDebug` after production code or resource changes unless the change is docs-only.
- For docs-only changes, verify the target file exists, relevant links/paths match current repo, and `git status --short` shows only intended doc/memory changes.
- Do not commit, push, reset, or branch unless user explicitly requests it.