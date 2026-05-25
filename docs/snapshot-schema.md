# Snapshot Schema

The default output bundle contains two files:

```text
screenshot.png
snapshot.json
```

`summary.json`, raw semantics, source indexes, and render logs are not default artifacts.

## Versioning

Current snapshots are emitted with `schemaVersion: 2`.

Version 2 adds preview parameter metadata, optional layout tree data, and optional render metadata to the original compact snapshot shape. Consumers should ignore unknown fields so additive fields can be introduced without breaking older readers. The plugin still decodes older v1 snapshots that omit `preview.previewParameter`, `layoutTree`, and `render`.

## JSON Shape

```json
{
  "schemaVersion": 2,
  "preview": {
    "id": ":app:commonMain:LoginPreview:previewParam-0",
    "name": "Login",
    "group": "Auth",
    "source": "LoginPreview.kt:12",
    "sourceSet": "commonMain",
    "previewParameter": {
      "providerClassName": "dev.example.LoginStateProvider",
      "parameterType": "dev.example.LoginState",
      "limit": 5,
      "index": 0
    }
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
      "tag": "continue_button",
      "source": "LoginScreen.kt:84"
    }
  ],
  "layoutTree": [
    {
      "id": "layout-1",
      "boundsPx": { "x": 0, "y": 0, "width": 393, "height": 852 },
      "boundsDp": { "x": 0.0, "y": 0.0, "width": 393.0, "height": 852.0 },
      "componentHint": "androidx.compose.foundation.layout.Column",
      "sourceName": "LoginCard",
      "sourceFile": "LoginPreview.kt",
      "sourceLine": 24,
      "sourceHintKind": "tooling-ancestor-node-identity",
      "modifierHint": "androidx.compose.ui.Modifier",
      "classHint": "androidx.compose.ui.node.LayoutNode",
      "semanticsId": "7",
      "semantics": {
        "text": "Continue",
        "contentDescription": "Primary action",
        "role": "Button",
        "actions": ["OnClick"]
      }
    }
  ],
  "render": {
    "mode": "robolectric"
  }
}
```

Optional fields are omitted when no value is available. In fake-renderer mode, `nodes` and `layoutTree` are empty and `render.mode` is `fake`.

## Preview Parameters

Parameterized previews are expanded during capture. The listed parent preview id remains filterable, and each captured value uses an expanded id with `:previewParam-N` appended. The `preview.previewParameter.index` field identifies which provider value produced the snapshot.

## Viewports

Production captures identify the platform and named viewport used for rendering. If an Android `@Preview` omits `widthDp` or `heightDp`, the production renderer renders the preview once for each configured Android viewport, such as `android-phone` and `android-tablet`. If the annotation specifies positive dimensions, those dimensions win and the viewport name is `preview`.

Future desktop and web renderers should use separate platform-specific viewport configuration instead of reusing Android viewport settings.

## Semantics and Layout Tree

Fake renderer snapshots emit an empty `nodes` list because no real Compose semantics tree is available. Production renderer snapshots populate `nodes` from Compose semantics when available and may include `layoutTree` entries derived from the rendered Compose layout hierarchy.

`layoutTree` entries always keep `componentHint` as the implementation-level fallback. Production Android rendering may also add nullable best-effort source hints from Compose tooling data: `sourceName`, `sourceFile`, `sourceLine`, and `sourceHintKind`. These fields are optional, depend on Compose tooling/source information being available at render time, and are omitted if enrichment fails.
