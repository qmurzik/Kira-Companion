# Kira art assets

`emotions/` holds the artwork shown for each of Kira's expressions, one PNG per
[`KiraEmotion`](../../java/com/kira/companion/model/KiraEmotion.kt) value:

```
assets/kira/emotions/idle.png
assets/kira/emotions/happy.png
assets/kira/emotions/love.png
assets/kira/emotions/shy.png
assets/kira/emotions/thinking.png
assets/kira/emotions/surprised.png
assets/kira/emotions/sad.png
assets/kira/emotions/angry.png
assets/kira/emotions/sleepy.png
assets/kira/emotions/excited.png
assets/kira/emotions/confused.png
assets/kira/emotions/wink.png
assets/kira/emotions/laughing.png
assets/kira/emotions/crying.png
```

At runtime `KiraImageProvider` (see `image/KiraImageProvider.kt`) looks here first. If a
file for the current emotion exists, it is decoded and shown (cropped to a soft circular
"avatar" chip by the UI). If it is missing, Kira automatically falls back to `KiraFace`, a
Jetpack Compose `Canvas` drawing that renders a simple, cute, non-explicit anime face - so
the app always builds and runs even without any bundled artwork.

## Where the shipped art came from

The bundled PNGs started as a 14-pose reference sheet (short black hair, purple eyes, one
consistent character) and were each cropped to a matching circular frame with a soft
feathered edge, sized identically, so switching between emotions doesn't jump or stretch.

## Adding/replacing an emotion's artwork

1. Export your art as `<emotion_name>.png` using the lowercase enum name from
   `KiraEmotion` (e.g. `love.png` for `KiraEmotion.LOVE`).
2. Keep it square and consistently framed with the existing set (a face/bust crop works
   best - the UI displays it as a circular avatar).
3. Drop it in `assets/kira/emotions/`.
4. Rebuild. No code changes required.
