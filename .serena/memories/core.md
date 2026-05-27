# Core

- Android/Kotlin native reader app: package `com.shuli.reader`, root project `ShuLi-Reader`, currently only Gradle module `:app` (`settings.gradle.kts`).
- Docs describe an eventual layered/multi-core layout, but current code is single module with packages under `app/src/main/java/com/shuli/reader`.
- Implemented areas: bookshelf UI/import flow, settings UI/preferences, Room entities/DAOs, TXT/EPUB metadata/content parser stubs, theme/i18n scaffolding.
- Missing major docs-aligned areas: reader feature package, ReaderScreen/ViewModel, paginator/renderer/PageDelegate engine, text selection/search/TTS/sync, unit/android test source sets.
- Read tech stack/build details in `mem:tech_stack`; run/check workflow in `mem:task_completion`; project conventions in `mem:conventions`.