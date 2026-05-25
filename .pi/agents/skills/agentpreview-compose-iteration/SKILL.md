---
name: agentpreview-compose-iteration
description: Use when converting Figma files, design screenshots, visual specs, or product UI requests into Jetpack Compose UI with AgentPreview available
---

# AgentPreview Compose Iteration

## Overview

Use AgentPreview as a tight visual feedback loop: small Compose edit, focused capture, compare `screenshot.png` plus `snapshot.json`, then iterate. Avoid mass-generating screens or rewriting unrelated code.

## Quick Reference

| Need | Command / file |
|---|---|
| List previews | `./gradlew :app:listComposePreviews` |
| Dry-run plan | `./gradlew :app:captureComposePreviews -PagentPreview.previewNameFilter=Login -PagentPreview.viewportFilter=phone -PagentPreview.maxPreviewParameterValues=1 -PagentPreview.maxCaptures=1 -PagentPreview.dryRun=true` |
| Capture | Same command without `-PagentPreview.dryRun=true` |
| Outputs | `build/agentPreviewSnapshots/<preview-id>/<viewport>/screenshot.png`, `snapshot.json` |
| Report | `build/agentPreviewReports/capture-report.json` |

If this repo still needs scaffold rendering, append `-PagentPreview.fakeRenderer=true`; fake captures are not visual parity evidence.

## Workflow

1. **Extract design targets:** viewport, spacing, type, color, shape, imagery/icons, states, accessibility labels, and acceptance criteria from Figma/screenshot/spec/request.
2. **Pick a focused preview:** run `listComposePreviews`; choose the matching component/screen. If none exists, add a local preview near the target code instead of capturing the whole app.
3. **Dry-run first:** check `capture-report.json` for `selectedPreviewCount`, `plannedViewportCaptureCount`, filters, and failures. If unrelated work is planned, tighten `previewNameFilter`, `viewportFilter`, `maxCaptures`, or `maxPreviewParameterValues`.
4. **Edit narrowly:** reuse existing design-system tokens/components. Touch only the target composable, preview data/state, assets, or local styling needed for parity.
5. **Capture and compare:** inspect `screenshot.png` for visual hierarchy, density, alignment, colors, typography, shapes, shadows, and state. Inspect `snapshot.json.nodes` for text/contentDescription/roles/actions/bounds.
6. **Use layout tree hints:** inspect `snapshot.json.layoutTree` fields: `boundsDp`, `componentHint`, `sourceName`, `sourceFile`, `sourceLine`, `modifierHint`, `classHint`, `semantics`. Navigate via `sourceFile/sourceLine/sourceName` before broad search.
7. **Iterate:** recapture only the same filtered preview after each change until differences are fixed, intentional, or blocked by ambiguous design input.
8. **Verify:** run relevant tests/build/format for touched modules. Do not claim completion from screenshots alone.

## Decision Checkpoints

- Before editing: which preview, viewport, and `PreviewParameter` value represents the design?
- Before capture: does dry-run plan only intended previews/viewports?
- After capture: is the main mismatch visual styling, layout structure, missing content, or wrong state/data?
- Before broad changes: can existing tokens/components/parameters solve it?
- Before done: are PNG, semantics, layout tree, and tests/build acceptable?

## Parameters and Safety

- `PreviewParameter` multiplies outputs; start with `-PagentPreview.maxPreviewParameterValues=1`, then expand only for required states.
- Use `-PagentPreview.previewNameFilter=<substring>` and `-PagentPreview.viewportFilter=<viewport>` for focused capture.
- Use `-PagentPreview.maxCaptures=1` while exploring and `-PagentPreview.dryRun=true` before expensive runs.
- Read reports after dry-runs/failures: skipped, planned, captured, failed counts, filters, and messages.
- Do not modify unrelated components, global themes, or tokens for one preview unless the design-system change is explicitly requested.
- Keep previews deterministic: fixed data/state, stable images, stable clocks.

## Common Mistakes

| Mistake | Fix |
|---|---|
| Capturing everything | Filter preview/viewport and set `maxCaptures=1` |
| Judging only the PNG | Also inspect semantics and `layoutTree` source/bounds |
| Global theme edits for local mismatch | Prefer existing tokens or local component changes |
| PreviewParameter explosion | Start with one value; expand intentionally |
