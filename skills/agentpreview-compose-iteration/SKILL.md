---
name: agentpreview-compose-iteration
description: Use when an AI agent is building, reviewing, or refining Jetpack Compose UI with AgentPreview available, especially from Figma files, screenshots, design specs, or product UI requests
---

# AgentPreview Compose Iteration

## What AgentPreview is

AgentPreview is a Gradle plugin that turns AndroidX Compose `@Preview` functions into files an agent can inspect:

```text
<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/
  screenshot.png
  snapshot.json
```

Use it as a tight feedback loop for Compose UI work:

1. edit a focused composable
2. capture the matching preview
3. compare `screenshot.png` and `snapshot.json` against the design request
4. iterate until the UI matches

Do **not** use AgentPreview as a default mass-capture tool for an entire project. Prefer one component, one viewport, and one `PreviewParameter` value while iterating.

## When to use this skill

Use this workflow when:

- converting a Figma frame, design screenshot, or product spec into Compose UI
- adjusting an existing Compose component to match a visual target
- checking if a UI change affected text, layout, accessibility semantics, or source-linked layout nodes
- an agent needs visual feedback without launching the full app manually

## Core commands

List previews first:

```bash
./gradlew :app:listComposePreviews
```

Plan a focused capture before rendering:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=Login \
  -PagentPreview.viewportFilter=phone \
  -PagentPreview.maxPreviewParameterValues=1 \
  -PagentPreview.maxCaptures=1 \
  -PagentPreview.dryRun=true
```

Capture the same target:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=Login \
  -PagentPreview.viewportFilter=phone \
  -PagentPreview.maxPreviewParameterValues=1 \
  -PagentPreview.maxCaptures=1
```

Useful controls:

| Need | Flag |
|---|---|
| Focus one preview | `-PagentPreview.previewNameFilter=PrismButtonPreview` |
| Focus one viewport | `-PagentPreview.viewportFilter=phone` |
| Limit `@PreviewParameter` states | `-PagentPreview.maxPreviewParameterValues=1` |
| Refuse broad plans | `-PagentPreview.maxCaptures=1` |
| Plan without rendering | `-PagentPreview.dryRun=true` |
| Continue collecting outputs after failures | `-PagentPreview.continueOnError=true` |
| Render concurrently when stable | `-PagentPreview.maxParallelRenders=2` |

Fake renderer mode, `-PagentPreview.fakeRenderer=true`, is only for discovery/debug wiring. Do not use fake screenshots as visual evidence.

## How to inspect outputs

Open:

```text
screenshot.png
snapshot.json
```

Read the capture report:

```text
<module>/build/agentPreviewReports/capture-report.json
```

Path note: logical preview ids are sanitized for folders. For example, `:app:main:LoginPreview` becomes `app-main-LoginPreview`, and viewport folders include platform, such as `android-phone`.

Key `snapshot.json` fields:

| Field | How agents should use it |
|---|---|
| `preview` | Preview id, name, group, source set, and `PreviewParameter` metadata. |
| `viewport` | Rendered platform/name/size/density. Use this to compare against design frame size. |
| `render.mode` | Should usually be `robolectric`; `fake` means placeholder output. |
| `nodes` | Compose semantics: text, content descriptions, roles, actions, bounds. Good for accessibility and interaction checks. |
| `layoutTree` | Experimental layout tree with px/dp bounds, source hints, component hints, and semantics summaries. Good for navigating code and diagnosing spacing/layout issues. |

Use `layoutTree.sourceFile`, `sourceLine`, and `sourceName` to jump to likely code. Treat source hints as best-effort clues, not absolute truth.

## Figma/design-to-Compose loop

1. **Extract design facts**
   - viewport/frame size
   - spacing and alignment
   - typography, color, shape, elevation
   - icons/images/content
   - states and variants
   - accessibility labels and roles

2. **Find or create a focused preview**
   - Run `listComposePreviews`.
   - Pick the closest component/screen preview.
   - If none exists, add a small deterministic preview near the target composable.

3. **Dry-run the capture plan**
   - Use `dryRun=true` and `maxCaptures=1`.
   - If unrelated previews are planned, tighten `previewNameFilter`, `viewportFilter`, or `maxPreviewParameterValues`.

4. **Edit narrowly**
   - Reuse existing design-system tokens/components.
   - Avoid global theme/token changes unless explicitly requested.
   - Keep preview data deterministic.

5. **Capture and compare**
   - Compare `screenshot.png` against the Figma/screenshot/spec.
   - Use `nodes` to verify text, roles, actions, and semantic bounds.
   - Use `layoutTree.boundsDp` / `boundsPx` to check spacing and dimensions.
   - Use source hints to navigate to the responsible composable.

6. **Iterate**
   - Re-run the same focused command after each small change.
   - Broaden to more states/viewports only after the focused case is close.

7. **Verify before completion**
   - Run relevant module tests/build/format.
   - Report what was compared: preview id, viewport, screenshot, snapshot, and remaining differences.

## Decision checkpoints

Before editing:

- Which preview represents the target design?
- Which viewport matches the design frame?
- Which `PreviewParameter` value/state should be used?

After capture:

- Is the mismatch layout, typography, color, content, state, or accessibility?
- Does `layoutTree` point to app/design-system code or only framework fallback hints?
- Can existing tokens/components solve the mismatch?

Before finishing:

- Did the focused screenshot visually match the request?
- Do semantics contain expected labels/roles/actions?
- Were relevant tests/build checks run?

## Safety rules

- Do not capture the whole project by default.
- Do not change unrelated components to fix one preview.
- Do not judge UI from fake-renderer screenshots.
- Do not ignore `capture-report.json` failures.
- Do not treat `layoutTree` source hints as perfect source mapping.
- Prefer small, reversible Compose changes and repeated focused captures.
