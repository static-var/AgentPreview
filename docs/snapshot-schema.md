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

## Phase 1 Semantics

The phase-1 fake renderer emits an empty `nodes` list because no real Compose semantics tree is available. Later production renderer work must populate `nodes` from a merged Compose semantics tree.
