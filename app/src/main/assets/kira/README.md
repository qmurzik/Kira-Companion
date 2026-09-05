# Kira art assets

This folder is where you can drop **your own hand-drawn or commissioned artwork**
for Kira to replace the built-in vector illustration.

At runtime `KiraImageProvider` (see `image/KiraImageProvider.kt`) looks here first:

```
assets/kira/idle.png
assets/kira/happy.png
assets/kira/sad.png
assets/kira/thinking.png
assets/kira/surprised.png
assets/kira/angry.png
assets/kira/sleepy.png
assets/kira/confused.png
assets/kira/love.png
assets/kira/excited.png
```

If a file for the current emotion exists, it is decoded and shown instead of the
built-in procedural face. If it is missing (the default state of this repo, since
we ship no binary art), Kira automatically falls back to `KiraFace`, a
Jetpack Compose `Canvas` drawing that renders a simple, cute, non-explicit anime
face (short black hair, purple eyes, soft expressions) per emotion — so the app
always builds and runs even without real artwork.

## Recommended format

- PNG with transparency, square, at least 512x512.
- Consistent framing/proportions across all 10 files so switching between
  emotions doesn't "jump" the character around.
- Keep the style cute and friendly (no explicit/sexualized content).

## Adding a new emotion image

1. Export your art as `<emotion_name>.png` using the lowercase enum name from
   `KiraEmotion` (e.g. `love.png` for `KiraEmotion.LOVE`).
2. Drop it in this folder.
3. Rebuild the app. No code changes required.
