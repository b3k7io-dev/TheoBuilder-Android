# TheoBuilder — Android source MVP

Нативный Kotlin/Jetpack Compose **исходный проект**, не APK. В доступном sandbox нет JDK, Gradle, Android SDK и удалённого AndroidHarness; сборка и запуск не выполнены.

Реализованный срез: Compose/Material 3 + Dynamic Color, вкладки Home/Projects/Library/Settings, проекты на устройстве в SharedPreferences JSON (создание/открытие/дублирование/переименование/подтверждаемое удаление), data-driven форма из JSON-схемы, PNG import через SAF в private storage без обработки, локальная проверка и ZIP через SAF (`plugin.manifest`, `drafts.json`, PNG).

Схема намеренно ограничена `park`, `decoration`, `residential` — только примерами, подтверждёнными учебными страницами TheoTown. Это не исчерпывающий API. Нет Room/DataStore, Lua/JSON editor, sprite-sheet/crop/pixel-grid/animation, остальных draft types, sync, device/game testing. Tutorial pages могут быть старыми; совместимость архива с актуальной версией игры **не проверялась**. `water`/`airport` не объявляются как object type без подтверждения.

Источники: `https://doc.theotown.com/`; `https://forum.theotown.com/viewtopic.php?p=95033`; `https://forum.theotown.com/viewtopic.php?p=49469`; `https://forum.theotown.com/viewtopic.php?t=10950`. Все schema fields carry their source in `app/src/main/assets/api-schema.json`.

Сборка: требуется JDK 17+, Android SDK platform 36/Build Tools и интернет для Gradle/Maven. Android Studio: открыть проект. POSIX: `./gradlew assembleDebug`. Вложенный лёгкий `gradlew` bootstrap (не стандартный wrapper) скачивает Gradle 9.6.0 с официального `services.gradle.org` и проверяет SHA-256 sidecar; Android SDK ставится отдельно. Ожидаемый APK path после успешной сборки: `app/build/outputs/apk/debug/app-debug.apk` — APK в этом артефакте отсутствует. `tools/validate_project.py` — структурная проверка, не компиляция Kotlin и не проверка в игре.
