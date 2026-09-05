# Kira Companion

Kira is a real-time 3D AI companion for Android: a fully-rigged VRM character who lives on
top of your other apps, tracks you with her eyes, blinks and breathes on her own, reacts to
touch, and chats with you — not a set of static images, an actual 3D scene rendered live on
your device.

<p>
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-8B5CF6" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/3D-Filament%20%2F%20SceneView-FF6F61" alt="Filament / SceneView">
  <img src="https://img.shields.io/badge/language-Kotlin-7F52FF" alt="Kotlin">
</p>

> **This repository ships no 3D model.** Kira's actual character file
> (`app/src/main/assets/models/Kira.vrm`) is not bundled, on purpose — see
> [The VRM pipeline](#the-vrm-pipeline-kiravrm) below. Without it, the app builds and runs
> normally and honestly tells you the model is missing, with instructions for where to put
> one; it never falls back to a static image or a fake placeholder character.

## Two independent halves, on purpose

- **Kira herself — fully local, no API key, ever.** The 3D rendering, overlay, dragging,
  emotions, gaze/blink/idle animation, touch reactions, random behaviors, settings, position
  memory, and local chat history all work completely offline. Nothing about *being Kira*
  requires an account, a key, or a network call.
- **Real AI conversation — via the official ChatGPT app/website.** This app never logs into
  ChatGPT, never reads its cookies/session/tokens, and never treats a ChatGPT subscription as
  an API. It just opens `https://chatgpt.com/` (the app if installed, the browser otherwise)
  through a normal `Intent.ACTION_VIEW` when you ask to chat for real. An optional, fully
  separate "Custom API" mode lets power users bring their own OpenAI-compatible key if they
  want in-app AI replies instead — never required.

## Why Filament/SceneView instead of Unity

The original ask was Unity or an equivalent 3D engine. Unity isn't usable in the environment
this app was built in (no Editor, no license, no GUI, no way to visually verify a rendered
scene), so the architecture uses **Google Filament** — the same open-source, physically-based
renderer Google ships inside ARCore/Android itself — through **SceneView's** Compose-native
Kotlin wrapper (`io.github.sceneview:sceneview`). Every Filament/SceneView API this app calls
(`Engine`, `TransformManager`, `RenderableManager`, `FilamentAsset`, `ModelLoader`,
`SceneView`/`ModelNode` composables) was verified against the actual library source rather
than guessed. If a genuine need for Unity ever arises, the VRM-parsing and behavior logic
(see below) don't touch Filament at all, so only the `render/` package would need replacing.

## The VRM pipeline (Kira.vrm)

1. Place a **VRM 0.x** file at `app/src/main/assets/models/Kira.vrm` — see
   [`assets/models/README.md`](app/src/main/assets/models/README.md) for the exact format,
   humanoid bone, blend shape, and spring bone requirements, plus where to get or make one
   (VRoid Studio is the easiest path to an anime-style character matching Kira's intended
   look: short black hair, purple eyes, casual outfit).
2. Rebuild. `ModelStatusRepository` checks for the file at startup, parses its glTF-binary
   JSON chunk (`vrm/GlbReader.kt`, `vrm/VrmJsonParser.kt` — pure Kotlin, no Android/Filament
   dependency, so this parsing is fully unit-tested on the JVM) and validates it has at least
   a humanoid `head` bone.
3. `KiraCharacterView` (`ui/character/KiraCharacterView.kt`) shows exactly one of three
   states, reactively: a loading spinner, the real rendered 3D character
   (`VrmCharacterHost`), or an honest Russian-language explanation of what's missing/wrong —
   never a fake stand-in.
4. **VRM 1.0** (`VRMC_vrm`) is explicitly detected and rejected with an on-screen message
   telling you to re-export as VRM 0.x, rather than silently mishandled.

## Features

- **A real, rigged 3D character**, not a sprite sheet — skeletal humanoid bones, facial
  blend shapes, and (if the model defines them) hair/cloth spring bones, all driven live
  every frame.
- **Eye tracking & idle gaze wandering** — Kira looks roughly forward, occasionally glances
  around on her own, and looks toward wherever you actually touched her, all blended smoothly
  between targets rather than snapping (`behavior/GazeController.kt`, `behavior/GazeMath.kt`).
- **Natural blinking** — randomized interval and duration per blink, with occasional
  double-blinks, instead of a fixed open→closed→open timer (`behavior/BlinkTimer.kt`).
- **Hair/cloth physics** — a lightweight spring-damper simulation per spring bone chain the
  model defines, reacting to how much the head just turned (`behavior/SpringBoneSimulator.kt`).
- **17 emotions**, each blending several facial parameters at once (never a single on/off
  face): `IDLE HAPPY LOVE SHY THINKING SURPRISED SAD ANGRY SLEEPY EXCITED CONFUSED WINK
  LAUGHING CRYING WORRIED EMBARRASSED SMUG` (`behavior/BlendShapePresets.kt`), resolved
  against whichever blend shape groups the loaded model actually defines.
- **Local, rule-based emotion engine** — Russian/English keyword matching, emoji, repeated-
  character laughter ("хаха", "lol"), and punctuation/ALL-CAPS heuristics pick an emotion
  *and* an intensity/duration from what you type (`EmotionEngine.analyze()`), in a fixed
  priority order so one phrase never produces a conflicting result. Swappable
  (`EmotionClassifier`) for a real AI-based classifier later.
- **Region-aware touch reactions** — a tap is classified into head/arm/body
  (`behavior/TouchGeometry.kt`) and reacted to accordingly: a head pat reads as
  HAPPY-then-SHY, repeated petting past a threshold gets a teasing line ("Эй~ хватит меня
  гладить >///<"), an arm tap redirects her gaze, a body tap is neutral — deliberately
  platonic reactions only, never sexualized.
- **A speech bubble** near Kira, driven by whatever she's currently "saying" (a chat reply,
  a touch reaction, "Подожди, я думаю..." while waiting on an AI response), fading in/out.
- **Autonomous random behaviors** — look away, blink, a small smile, on a randomized
  30–120s-ish interval (scaled by an Off/Low/Normal/High setting), separate from and never
  conflicting with whatever emotion/interaction is currently active.
- **Floating overlay bubble** — the same live 3D character renders on top of other apps via
  `SYSTEM_ALERT_WINDOW`, draggable and dockable to a screen edge, remembering position/size
  across restarts.
- **Long-press menu** — Open ChatGPT / Emotion (debug cycle) / Settings / Hide Kira / Close app.
- **Battery-conscious rendering** — the overlay renders at `RenderQuality.Performance`
  (shadows/SSAO/bloom off, dynamic resolution) and throttles her behavior/physics tick to
  ~30fps, independent of Filament's own frame presentation rate; the in-app screens use the
  higher-fidelity default since they're a deliberate viewing session, not an always-on
  background companion.
- **Full chat screen** — bubbles, "Kira is typing…" indicator, multiline + emoji + full
  Unicode/Cyrillic input, persisted history (JSON file on disk), "clear history" — shown only
  in Local Demo / Custom API mode; in ChatGPT mode it's replaced by a single "Open ChatGPT"
  card (with the real 3D Kira above it).
- **Swappable AI backend** — `AiProvider` interface with a `LocalDemoAiProvider` (fully
  offline, on-brand canned replies) and an `OpenAiProvider` for the optional Custom API mode.
- **Full settings screen** — overlay on/off, random-behavior frequency, Kira's size, sound,
  notifications, light/dark/system theme, AI provider + optional API key, clear history,
  reset position, about.

## Requirements

- Android 8.0 (API 26) or newer, with **OpenGL ES 3.0+** (declared as a required feature in
  the manifest — Filament's minimum).
- A VRM 0.x character file at `app/src/main/assets/models/Kira.vrm` (see above) — not bundled.
- To build: JDK 17, Android SDK (platform 35, build-tools 35.0.0), Kotlin 2.4.10 (pinned in
  the root `build.gradle.kts` — needed to match the Kotlin version SceneView 4.34.0 itself
  was compiled with). Android Studio Ladybug/Meerkat or newer will fetch these automatically.

## Installing the app

1. Place your `Kira.vrm` at `app/src/main/assets/models/Kira.vrm` **before building** — the
   APK bakes in whatever's there at build time.
2. Download/build the debug APK (see [Where to find the APK](#where-to-find-the-apk) below).
3. On your phone: enable "Install unknown apps" for the source you used if prompted, then
   open the APK to install it.
4. Launch **Kira**. If no model was bundled, every screen that would show her instead shows
   "Модель Kira.vrm не найдена" with the same placement instructions — install a build with
   the file in place to see the real character.

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
Every companion feature (3D rendering, overlay, emotions, gaze/blink/physics, touch
reactions) already works before you touch this setting; it only decides what "real AI
conversation" means:

- **ChatGPT** (default) — the chat screen shows the real 3D Kira plus a single "Open ChatGPT"
  button that hands off to the official app/website via a plain `Intent.ACTION_VIEW`. This
  app never logs in, never reads ChatGPT's cookies/session/tokens, and a ChatGPT subscription
  is never treated as API access.
- **Local Demo** — a fully offline canned-response generator (`LocalDemoAiProvider`) so you
  can try the full chat UI and emotion engine with zero setup.
- **Custom API** — optional, opt-in: paste your own OpenAI-compatible API key for real
  in-app replies. Never required to use any other part of the app.

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
# Place your Kira.vrm at app/src/main/assets/models/Kira.vrm first (see above).
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Useful commands:

```bash
./gradlew test           # unit tests (VRM parsing, behavior/gaze/blink/physics logic, emotion engine/controller - JVM-only)
./gradlew lint           # Android lint
./gradlew assembleDebug  # debug APK
./gradlew assembleRelease  # release APK (falls back to debug signing without secrets, see below)
```

Note: the render layer itself (`render/VrmRig.kt`, `render/KiraModelAsset.kt`,
`render/VrmCharacterHost.kt`) touches Filament directly and has no unit tests — there's no
Filament runtime outside a real device/emulator. Everything it depends on (which blend
shapes to apply, which bone angles, physics state) is pure Kotlin and unit-tested instead;
the Filament-touching code is kept as thin as possible specifically so it needs the least
trust.

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
**`kira-companion-debug-apk`**. Note that CI builds without a real `Kira.vrm` present, so the
resulting APK will show the "model not found" screen until you rebuild with one bundled.

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
├── KiraApplication.kt         # Manual service locator (settings, chat store, AI, behavior controller, model status)
├── KiraViewModelFactory.kt    # Tiny ViewModel factory (no DI framework needed at this size)
├── model/                     # KiraEmotion (17 values), ChatMessage, app-wide enums (size/theme/frequency/...)
├── emotion/                    # EmotionEngine (rule-based classifier + analyze()) + EmotionController (state machine)
├── vrm/                        # Pure-Kotlin VRM parsing: GlbReader (glTF-binary container), VrmJsonParser,
│                                #   VrmModel (VrmModelData/VrmHumanBone/VrmBlendShapeGroup/VrmSpringBoneChain)
├── behavior/                   # KiraBehaviorController (orchestrator) + separate concerns it wires together:
│                                #   GazeController, BlinkTimer, RandomBehaviorScheduler, SpeechBubbleController,
│                                #   TouchReactionController, TouchGeometry, BlendShapePresets, SpringBoneSimulator,
│                                #   GazeMath - each independently unit-tested
├── render/                     # Filament/SceneView glue: ModelStatusRepository, VrmRig, KiraModelAsset, Mat4,
│                                #   VrmCharacterHost (the actual 3D rendering composable) - the only layer that
│                                #   touches raw Filament APIs, kept as thin as possible
├── ai/                          # AiProvider interface, LocalDemoAiProvider, OpenAiProvider, factory, openChatGpt()
├── data/                        # SettingsRepository (DataStore), ChatHistoryStore (JSON file), SecureKeyStore
├── overlay/                     # OverlayService (WindowManager + foreground service), gestures, menu UI
├── notifications/               # NotificationHelper (overlay + chat-reply notifications)
├── navigation/                  # Bottom-nav Compose NavHost (Home / Chat / Settings)
└── ui/
    ├── theme/                  # Material 3 theme (purple/pink palette, light+dark)
    ├── character/               # KiraCharacterView - picks loading/missing-model/real-3D-character per screen
    ├── home/, chat/, settings/, permission/   # Screens + their ViewModels
```

No dependency-injection framework, no database — the app is intentionally small enough that a
manual service locator (`KiraApplication`) and a JSON file (`ChatHistoryStore`) are simpler and
just as correct as Hilt/Room would be here, without their setup overhead.

### The behavior system: `KiraBehaviorController`

Per the explicit design goal of not mixing everything into one class, each concern Kira's
behavior needs stays in its own, independently unit-tested class:

| Concern | Class |
|---|---|
| Current emotion, auto-return-to-idle timing | `EmotionController` |
| Where the eyes/head look (idle wandering + touch look-at) | `GazeController` / `GazeMath` |
| Blink timing (randomized interval/duration, double-blinks) | `BlinkTimer` |
| Autonomous idle behaviors (look away, blink, smile, ...) | `RandomBehaviorScheduler` |
| What Kira is currently "saying" | `SpeechBubbleController` |
| Interpreting a tap by body region into a reaction | `TouchReactionController` / `TouchGeometry` |
| Which blend shapes an emotion maps to | `BlendShapePresets` |
| Hair/cloth sway physics | `SpringBoneSimulator` |

`KiraBehaviorController` only wires these together behind one `update()`/`onTap()`/
`onUserMessage()` surface for `VrmCharacterHost` and the overlay to drive — it makes no
behavioral decisions of its own.

### Why two WindowManager windows for the overlay?

`OverlayService` manages:

1. A small, always-visible, `WRAP_CONTENT` bubble window (the draggable, live-3D-rendering
   Kira you see).
2. A full-screen, mostly-transparent window that is added **only** while the long-press menu is
   open, so its scrim can catch outside taps to dismiss the menu — then removed.

Both use `FLAG_NOT_FOCUSABLE` (never steals keyboard/back-button focus from whatever app is
underneath) but **not** `FLAG_NOT_TOUCHABLE`, so taps on Kira and on the menu still work normally.

## How to add a new emotion

1. Add the value to the `KiraEmotion` enum in `model/KiraEmotion.kt`.
2. Give it a duration/eligibility entry in the `spec()` function right below it.
3. Add a keyword/emoji rule (or reuse the punctuation fallback) in `emotion/EmotionEngine.kt`,
   placed at the right priority position in the `rules` list, and a base intensity in its
   `baseIntensity` map.
4. Add a blend shape combination for it in `behavior/BlendShapePresets.kt` — always more than
   one shape at once (e.g. a standard preset plus a custom-named group), resolved against
   whichever groups the loaded model actually defines.

Nothing else needs to change: `KiraBehaviorController`, the render layer, and the UI all
switch on the enum generically.

## Roadmap (not in v1, architecture allows it later)

Voice, speech recognition, lip-sync (the blend-shape pipeline already has viseme presets
`a`/`i`/`u`/`e`/`o` wired up, just nothing driving them yet), VRM 1.0 support, outfits,
backgrounds, multiple characters, wake word, calendar/reminders integration, long-term
memory, and a real AI-based emotion classifier behind the existing `EmotionClassifier`
interface.

## Privacy & permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw Kira's bubble over other apps. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the overlay alive reliably (Android 14+ requires a declared foreground service type). |
| `POST_NOTIFICATIONS` | Only requested when you enable the "reply notifications" setting; shows the persistent low-priority overlay notice and optional chat-reply notices. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Only used when you opt into Custom API mode, to call your configured endpoint with your own key. ChatGPT mode uses a plain `Intent.ACTION_VIEW`, which needs no permission. |

No analytics, no ads, no third-party trackers.
