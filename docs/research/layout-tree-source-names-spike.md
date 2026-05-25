# Layout tree source/composable names spike

## Goal

Research whether experimental layout tree nodes can get better nullable source/composable names without requiring application `testTag`s. This builds on the runtime `LayoutNode` reflection spike, which already proved no-tag bounds extraction for Android Compose and CMP Android targets.

## Probe implementation

Added isolated spike tests only:

- `spikes/renderer-android-compose/app/src/test/java/dev/staticvar/agentpreview/spike/CompositionToolingDataProbeTest.kt`
- `spikes/renderer-cmp-compose/composeApp/src/androidUnitTest/kotlin/dev/staticvar/agentpreview/cmp/CmpCompositionToolingDataProbeTest.kt`

The tests render a nested no-tag tree wrapped in Compose tooling's internal `Inspectable` recorder:

```kotlin
val record = CompositionDataRecord.create()
setContent {
    Inspectable(record) {
        MaterialTheme {
            SourceNamesProbeScreen()
        }
    }
}
```

Then they:

1. Convert each `record.store` `CompositionData` entry to a tooling `Group` tree with `androidx.compose.ui.tooling.data.asTree()`.
2. Dump `Group.name`, `SourceLocation.sourceFile`, `SourceLocation.lineNumber`, `Group.box`, `Group.identity`, and `NodeGroup.node` class/identity.
3. Reflect `AndroidComposeView.getRoot()` and traverse runtime `LayoutNode`s, as in the earlier no-test-tag spike.
4. Check whether any `NodeGroup.node` object identity matches a reflected runtime `LayoutNode` identity.

`CompositionDataRecord` and `Inspectable` are Kotlin-internal to `androidx.compose.ui:ui-tooling`, so the spike uses `@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")`. `ui-tooling-data` APIs are annotated `@UiToolingDataApi`, so the tests opt in. This is intentionally isolated to spike tests.

CMP needed only a spike-test dependency on `androidx.compose.ui:ui-tooling:1.11.2` for `androidUnitTest`; no production dependency was added.

## Sample output excerpt

Android probe output included custom composables, framework composables, source locations, boxes, and separate node groups:

```json
{
  "name": "SourceNamesProbeScreen",
  "sourceFile": "CompositionToolingDataProbeTest.kt",
  "lineNumber": 56,
  "box": "IntRect.fromLTRB(16, 16, 304, 454)",
  "identity": "androidx.compose.runtime.composer.gapbuffer.GapAnchor@385575505",
  "layoutNodeIdentity": null,
  "nodeClass": null,
  "children": [
    {
      "name": "Column",
      "sourceFile": "CompositionToolingDataProbeTest.kt",
      "lineNumber": 104,
      "box": "IntRect.fromLTRB(16, 16, 304, 454)",
      "identity": "androidx.compose.runtime.composer.gapbuffer.SourceInformationSlotTableGroupIdentity@1976138797",
      "layoutNodeIdentity": null,
      "nodeClass": null
    },
    {
      "name": null,
      "sourceFile": null,
      "lineNumber": -1,
      "box": "IntRect.fromLTRB(16, 16, 304, 454)",
      "identity": null,
      "layoutNodeIdentity": 868553805,
      "nodeClass": "androidx.compose.ui.node.LayoutNode"
    }
  ]
}
```

Other observed names included `SourceNamesProbeCard`, `Card`, `Row`, `Text`, `Spacer`, `Button`, `MaterialTheme`, and Material internals. CMP Android target produced the same kind of output for `CmpSourceNamesProbeScreen`, `Column`, `Row`, and `Button`.

## Answers to research questions

### 1. Can Compose tooling/composition data expose composable names?

Yes, when content is wrapped with tooling inspection and source information is present. The `androidx.compose.ui.tooling.data.Group` tree exposes `name` values for custom composables (`SourceNamesProbeScreen`, `SourceNamesProbeCard`) and Compose library calls (`Column`, `Row`, `Button`, `Text`, `Card`, `MaterialTheme`).

This data is not available from the runtime `LayoutNode` tree alone. `LayoutNode` still gives implementation hints such as measure-policy classes; the tooling `Group` tree gives composable call names.

### 2. Can it expose source file/line info? Does it require compiler/source-info flags?

Yes. `Group.location` exposes `SourceLocation(sourceFile, lineNumber, offset, length, packageHash)`. The spike tests saw local file/line values such as `CompositionToolingDataProbeTest.kt:56` without adding explicit compiler options in debug/unit-test builds.

However, the mechanism depends on Compose compiler source information emitted into the slot table. Production should treat these fields as nullable and build-configuration-sensitive. If source information is disabled/stripped, names, file, line, or call boxes may be absent or less useful.

### 3. Can composition/tooling groups be correlated to `LayoutNode` tree nodes?

Partially, and better than bounds-only:

