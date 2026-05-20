# Snapshot Schema

The default output bundle contains two files:

```text
screenshot.png
snapshot.json
```

`summary.json`, raw semantics, source indexes, and render logs are not default phase-1 artifacts.

## JSON Shape

```json
{
  "schemaVersion": 1,
  "preview": {
    "id": ":app:commonMain:LoginPreview",
    "name": "Login",
    "group": "Auth",
    "source": "LoginPreview.kt:12",
    "sourceSet": "commonMain"
  },
  "viewport": {
    "platform": "android",
    "name": "phone",
    "width": 393,
    "height": 852,
    "density": 1.0
  },
  "nodes": [
    {
      "id": "n1",
      "role": "button",
      "text": "Continue",
      "bounds": {
        "x": 48,
        "y": 720,
        "width": 297,
        "height": 56
      },
      "actions": ["click"],
      "tag": "continue_button"
    }
  ]
}
```

## Viewports

Production captures identify the platform and named viewport used for rendering. If an Android `@Preview` omits `widthDp` or `heightDp`, the production renderer should render the preview once for each configured Android viewport, such as `android-phone` and `android-tablet`. If the annotation specifies positive dimensions, those dimensions win and the viewport name should be `preview`.

Future desktop and web renderers should use separate platform-specific viewport configuration instead of reusing Android viewport settings.

## Phase 1 Semantics

The phase-1 fake renderer emits an empty `nodes` list because no real Compose semantics tree is available. Later production renderer work must populate `nodes` from a merged Compose semantics tree.
