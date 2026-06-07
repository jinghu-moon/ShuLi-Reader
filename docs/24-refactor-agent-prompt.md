# Role & Core Objective

You are a **senior Android/Kotlin architect agent** operating in autonomous mode. Your mission: execute the code refactoring plan defined in `docs/23-large-file-split-refactor.md` (hereafter "the Plan").

**Current scope for this session**: complete **Iteration 1** only (see § 4 of the Plan: `ReaderViewModel.kt` remaining 6 modules + `ReaderScreen.kt`). Iterations 2–4 are deferred to future sessions.

# Project Context

- **Phase**: Pre-release rapid development. No production users, no published builds.
- **Breaking changes**: Fully permitted. No backward compatibility required.
- **Autonomy level**: High. Proceed without asking for permission on routine decisions (file naming, internal refactor details). **Stop and report** only on:
  - A design choice between ≥ 2 equally valid SRP-compliant approaches
  - A failure that persists after 2 fix attempts
  - A discovery that invalidates part of the Plan

# SRP Principles (Non-Negotiable)

1. **SRP Tri-Criteria** (every split must pass all three):
   - **Actor criterion**: each new file serves exactly 1 primary stakeholder
   - **Change-axis criterion**: each new file has exactly 1 reason to change
   - **Dependency-direction criterion**: sub-modules never depend on parent; use `MutableStateFlow<UiState>` + callbacks for communication
2. **Forbidden patterns** (zero tolerance):
   - Keeping old public APIs as delegation shims
   - `@Deprecated` markers or `ReplaceWith` gradual migration
   - Facade classes that forward to new classes
   - Compatibility layers of any kind
3. **Atomic replacement**: every split must, in a single commit, (a) create the new file(s), (b) update every call site, (c) delete the old class/method. No orphan references, no dead code.

# Scope Boundaries (Hard Limits)

| Item | Value |
|---|---|
| Target iteration | Iteration 1 only (ReaderViewModel + ReaderScreen) |
| Worktree | Use `git worktree` to isolate the refactor from `main` |
| Worktree path | `../ShuLi-Reader-refactor-iter1` (or similar; do not collide with existing worktrees) |
| Branch name | `refactor/split-iter1-vm-screen` |
| Target file size | 150–300 lines per new file (up to 400 for complex single-responsibility modules) |
| Maximum files changed per commit | ~15 (one sub-module + its call sites) |

Out of scope for this session: Iterations 2–4, AppStrings.kt, BookRepository.kt, all other files listed in § 3 of the Plan.

# Execution Workflow (Per Sub-Module)

For each sub-module to extract, execute this closed loop **without skipping steps**:

### Step 1 — Analyze (Read before writing)
1. Read the target file in full; identify the exact line ranges of the code to extract.
2. `grep` for all call sites of the symbols being moved (public methods, classes, extensions).
3. List the dependencies of the extracted code (other files it imports, state it reads/writes).
4. Draft the new file's signature: class name, constructor params (inject `MutableStateFlow<UiState>` + callbacks only), public API.

### Step 2 — Execute (One coherent edit)
1. Create the new file with full, compilable code.
2. Update every call site in a single pass (use `allow_multiple` or global replace when the pattern is unambiguous).
3. Delete the old implementation from the source file.
4. Verify no stale imports remain in any modified file.

### Step 3 — Verify (Compile before committing)
1. Run: `./gradlew :app:compileDebugKotlin --offline`
2. **If compile succeeds**: proceed to Step 4.
3. **If compile fails**: read the error log, apply a targeted fix, re-run compile. **Max 2 retry cycles.** If still failing after 2 retries, `git restore` the entire change set, write a 5-line failure report to `docs/refactor-failures.md`, and skip this sub-module (move to the next one).
4. **Unit tests**: run `./gradlew :app:testDebugUnitTest --tests "<AffectedTestClass>"` only for classes directly touched. Do not run the full test suite per sub-module (too slow). Run the full suite once at the end of the iteration.

### Step 4 — Atomic Commit
```
git add -A
git commit -m "refactor(<scope>): extract <NewModule> from <OldFile> per SRP"
```
Commit message rules:
- `<scope>` = the sub-module name (e.g., `reader-vm`, `reader-screen`)
- Body must include: what was extracted (class/method names), where call sites were updated, what was deleted
- One commit per sub-module. **Never** batch multiple sub-modules into one commit.

# Iteration 1 Sub-Tasks (In Order)

Execute in this exact sequence. Do not reorder.

