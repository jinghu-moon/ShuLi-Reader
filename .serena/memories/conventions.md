# Conventions

- User-facing and code comments are Chinese in current code/docs; preserve Simplified Chinese for new documentation and comments unless a file is already otherwise.
- Package naming uses `com.shuli.reader.<area>`; current single-module packages are `core`, `feature`, `ui` under `:app`.
- Compose screen functions use PascalCase, ViewModels expose `StateFlow` UI state and `SharedFlow` one-shot events.
- Current DI is a manual `ShuLiAppContainer`; docs mention Koin but implementation has not adopted it.
- Docs prioritize KISS/YAGNI/minimal dependencies, Canvas/direct View for reader core, Room/DataStore, coroutine main-safety, and testability via Clean Architecture boundaries.
- Existing parser implementations load full content; docs expect mmap/streaming, virtualized pagination, LRU caches, and async pagination for large files.