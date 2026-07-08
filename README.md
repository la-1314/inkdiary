# InkDiary

An AI diary companion with stroke-order handwriting animation for Chinese and Latin text on Android.

Write on the screen with your finger or stylus. After a pause, your words fade away, and a reply writes itself back — stroke by stroke, following proper writing order — in a flowing hand, then fades away.

No chat UI. Just ink on screen.

## Features

- **Stroke-order animation** — replies are drawn stroke-by-stroke with correct writing order for both Chinese (via Make Me a Hanzi data) and Latin text (via Zhang-Suen skeletonization)
- **Configurable AI persona** — default is a warm, sincere friend
- **Memory-driven growth** — remembers conversations and injects context
- **Any OpenAI-compatible API** — OpenAI, OpenRouter, Groq, etc.
- **Three-finger long-press (5s)** — enter config screen
- **First-launch setup** — auto enters config if no API key

## How it works

```
pen/stylus input (MotionEvent, pressure-sensitive)
   │ strokes
   ▼
InkCanvasView ── idle 2.8s → commit page → PNG
   │
   ▼
OracleClient (OpenAI-compatible /chat/completions, SSE streaming)
   │  reads handwriting from PNG (vision LLM)
   │  streams reply sentence-by-sentence
   ▼
StrokeAnimator
   ├── Chinese chars → Make Me a Hanzi SVG paths + medians
   └── Latin chars  → handwriting font → rasterize → Zhang-Suen thin → trace
   │
   ▼ stroke-by-stroke animation on canvas
InkCanvasView
```

## Building

Requirements: Android Studio Hedgehog+ (AGP 8.1), JDK 17, Android SDK 34.

```sh
git clone https://github.com/la-1314/inkdiary.git
cd inkdiary
./gradlew assembleDebug
```

### Chinese stroke data

The app ships with a small subset (~80 common characters) in `app/src/main/assets/hanzi_subset.json`. To get the full 9000+ character set:

```sh
bash scripts/download_hanzi_data.sh
```

## Configuration

On first launch, the app enters the config screen automatically. Later, use a **three-finger long-press (5 seconds)** on the diary page.

| Field | Description | Default |
|---|---|---|
| API Key | Your OpenAI-compatible API key | (required) |
| Base URL | API endpoint | `https://api.openai.com/v1` |
| Model | Vision-capable model name | `gpt-4o-mini` |
| Persona | System prompt defining the AI's personality | Warm friend template |

## Gestures

| Action | Effect |
|---|---|
| Write, then lift pen | The diary drinks your ink and a reply appears |
| Three-finger long-press (5s) | Enter config screen |
| Tap with 5 fingers | Exit the diary |

## Tech stack

- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34
- **HTTP**: OkHttp 4 (SSE streaming)
- **Async**: Kotlin Coroutines
- **UI**: Pure Android Views + Canvas

## License

MIT for all code in this repository.

- Chinese stroke data: Arphic Public License (from Make Me a Hanzi)
- Dancing Script font: SIL OFL 1.1 (from Google Fonts)

## Acknowledgments

- riddle by MaximeRivest — original inspiration
- Make Me a Hanzi — Chinese stroke data
- HanziWriter — reference animation implementation
- Tegaki — Zhang-Suen pipeline reference
