# Kira's 3D model

This is where the real Kira lives: place a file named exactly `Kira.vrm` in this directory
before building the app.

```
assets/models/Kira.vrm
```

**There is no bundled or fake `Kira.vrm` in this repository.** If the file isn't here, the
app honestly says so - "Модель Kira.vrm не найдена" ("Kira.vrm model not found") - with
these same instructions, rather than showing a placeholder image or synthetic character.
See [`ModelStatusRepository`](../../java/com/kira/companion/render/ModelStatusRepository.kt).

## What the file needs to be

- **Format:** VRM 0.x (a glTF Binary `.glb` container with a VRM extension under
  `extensions.VRM` in its JSON chunk). This is what VRoid Studio and most VRM marketplaces
  export by default. **VRM 1.0** (`extensions.VRMC_vrm`) is explicitly detected and
  rejected with an on-screen explanation rather than silently mishandled - re-export as
  VRM 0.x if your tool defaults to 1.0.
- **Humanoid rig, at minimum a `head` bone.** The parser reads
  `humanoid.humanBones[]`; without at least a mapped head bone the model is treated as not
  usable (see `VrmModelData.isUsable()`).
- **Recommended additional humanoid bones** for the full experience: `hips`, `spine`,
  `chest`, `neck`, `leftEye`/`rightEye` (for eye tracking), `leftShoulder`/`leftUpperArm`
  and `rightShoulder`/`rightUpperArm` (for touch reactions on the arms).
- **Blend shapes** (`blendShapeMaster.blendShapeGroups[]`) drive facial expressions. The
  app looks up groups by name - see
  [`BlendShapePresets`](../../java/com/kira/companion/behavior/BlendShapePresets.kt) for
  every name it tries per emotion, in order: the standard VRM presets (`neutral`, `joy`,
  `angry`, `sorrow`, `fun`, `blink`, `blink_l`, `blink_r`, `lookup`/`lookdown`/`lookleft`/
  `lookright`, visemes `a`/`i`/`u`/`e`/`o`) plus custom-named groups (`Love`, `Blush`,
  `Thinking`, `Surprised`, `Sleepy`, `Excited`, `Confused`, `Crying`, `Worried`,
  `Smug`). A model missing some of these still works - Kira just won't show that
  particular expressive detail - but a model with only the bare standard presets will
  look noticeably flatter across emotions like LOVE, SURPRISED, or SMUG that lean on the
  custom ones.
- **Spring bone chains** (`secondaryAnimation.boneGroups[]`) drive hair/cloth physics via
  [`SpringBoneSimulator`](../../java/com/kira/companion/behavior/SpringBoneSimulator.kt).
  Optional - without them Kira's hair simply won't sway - but VRoid Studio sets these up
  automatically for ponytails/twin-tails/skirts.

## Where to get or make one

- **VRoid Studio** (free, Windows/Mac) is the most common way to make an anime-style VRM
  character matching this app's intended look (short black hair, purple eyes, casual
  outfit) - export as VRM0.
- A VRM you already own or commissioned, as long as it meets the requirements above.
- Marketplaces like Booth.pm sell/give away VRM avatars, many exported as VRM 0.x already.

Once the file is in place, rebuild the app - no code changes are needed.