- Tooling `NodeGroup.node` can be the actual `androidx.compose.ui.node.LayoutNode` object. In both probes, `System.identityHashCode(NodeGroup.node)` matched an identity from the reflected `AndroidComposeView.root` layout tree.
- Named composable `CallGroup`s are usually ancestors/siblings around separate unnamed `NodeGroup`s, not the same object. A `Column` call group may have a useful source name and box, while an adjacent/descendant unnamed node group owns the actual `LayoutNode` identity.
- `Group.box` uses the same pixel coordinate space as runtime layout bounds, so ancestry plus exact/near bounds and traversal order can correlate source call groups to layout nodes.
- Semantics id is still useful for joining runtime layout nodes to semantics, but the tooling group API does not directly expose semantics ids in the observed data.

Best production correlation strategy: build both trees, map exact `NodeGroup.node` identity to runtime layout nodes first, then propagate nearest named/source ancestor to node groups. Use bounds and preorder as fallback, and keep confidence nullable/diagnostic.

### 4. Does it work in Android sample, CMP Android target, and VLR designsystem focused preview?

- Android Compose spike: yes; focused test passed and dumped custom/framework names, source file/line, and layout-node identity matches.
- CMP Android target spike: yes; focused test passed after adding `ui-tooling` to `androidUnitTest`. CMP Android still renders through `AndroidComposeView` and exposes the same tooling/layout objects.
- VLR designsystem focused preview: not verified in this repository. No VLR/designsystem focused preview source was present in the checked-out project. The result is likely similar for an Android target if it can render in the same Robolectric/Compose tooling environment.

### 5. What dependencies/classes/APIs are involved and how stable are they?

Observed APIs/classes:

- `androidx.compose.ui:ui-tooling` (`1.11.2` in the spikes)
  - `androidx.compose.ui.tooling.CompositionDataRecord` - Kotlin-internal.
  - `androidx.compose.ui.tooling.Inspectable` - Kotlin-internal composable wrapper.
- `androidx.compose.ui:ui-tooling-data` (`1.11.2`, transitive from `ui-tooling`)
  - `androidx.compose.ui.tooling.data.asTree(CompositionData): Group` - public but `@UiToolingDataApi`.
  - `Group`, `CallGroup`, `NodeGroup`, `SourceLocation`, `ParameterInformation` - tooling API, not app-stability API.
- `androidx.compose.runtime.tooling`
  - `CompositionData`, `CompositionGroup`, `CompositionInstance` - runtime tooling interfaces.
- Runtime layout correlation still uses internal/reflected Android Compose UI objects:
  - `AndroidComposeView.getRoot()`
  - `LayoutNode.getZSortedChildren()` / `get_children$ui`

Stability assessment: high risk for direct production use. The data is intended for tools/inspection, `CompositionDataRecord`/`Inspectable` are internal, `ui-tooling-data` is explicitly tooling-only, and source information depends on compiler output. This should be optional and version-tolerant.

### 6. What would a production-safe nullable schema addition look like?

Avoid replacing existing `componentHint`. Add nullable, best-effort fields under experimental layout nodes, for example:

```json
{
  "componentHint": "androidx.compose.foundation.layout.ColumnMeasurePolicy",
  "sourceName": "Column",
  "sourceFile": "LoginPreview.kt",
  "sourceLine": 42,
  "sourceColumn": null,
  "sourcePackageHash": 123456,
  "sourceHintKind": "tooling-nearest-app-ancestor"
}
```

Recommended semantics:

- `sourceName`: nullable composable/tooling group name correlated to the layout node, preferring app/source-file groups over Compose runtime internals.
- `sourceFile`, `sourceLine`, `sourceColumn`, `sourcePackageHash`: nullable source location parts from `SourceLocation`.
- `sourceHintKind`: optional nullable diagnostic enum/string such as `tooling-node-identity`, `tooling-nearest-app-ancestor`, `tooling-sibling-preorder-app`, `tooling-useful-framework-ancestor`, `tooling-framework-ancestor`, or `preview-entrypoint-fallback`.

Do not require these fields for consumers. Tooling hints may still fall back to framework/internal groups when no app group can be correlated within ancestry/preorder/bounds constraints; CMP Android-target captures may remain preview-entrypoint-fallback-only when composition tooling data is unavailable. Do not fail snapshot extraction when tooling data is unavailable.

## Production recommendation

Do not make a broad production schema/runtime change in this spike. For a production follow-up:

1. Keep the runtime `LayoutNode` extractor as the source of layout nodes, bounds, semantics ids, and measure-policy/modifier fallback hints.
2. Add an optional source-name enricher that is enabled only in the render process when Compose tooling APIs are present.
3. Wrap rendered content in a tiny inspection recorder abstraction. Because `Inspectable`/`CompositionDataRecord` are internal, evaluate whether production should:
   - use suppressed internal access in a strictly isolated adapter,
   - invoke the internal APIs reflectively,
   - or render through `ComposeViewAdapter`/preview tooling only when available.
4. Traverse `ui-tooling-data.asTree()` and correlate:
   - exact `NodeGroup.node === LayoutNode`,
   - nearest named/source ancestor,
   - then bounds/preorder fallback.
5. Emit only nullable source fields and keep existing `componentHint` as fallback.
6. Add version-matrix tests across the Compose versions AgentPreview supports before enabling by default.
