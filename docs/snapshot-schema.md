# Snapshot Schema

The default output bundle contains two files:

```text
screenshot.png
snapshot.json
```

`summary.json`, raw semantics, source indexes, and render logs are not default artifacts.

## Versioning

Current snapshots are emitted with `schemaVersion: 1`.

`schemaVersion` identifies the current snapshot shape and gives consumers a stable field to check if the schema changes in the future. Consumers should ignore unknown fields so additive fields can be introduced without breaking older readers.

## JSON Shape

```json
{
  "schemaVersion": 1,
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
      "sourceHintKind": "tooling-nearest-app-ancestor",
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
  },
  "screenshot": {
    "width": 320,
    "height": 180,
    "crop": {
      "enabled": true,
      "fallback": false,
      "x": 36,
      "y": 120,
      "width": 320,
      "height": 180,
      "paddingDp": 20
    }
  }
}
```

Optional fields are omitted when no value is available. In fake-renderer mode, `nodes` and `layoutTree` are empty and `render.mode` is `fake`.

## Preview Parameters

Parameterized previews are expanded during capture. The listed parent preview id remains filterable, and each captured value uses an expanded id with `:previewParam-N` appended. The `preview.previewParameter.index` field identifies which provider value produced the snapshot.

## Viewports and Screenshot Crop

Production captures identify the platform and named viewport used for rendering. If an Android `@Preview` omits `widthDp` or `heightDp`, the production renderer renders the preview once for each configured Android viewport, such as `android-phone` and `android-tablet`. If the annotation specifies positive dimensions, those dimensions win and the viewport name is `preview`.

`snapshot.viewport` always describes the original render viewport. `screenshot.png` may be smaller because real rendered screenshots crop to detected layout-tree or semantics content by default with 20dp padding. `screenshot.width` and `screenshot.height` describe the exported PNG. When cropping succeeds, `screenshot.crop.x/y/width/height` map the exported PNG back into original viewport pixel coordinates.

If cropping is disabled or bounds are ambiguous, AgentPreview exports the full viewport and records a fallback:

```json
"screenshot": {
  "width": 393,
  "height": 852,
  "crop": {
    "enabled": true,
    "fallback": true,
    "reason": "ambiguous-content-bounds",
    "paddingDp": 20
  }
}
```

When disabled with `-PagentPreview.cropToContent=false` or `agentPreview { android { screenshot { cropToContent.set(false) } } }`, the fallback reason is `disabled`. Override padding with `-PagentPreview.cropPaddingDp=12` or `cropPaddingDp.set(12)`. CLI properties override DSL values for the current invocation.

Future desktop and web renderers should use separate platform-specific viewport configuration instead of reusing Android viewport settings.

## Semantics and Layout Tree

Fake renderer snapshots emit an empty `nodes` list because no real Compose semantics tree is available. Production renderer snapshots populate `nodes` from Compose semantics when available and may include `layoutTree` entries derived from the rendered Compose layout hierarchy.

`layoutTree` entries always keep `componentHint` as the implementation-level fallback. Production Android rendering may also add nullable best-effort source hints from Compose tooling data: `sourceName`, `sourceFile`, `sourceLine`, and `sourceHintKind`. The correlation prefers app/preview source files over Compose runtime internals such as `ReusableComposeNode`, `Layout.kt`, `Composer.kt`, and `Composables.kt`; if no app group can be correlated within ancestry/preorder/bounds constraints, the hint may be a useful framework composable or a framework/internal fallback. Current hint kinds include `tooling-node-identity`, `tooling-nearest-app-ancestor`, `tooling-sibling-preorder-app`, `tooling-useful-framework-ancestor`, `tooling-framework-node-identity`, `tooling-framework-ancestor`, `tooling-sibling-preorder-framework`, and `preview-entrypoint-fallback`.

These fields are optional, depend on Compose tooling/source information being available at render time, and are omitted if enrichment fails. `sourceLine` is emitted only when an actual positive source line is available; preview-entrypoint fallback hints may include `sourceName` and `sourceFile` with `sourceLine` omitted. Compose Multiplatform Android-target captures may currently emit only `preview-entrypoint-fallback` layout source hints when tooling composition data is unavailable for the rendered common source.
