# Tech Stack

- Kotlin 2.1.0, AGP 8.7.3, Java 17, Android app module only.
- SDK: minSdk 31, compileSdk/targetSdk 35, app id `com.shuli.reader`, debug suffix `.debug`, version `0.1.0`.
- UI: Jetpack Compose BOM 2024.12.01, Material3 1.3.1, Compose Navigation 2.8.5.
- Persistence: Room 2.6.1 with KSP, DataStore Preferences 1.1.1 currently used despite early docs mentioning SharedPreferences.
- Async: Kotlin Coroutines 1.9.0 + Flow. Serialization: kotlinx-serialization 1.7.3.
- Libraries: Coil Compose 2.7.0, OkHttp 4.12.0, Jsoup 1.18.3, juniversalchardet 2.4.0.
- Tests declared: JUnit4, MockK, coroutines-test. No `app/src/test` or `app/src/androidTest` directories observed.