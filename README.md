# Branch Assistant

Android-голосовой ИИ-ассистент с **ветвящимися беседами**. Нативно на Kotlin +
Jetpack Compose, работает с **MiniMax API** (OpenAI-совместимый протокол, стриминг
по SSE).

## Что умеет

- **Текстовый чат с деревом сообщений** — любое сообщение можно форкнуть,
  переключаться между вариантами ответа (`← N/M →`), регенерировать.
- **Стриминг токенов в реальном времени** (SSE, OpenAI-compatible).
- **Голосовой ввод** через системный `SpeechRecognizer` (частичные результаты).
- **Голосовой вывод** через системный `TTS` (ru-RU по умолчанию). Можно
  озвучить отдельную реплику (▶ у каждого сообщения) или всю активную ветку.
- **Jev-роутинг**: пользователь сам задаёт список **моделей** и список
  **маршрутов** (тип запроса → модель). Jev-классификатор на каждый
  запрос выбирает подходящий маршрут; ключ Jev опционален, без него берётся
  первая модель из списка.
- **Скользящее окно контекста** — в LLM уходят только последние N сообщений
  активной ветки (настраивается в Настройках, дефолт 40). Защищает от
  взрыва стоимости в длинных беседах.
- **Локальная БД** (Room) — все беседы и ветки сохраняются, бэкапятся в
  `/data/data/com.assistant.branch/`. API-ключи — в **зашифрованном**
  SharedPreferences (Android Keystore, AES-256-GCM).
- Тёмная тема, edge-to-edge Compose UI.

## Архитектура

```
app/src/main/java/com/assistant/branch/
├── AssistantApp.kt              # DI singleton (db, repo, settings, engines)
├── MainActivity.kt              # Compose host, edge-to-edge
│
├── data/
│   └── db/
│       ├── Conversation.kt      # беседа
│       ├── Message.kt           # узел: parentId → дети
│       ├── ConversationDao.kt
│       ├── MessageDao.kt
│       └── AppDatabase.kt       # Room
│
├── network/
│   ├── ApiDtos.kt               # ChatRequest/Response/Delta
│   ├── ApiClient.kt             # OpenAI-compatible SSE через OkHttp
│   ├── JevDtos.kt               # Jev request/response
│   └── JevClient.kt             # Jev /v1/systemone
│
├── repo/
│   ├── ChatRepository.kt        # бизнес-логика: send, regenerate, compress
│   ├── JevRouter.kt             # intent → модель
│   ├── JevHistoryStore.kt       # история Jev (JSON в filesDir)
│   └── LruCache.kt              # (резерв)
│
├── settings/
│   ├── AssistantSettings.kt     # DataStore + модели/маршруты/Jev
│   └── SecureSettings.kt        # EncryptedSharedPreferences для ключей
│
├── voice/
│   ├── SttEngine.kt             # SpeechRecognizer wrapper
│   └── TtsEngine.kt             # TextToSpeech wrapper
│
└── ui/
    ├── theme/                   # цвета, палитры, типографика
    ├── HomeScreen.kt            # drawer + bottom-nav (Чат / Jev / История / Настройки)
    ├── ChatScreen.kt            # основной экран чата
    ├── ConversationListScreen.kt
    ├── SettingsScreen.kt        # модели +, маршруты +
    ├── BranchTreeView.kt        # линейный рендер дерева + SiblingSwitcher
    ├── BranchCanvasTree.kt      # canvas-дерево с pan/resize
    └── BranchNodeInfo.kt
```

## Сборка и запуск

### Требования

- Android Studio Koala или новее
- JDK 17 (или 21)
- Android SDK 34 / Build-Tools 35
- Реальное устройство Android 8.0+ (API 26)

### Шаги

1. `local.properties` в корне:

```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

(или `ANDROID_HOME`).

2. Сгенерируй Gradle wrapper (один раз):

```
gradle wrapper --gradle-version 9.3.1
```

3. Собери debug-APK:

```
./gradlew :app:installDebug
```

4. Запусти приложение → **Настройки** → введи MiniMax API ключ.
   При желании — Jev API ключ и модели.

## Настройка MiniMax

1. https://platform.MiniMax.io (или https://api.minimax.chat) → API Keys.
2. Создай ключ, скопируй.
3. В приложении: **Настройки → Чат API → API ключ** — вставь.
4. **Модели**: список имён (`+` добавляет, X удаляет). Дефолт `MiniMax-M3`.
5. **Маршруты**: имя + описание (инструкция для Jev) + ссылка на модель.

## Как работает ветвление

- Сообщения хранятся как дерево: у каждого `Message` есть `parentId`.
- Активная ветка (`activePath` в UI-стейте) — цепочка id от корня до листа.
- Чтобы форкнуть ответ: нажми **«ветка → перегенерировать»** под сообщением
  ассистента. Старый узел удаляется, новый ответ пишется в новую ветку.
  Рядом с сообщением появляется переключатель «←/→» для навигации между
  вариантами одного уровня.

## Технические заметки

- STT использует `SpeechRecognizer` от Google (`RECORD_AUDIO` обязательно).
- TTS — системный `TextToSpeech` с локалью `ru-RU` (можно поменять в
  настройках: BCP-47 тег, например `en-US`).
- MiniMax API — OpenAI-совместимый; эндпоинт
  `https://api.minimax.chat/v1/chat/completions` (или свой). SSE: `data: {...}`
  чанки, терминатор `[DONE]`.
- База — `/data/data/com.assistant.branch/databases/branch_assistant.db`.
- API-ключи — `/data/data/com.assistant.branch/shared_prefs/secure_prefs.xml`
  (зашифровано через Android Keystore). Не попадают в бэкапы.

## Известные ограничения / TODO

- Нет экспорта/импорта бесед.
- Нет полноценного markdown-рендера (только inline `**bold**`, `*italic*`, `` `code` ``).
- Streaming-индикатор иногда на доли секунды отстаёт от листа.
- Регенерация всегда удаляет выбранный узел и создаёт новый; нет
  «сравнить варианты».
- Нет интеграции с системным ассистентом Android
  (`VoiceInteractionService` — запланировано, не реализовано).

## Лицензия

MIT — делай что хочешь.