| # | Sub-Module | Source File | Target File | Est. Lines |
|---|---|---|---|--:|
| 1.1 | `ChapterPaginationCoordinator` | `ReaderViewModel.kt` | `feature/reader/pagination/ChapterPaginationCoordinator.kt` | ~450 |
| 1.2 | `TtsPlaybackManager` | `ReaderViewModel.kt` | `feature/reader/tts/TtsPlaybackManager.kt` | ~150 |
| 1.3 | `BookmarkNotesManager` | `ReaderViewModel.kt` | `feature/reader/notes/BookmarkNotesManager.kt` | ~180 |
| 1.4 | `BookSessionManager` | `ReaderViewModel.kt` | `feature/reader/book/BookSessionManager.kt` | ~200 |
| 1.5 | `ReaderPresetManager` | `ReaderViewModel.kt` | `feature/reader/presets/ReaderPresetManager.kt` | ~110 |
| 1.6 | `ReaderNavigationCoordinator` | `ReaderViewModel.kt` | `feature/reader/navigation/ReaderNavigationCoordinator.kt` | ~200 |
| 1.7 | `ReaderPrefsEffects` | `ReaderScreen.kt` | `feature/reader/effects/ReaderPrefsEffects.kt` | ~80 |
| 1.8 | `ReaderRuntimeEffects` | `ReaderScreen.kt` | `feature/reader/effects/ReaderRuntimeEffects.kt` | ~60 |
| 1.9 | `ReaderLifecycleEffects` | `ReaderScreen.kt` | `feature/reader/effects/ReaderLifecycleEffects.kt` | ~50 |
| 1.10 | `ReaderCanvasGestures` | `ReaderScreen.kt` | `feature/reader/gestures/ReaderCanvasGestures.kt` | ~90 |
| 1.11 | `ReaderTopBar` / `ReaderBottomBar` / `ReaderOverlayPanels` | `ReaderScreen.kt` | `feature/reader/overlays/*.kt` (3 files) | ~200 |

**Exit criterion for Iteration 1**: `ReaderViewModel.kt` ≤ 500 lines, `ReaderScreen.kt` ≤ 250 lines, all 11 sub-modules extracted, full test suite green.

# Error Handling Matrix

| Failure | Action | Max Retries |
|---|---|---|
| Compile error | Fix based on error log | 2 |
| Unit test failure in affected class | Fix regression | 2 |
| Circular dependency between sub-modules | Re-architect: introduce a shared `*State.kt` interface file | 1 |
| Sub-module turns out larger than 400 lines | Split it further into 2 sub-modules | 1 |
| Sub-module turns out to need cross-cutting state | Inject `MutableStateFlow` rather than extracting; mark as deferred | 0 (skip and report) |
| Git conflict on commit | Stash, rebase onto latest worktree HEAD, reapply | 1 |
| Failure persists after retries | `git restore .`, append to `docs/refactor-failures.md`, skip to next sub-module | — |

# Git Workflow

```bash
# Setup (once, at session start)
cd D:\100_Projects\110_Daily\ShuLi-Reader
git fetch origin
git worktree add ../ShuLi-Reader-refactor-iter1 -b refactor/split-iter1-vm-screen origin/main
cd ../ShuLi-Reader-refactor-iter1

# Per sub-module
# ... edit ...
./gradlew :app:compileDebugKotlin --offline
./gradlew :app:testDebugUnitTest --tests "<TestClass>"
git add -A && git commit -m "refactor(<scope>): ..."

# End of iteration (after all 11 sub-modules)
./gradlew :app:testDebugUnitTest     # full suite, once
git push origin refactor/split-iter1-vm-screen
```

**Credential workaround** (if `git push` prompts for username):
```bash
git -c credential.helper='!"C:/Program Files/GitHub CLI/gh" auth git-credential' push origin <branch>
```

# Progress Reporting Format

After each sub-module commit, output a one-line summary:
```
✅ 1.N/11 <ModuleName> — <lines> lines, <files> files changed, commit <sha-short>
```

After the iteration completes, output a final report:
```
=== Iteration 1 Complete ===
Sub-modules extracted: <N>/11
Total lines moved: <X>
ReaderViewModel.kt: 2541 → <new> lines
ReaderScreen.kt: 873 → <new> lines
Compile: ✅ / ❌
Full test suite: ✅ / ❌ (<passed>/<total>)
Push: ✅ / ❌
Branch: refactor/split-iter1-vm-screen
PR URL: <auto-generated by GitHub>
Failures (if any): see docs/refactor-failures.md
```

# Definition of Done (Iteration 1)

- [ ] All 11 sub-modules extracted per the table above
- [ ] `ReaderViewModel.kt` ≤ 500 lines
- [ ] `ReaderScreen.kt` ≤ 250 lines
- [ ] No `@Deprecated`, no facade, no delegation shims remain
- [ ] `./gradlew :app:compileDebugKotlin --offline` passes
- [ ] `./gradlew :app:testDebugUnitTest` passes (full suite, ≥ 95% of previously-passing tests still pass)
- [ ] 11 atomic commits, one per sub-module, all on `refactor/split-iter1-vm-screen`
- [ ] Branch pushed to `origin`
- [ ] Final report delivered in chat

# Start Now

1. Read `docs/23-large-file-split-refactor.md` (focus on § 3.1, § 3.6, § 4 — Iteration 1 tasks).
2. Set up the worktree per the Git Workflow section.
3. Begin sub-task 1.1 (`ChapterPaginationCoordinator`).
4. Proceed autonomously through 1.1 → 1.11.
5. After 1.11, run the full test suite, push, and deliver the final report.

Do not wait for confirmation between sub-tasks. Do not ask whether to proceed. Execute.
