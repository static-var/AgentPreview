---
name: agentpreview
description: Use when an agent needs visual or structured feedback for AndroidX Compose, Compose Multiplatform Android-target, or Android KMP UI changes without launching the full app.
---

# AgentPreview

Use AgentPreview as a tight feedback loop for Compose UI work. It turns `@Preview`
functions into artifacts an agent can inspect:

```text
<module>/build/agentPreviewSnapshots/<sanitized-preview-id>/<platform>-<viewport>/
  screenshot.png
  snapshot.json
```

The goal is to understand the UI you are writing: pixels from `screenshot.png`,
plus machine-readable preview, viewport, semantics, layout, crop, and render
metadata from `snapshot.json`.

This skill is the operating loop, not the full manual. For install, convention
plugin setup, property reference, schema details, or resource/font
troubleshooting, read `docs/agent-usage.md` selectively.

## Focus The Capture

1. Run the module task that owns the previews:

```bash
./gradlew :app:listComposePreviews
```

2. Pick the smallest relevant preview. Prefer the full left-hand id from
   `listComposePreviews`; short filters such as `Login` can match multiple
   previews.
3. Dry-run before rendering:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=:app:main:LoginPreview \
  -PagentPreview.viewportFilter=preview \
  -PagentPreview.maxCaptures=1 \
  -PagentPreview.dryRun=true
```

4. Inspect `<module>/build/agentPreviewReports/capture-report.json`. Confirm
   the selected preview, selected viewport, planned count, and failures.
5. Render the same target without `dryRun=true`:

```bash
./gradlew :app:captureComposePreviews \
  -PagentPreview.previewNameFilter=:app:main:LoginPreview \
  -PagentPreview.viewportFilter=preview \
  -PagentPreview.maxCaptures=1
```

Use `viewportFilter=preview` for previews with explicit
`@Preview(widthDp/heightDp)` dimensions. Use configured viewport names such as
`phone`, `tablet`, `android-phone`, or `android-tablet` when the preview omits
explicit dimensions and AgentPreview expands it across configured viewports.

## Useful Properties

| Property | Use |
| --- | --- |
| `-PagentPreview.previewNameFilter=...` | Filter by preview id, name, group, function name, or expanded `:previewParam-N` id. Prefer full ids for exact work. |
| `-PagentPreview.viewportFilter=...` | Filter viewport names, including `preview` or `platform-name` forms such as `android-phone`. |
| `-PagentPreview.maxPreviewParameterValues=1` | Limit `@PreviewParameter` states while iterating. |
| `-PagentPreview.maxCaptures=1` | Fail before rendering if the plan is broader than intended. |
| `-PagentPreview.continueOnError=true` | Collect remaining captures, while still failing the task if any capture fails. |
| `-PagentPreview.cropToContent=false` | Export full-viewport diagnostic screenshots. |
| `-PagentPreview.cropPaddingDp=12` | Override default crop padding for the run. |
| `-PagentPreview.fakeRenderer=true` | Discovery/debug wiring only. Never judge UI from fake screenshots. |
| `-Dagentpreview.fontProbe=true` | Debug CMP font asset visibility when real renders lose fonts. |

## Inspect The Artifacts

Open `screenshot.png` only after checking `snapshot.json` says:

```json
"render": { "mode": "robolectric" }
```

`render.mode` is the evidence gate:

- `robolectric`: real Android-backed render; screenshot can be used as visual evidence.
- `fake`: deterministic placeholder for discovery/debugging only.
- `diagnostic-fallback`: diagnostic placeholder; useful for failure analysis, not UI judgment.

Use `snapshot.json` to answer concrete UI questions:

- `preview`: which preview, source set, source hint, and preview parameter value rendered.
- `viewport`: original render platform, viewport name, size, and density.
- `screenshot`: exported PNG size and crop/fallback metadata.
- `nodes`: semantics text, content descriptions, roles, actions, tags, and bounds.
- `layoutTree`: best-effort layout bounds, component hints, source hints, and semantics correlation.

Treat `layoutTree` source hints as clues, not ground truth. Prefer screenshot plus
semantics when judging visual or accessibility behavior.

## Iterate

Use a narrow edit-capture-inspect loop:

1. Change the focused composable or preview data.
2. Re-run the same focused capture command.
3. Compare `screenshot.png` against the design or expected UI.
4. Use `nodes` to verify labels, roles, actions, and bounds.
5. Use `layoutTree.boundsDp` / `boundsPx` to reason about spacing and sizing.
6. Broaden to more previews, preview-parameter values, or viewports only after
   the focused case is correct.

When reporting results, include the preview id, viewport, screenshot path,
snapshot path, `render.mode`, capture-report failure count, and any remaining
visual differences.

## Failure Handling

- No previews listed: ensure the plugin is applied to the module that owns the
  compiled preview classes, then run that module's `listComposePreviews`.
- Too many captures: dry-run first, inspect `capture-report.json`, then narrow
  `previewNameFilter`, `viewportFilter`, or `maxPreviewParameterValues`.
- Empty `nodes`: confirm the capture used `robolectric`, then check whether the
  preview exposes useful semantics.
- Weak source hints: fall back to screenshot, semantics nodes, and normal code
  search.
- Android wiring error: use an Android app/library or Compose Multiplatform
  module with an Android target.
- Font/resource mismatch: require `render.mode=robolectric`, confirm the
  selected Android variant, then use `docs/agent-usage.md` for `assetsDirs`,
  AGP resource artifact, and `fontProbe` guidance.
