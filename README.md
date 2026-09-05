# Kira Companion

Kira is a virtual AI companion for Android: a small, animated character who lives on
top of your other apps, reacts to taps, shows emotions, and chats with you.

<p>
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-8B5CF6" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF" alt="Kotlin">
</p>

> Kira ships with real character artwork in `app/src/main/assets/kira/emotions/` (14 poses,
> circular avatar chips) plus a built-in, dependency-free procedural illustration as a
> fallback (see [Changing Kira's appearance](#changing-kiras-appearance)), so the app is
> fully buildable and runnable even if the art files are ever removed.

## Two independent halves, on purpose

- **Kira herself — fully local, no API key, ever.** The overlay, dragging, emotions,
  animations, random reactions, settings, position memory, and local chat history all work
  completely offline. Nothing about *being Kira* requires an account, a key, or a network call.
- **Real AI conversation — via the official ChatGPT app/website.** This app never logs into
  ChatGPT, never reads its cookies/session/tokens, and never treats a ChatGPT subscription as
  an API. It just opens `https://chatgpt.com/` (the app if installed, the browser otherwise)
  through a normal `Intent.ACTION_VIEW` when you ask to chat for real. An optional, fully
  separate "Custom API" mode lets power users bring their own OpenAI-compatible key if they
  want in-app AI replies instead — never required.

## Features

- **Floating overlay bubble** — Kira renders on top of other apps via `SYSTEM_ALERT_WINDOW`,
  as a small draggable, tappable character.
- **Drag & dock** — drag Kira anywhere; on release she snaps to the nearest screen edge and
  remembers her position (and size) across restarts.
- **14-emotion system** — `IDLE`, `HAPPY`, `LOVE`, `SHY`, `THINKING`, `SURPRISED`, `SAD`,
  `ANGRY`, `SLEEPY`, `EXCITED`, `CONFUSED`, `WINK`, `LAUGHING`, `CRYING`, each with its own
  artwork/look, auto-return-to-idle timing, and eligibility for spontaneous "random reactions".
- **Local, rule-based emotion engine** — no ML model needed for v1: Russian/English keyword
  matching, emoji, repeated-character laughter patterns ("хаха", "lol"), and punctuation
  heuristics (`!`, `?`, ALL CAPS) pick an emotion from what you type, in a fixed priority order
  so one phrase never produces a random/conflicting result. The interface (`EmotionClassifier`)
  is swappable for a real AI-based classifier later.
- **Smooth emotion transitions** — Compose `AnimatedContent` cross-fades + scales between
  expressions instead of ever swapping images instantly.
- **Idle/interaction animations** — gentle sway, periodic blinking, a tap "pop" reaction, a
  thinking wobble while Kira waits for a reply, and small floating accents (hearts, tears,
  "Zzz", "!", "…") layered on top of whichever face art is showing.
- **Random spontaneous reactions** — one setting with four levels: Off / Low / Normal / High,
  so Kira occasionally reacts on her own without ever becoming annoying.
- **Long-press menu** — Open ChatGPT / Emotion (debug cycle) / Settings / Hide Kira / Close app.
- **Full chat screen** — bubbles, avatar, "Kira is typing…" indicator, multiline + emoji + full
  Unicode/Cyrillic input, persisted history (JSON file on disk), and a "clear history" action —
  shown only in Local Demo / Custom API mode; in ChatGPT mode it's replaced by a single
  "Open ChatGPT" card.
- **Swappable AI backend** — `AiProvider` interface with a `LocalDemoAiProvider` (fully offline,
  on-brand canned replies, for trying out the UI/emotion engine with zero setup) and an
  `OpenAiProvider` for the optional, opt-in Custom API mode.
- **Emotion carries context** — Kira's expression factors in the latest message *and* her
  previous state (`EmotionController`), so a sad message followed by good news correctly shifts
  her mood.
- **Background reply notifications** — optional local notification if Kira's reply arrives after
  you've left the app (Local Demo / Custom API modes only).
- **Full settings screen** — overlay on/off, random reactions Off/Low/Normal/High, Kira's size,
  sound, notifications, light/dark/system theme, AI provider (ChatGPT / Local Demo / Custom API)
  + optional API key, clear history, reset position, about.
- **Accessible & themeable** — content descriptions on the avatar, Material 3 dark/light/system
  theming throughout.

## Requirements

- Android 8.0 (API 26) or newer.
- To build: JDK 17, Android SDK (platform 34, build-tools 34.0.0). Using Android Studio
  Koala/Ladybug or newer will fetch these automatically.

## Installing the app

1. Download the debug APK (see [Where to find the APK](#where-to-find-the-apk) below).
2. On your phone: enable "Install unknown apps" for the source you used (browser/file manager)
   if prompted, then open the APK to install it.
3. Launch **Kira**.

### Granting the overlay permission

The first thing the app shows is a screen explaining that Kira needs the **"Display over other
apps"** permission to appear on top of other apps. Tap **"Разрешить отображение поверх других
приложений"** — this opens Android's system settings for the app; turn the toggle on and go back.
The app re-checks the permission automatically when you return to it.

Once granted, flip the **"Кира на экране"** switch on the Home screen (or in Settings) to start
the floating overlay. A low-priority, non-intrusive notification stays visible while she's active
(Android requires this for any always-on foreground service) — it does nothing but let you jump
back into the app.

### Configuring AI (optional)

Kira launches in **ChatGPT mode** by default — no setup, no network, no API key of any kind.
Every companion feature (overlay, emotions, chat UI's own history/engine) already works before
you touch this setting; it only decides what "real AI conversation" means:

- **ChatGPT** (default) — the chat screen shows a single "Open ChatGPT" button that hands off to
  the official app/website via a plain `Intent.ACTION_VIEW`. This app never logs in, never reads
  ChatGPT's cookies/session/tokens, and a ChatGPT subscription is never treated as API access.
- **Local Demo** — a fully offline canned-response generator (`LocalDemoAiProvider`) so you can
  try the full chat UI and emotion engine with zero setup.
- **Custom API** — optional, opt-in: paste your own OpenAI-compatible API key for real in-app
  replies. Never required to use any other part of the app.

To switch: **Settings → Настройка ИИ → Источник ответов**, then (for Custom API only) paste your
key and tap Save.

The key is stored **encrypted on-device** (Android Keystore-backed `EncryptedSharedPreferences`)
and is only ever sent directly to your configured API endpoint over HTTPS with your requests —
never bundled into the APK, never committed to this repository, never sent anywhere else.

> **Security note:** storing a raw API key on a client device is inherently less safe than a
> server-side proxy — a compromised/rooted device could still extract it from the running
> process. This is an acceptable tradeoff for a personal-use, opt-in feature. For a production/
> public release, put your own backend between the app and the API: the app authenticates to
> *your* server, and only your server holds the real key.

## Building locally

```bash
git clone <this-repo-url>
cd Kira-Companion
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Useful commands:

```bash
./gradlew test           # unit tests (emotion engine/controller, JVM-only)
./gradlew lint           # Android lint
./gradlew assembleDebug  # debug APK
./gradlew assembleRelease  # release APK (falls back to debug signing without secrets, see below)
```

### Release signing

Release builds are unsigned-by-default-safe: `app/build.gradle.kts` only applies a real release
`signingConfig` when the `KIRA_RELEASE_STORE_FILE` environment variable is set; otherwise
`assembleRelease` transparently falls back to debug signing so you always get an installable APK.

To sign real releases:

- **Locally:** copy [`gradle.properties.example`](gradle.properties.example) for the documented
  env vars, export `KIRA_RELEASE_STORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` in
  your shell, then run `./gradlew assembleRelease`.
- **In CI:** add these **GitHub Actions secrets** to the repository (Settings → Secrets and
  variables → Actions):
  - `KEYSTORE_BASE64` — your `.keystore`/`.jks` file, base64-encoded (`base64 -w0 release.keystore`)
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`

  The [release workflow](.github/workflows/release.yml) decodes and uses them automatically —
  never commit a real keystore, password, or API key to this repository.

## Building via GitHub Actions

Every push and pull request runs [`.github/workflows/build.yml`](.github/workflows/build.yml):
unit tests → lint → `assembleDebug`, then uploads the debug APK as a workflow artifact named
**`kira-companion-debug-apk`**.

Pushing a tag matching `v*` (e.g. `v1.0.0`) additionally runs
[`.github/workflows/release.yml`](.github/workflows/release.yml), which builds a release APK and
publishes it as a **GitHub Release** with the APK attached.

### Where to find the APK

- **From a normal push:** GitHub repo → **Actions** tab → the latest **Build** run → **Artifacts**
  section at the bottom → download `kira-companion-debug-apk`.
- **From a version tag:** GitHub repo → **Releases** → the corresponding release → download the
  attached `.apk` directly.

## Architecture

```
app/src/main/java/com/kira/companion/
├── MainActivity.kt            # Compose host, deep-links from the overlay menu into Chat/Settings
├── KiraApplication.kt         # Manual service locator (settings, chat store, AI, emotion controller)
├── KiraViewModelFactory.kt    # Tiny ViewModel factory (no DI framework needed at this size)
├── model/                     # KiraEmotion, ChatMessage, app-wide enums (size/theme/frequency/...)
├── emotion/                    # EmotionEngine (rule-based classifier) + EmotionController (state machine)
├── ai/                         # AiProvider interface, LocalDemoAiProvider, OpenAiProvider, factory, openChatGpt()
├── data/                       # SettingsRepository (DataStore), ChatHistoryStore (JSON file), SecureKeyStore
├── image/                      # KiraImageProvider — assets/kira/emotions/*.png override lookup
├── overlay/                    # OverlayService (WindowManager + foreground service), gestures, menu UI
├── notifications/              # NotificationHelper (overlay + chat-reply notifications)
├── navigation/                 # Bottom-nav Compose NavHost (Home / Chat / Settings)
└── ui/
    ├── theme/                  # Material 3 theme (purple/pink palette, light+dark)
    ├── components/             # KiraAvatar + KiraFace (the built-in animated illustration)
    ├── home/, chat/, settings/, permission/   # Screens + their ViewModels
```

No dependency-injection framework, no database — the app is intentionally small enough that a
manual service locator (`KiraApplication`) and a JSON file (`ChatHistoryStore`) are simpler and
just as correct as Hilt/Room would be here, without their setup overhead.

### Why two WindowManager windows for the overlay?

`OverlayService` manages:

1. A small, always-visible, `WRAP_CONTENT` bubble window (the draggable Kira you see).
2. A full-screen, mostly-transparent window that is added **only** while the long-press menu is
   open, so its scrim can catch outside taps to dismiss the menu — then removed.

Both use `FLAG_NOT_FOCUSABLE` (never steals keyboard/back-button focus from whatever app is
underneath) but **not** `FLAG_NOT_TOUCHABLE`, so taps on Kira and on the menu still work normally.

## How to add a new emotion

1. Add the value to the `KiraEmotion` enum in `model/KiraEmotion.kt`.
2. Give it a duration/eligibility entry in the `spec()` function right below it.
3. Add a keyword/emoji rule (or reuse the punctuation fallback) in `emotion/EmotionEngine.kt`,
   placed at the right priority position in the `rules` list.
4. Add a `when` branch for its eyes/eyebrows/mouth in `ui/components/KiraAvatar.kt`'s
   `drawKiraFace`, and optionally an accent in `drawEmotionAccent` — or just drop
   `assets/kira/emotions/<your_emotion>.png` (see below) and skip the face-drawing step entirely.

Nothing else needs to change: the controller, overlay, and chat screen all switch on the enum
generically.

## How to add a new animation

Idle/interaction animations live in `KiraAvatar` (`ui/components/KiraAvatar.kt`), driven by
`rememberInfiniteTransition` (sway, blink), a one-shot `Animatable` (tap "pop"), and an
`AnimatedContent` cross-fade between emotions. To add a new animated behavior, branch on
`emotion` the same way the existing `swayAmplitude`/`swayDurationMs` `when` blocks do, or add a
new animated value alongside `sway`/`blink` and feed it into `drawKiraFace`.

## Changing Kira's appearance

Kira's shipped look lives in `app/src/main/assets/kira/emotions/*.png` (14 circular avatar
chips, one per emotion) with a Compose `Canvas` drawing (`KiraFace` in
`ui/components/KiraAvatar.kt`) as the automatic fallback for any emotion whose file is missing —
so the app always builds and runs even with zero art assets. To use your own art instead:

1. Export each emotion as a PNG (see `app/src/main/assets/kira/README.md` for the full list and
   naming convention, e.g. `happy.png`, `love.png`, `sleepy.png`). Square, consistently framed
   art works best since the UI displays it as a circular avatar.
2. Drop the files into `app/src/main/assets/kira/emotions/`.
3. Rebuild. `KiraImageProvider` picks up any file that exists there automatically and shows it
   instead of the built-in drawing — no code changes required. Emotions with no matching file
   keep using the built-in illustration, so you can override just a few at a time.

## Roadmap (not in v1, architecture allows it later)

Voice, speech recognition, lip-sync, a full 2D Live2D or 3D model, outfits, backgrounds, multiple
characters, wake word, calendar/reminders integration, long-term memory, and a real AI-based
emotion classifier behind the existing `EmotionClassifier` interface.

## Privacy & permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw Kira's bubble over other apps. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the overlay alive reliably (Android 14+ requires a declared foreground service type). |
| `POST_NOTIFICATIONS` | Only requested when you enable the "reply notifications" setting; shows the persistent low-priority overlay notice and optional chat-reply notices. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Only used when you opt into Custom API mode, to call your configured endpoint with your own key. ChatGPT mode uses a plain `Intent.ACTION_VIEW`, which needs no permission. |

No analytics, no ads, no third-party trackers.
