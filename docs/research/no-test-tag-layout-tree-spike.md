# No-test-tag Compose layout tree extraction spike

## Goal

Investigate whether AgentPreview can extract a composable/layout tree from Robolectric-rendered Compose content without requiring application code to add `testTag`s. The target output is richer than the current semantics-only snapshot: wrapper layouts, bounds in px and dp, and best-effort component/modifier/source hints.

## Prototype

Added focused spike tests:

- `spikes/renderer-android-compose/app/src/test/java/dev/staticvar/agentpreview/spike/LayoutTreeReflectionProbeTest.kt`
- `spikes/renderer-cmp-compose/composeApp/src/androidUnitTest/kotlin/dev/staticvar/agentpreview/cmp/CmpLayoutTreeReflectionProbeTest.kt`

Both tests render a nested no-tag tree:

```kotlin
Column(Modifier.fillMaxSize().padding(16.dp)) {
    Card(Modifier.padding(8.dp)) {
        Row(Modifier.padding(12.dp)) {
            Text("Welcome")
            Spacer(Modifier.size(8.dp))
            Button(onClick = {}) { Text("Continue") }
        }
    }
    Box(Modifier.size(24.dp).background(Color.Red))
}
```

The prototype finds `androidx.compose.ui.platform.AndroidComposeView`, reflects `getRoot()` to obtain the root `androidx.compose.ui.node.LayoutNode`, traverses `getZSortedChildren`, reads each node's `getCoordinates()`, `getMeasurePolicy()`, `getModifier()`, `getSemanticsId()`, and `getSemanticsConfiguration()`, then prints JSON with px and dp bounds.

Example Android output excerpt:

```json
{
  "semanticsId": "2",
  "componentHint": "androidx.compose.foundation.layout.ColumnMeasurePolicy",
  "modifierHint": "androidx.compose.ui.CombinedModifier",
  "boundsPx": {"x":16,"y":16,"width":288,"height":438},
  "boundsDp": {"x":16,"y":16,"width":288,"height":438},
  "children": [
    {
      "componentHint": "androidx.compose.foundation.layout.BoxMeasurePolicy",
      "boundsPx": {"x":24,"y":24,"width":209,"height":72},
      "children": [
        {
          "componentHint": "androidx.compose.foundation.layout.RowMeasurePolicy",
          "modifierHint": "androidx.compose.foundation.layout.PaddingElement",
          "boundsPx": {"x":36,"y":36,"width":185,"height":48},
          "children": [
            {"componentHint":"androidx.compose.foundation.text.EmptyMeasurePolicy","text":"[Welcome]"},
            {"componentHint":"androidx.compose.foundation.layout.SpacerMeasurePolicy"},
            {"componentHint":"androidx.compose.foundation.layout.BoxMeasurePolicy","role":"Button"}
          ]
        }
      ]
    },
    {
      "componentHint": "androidx.compose.foundation.layout.BoxKt$EmptyBoxMeasurePolicy$1",
      "boundsPx": {"x":16,"y":104,"width":24,"height":24}
    }
  ]
}
```

## Answers to research questions

1. **Can we extract a runtime Compose layout tree from Robolectric-rendered `AndroidComposeView`?**
   Yes. In current Compose versions used by the spikes, `AndroidComposeView.getRoot()` is reflectable and returns a `LayoutNode`. Children are available through `LayoutNode.getZSortedChildren()` as a `MutableVector`.

2. **Does it include wrapper layout nodes such as Column, Row, Box, Card, Spacer?**
   Mostly yes. `Column`, `Row`, `Box`, and `Spacer` appear as layout nodes via measure-policy class names such as `ColumnMeasurePolicy`, `RowMeasurePolicy`, `BoxMeasurePolicy`, and `SpacerMeasurePolicy`. Material `Card` does not appear as a stable `Card` component name; in the observed tree it manifests as lower-level `Box`/`Column`/surface implementation nodes. Material `Button` similarly appears as a `BoxMeasurePolicy` node with semantics role `Button` plus internal row/text children.

3. **Can we get bounds for each node in px?**
   Yes. The prototype uses `LayoutNode.getCoordinates()`, `getWidth()`, `getHeight()`, and the mangled `localToRoot-*` method with packed `Offset.Zero` to calculate root-relative px bounds. This worked in Robolectric after measure/layout/draw and `waitForIdle()`.

4. **Can we convert bounds to dp using runtime density reliably?**
   Yes for Android-target rendering. The prototype captures `LocalDensity.current.density` at render time and divides px values by density. AgentPreview's render harness already controls display metrics density, so production can store both the raw px bounds and dp bounds computed with the same runtime density/display metrics used for screenshot rendering. Use floats or rounded ints deliberately; the prototype rounds to ints for readability.

5. **Can we get composable names/source info from Compose tooling/composition data without adding tags?**
   Not from `LayoutNode` alone. Layout nodes expose implementation hints (`measurePolicy` class, modifier element class, semantics config), not original composable call names. Compose tooling/composition data is the likely route for source/composable names, but it requires adding tooling data dependencies and enabling compiler source information. This spike did not wire tooling-data traversal into the tests. Expect source/name extraction to be best-effort and build-configuration-sensitive, not a replacement for runtime layout traversal.

6. **Can we map semantics nodes onto layout/component nodes?**
   Yes, likely by `semanticsId` first and by bounds/tree order as fallback. `LayoutNode.getSemanticsId()` is visible by reflection, and semantics configurations are also visible on layout nodes. The prototype reads text/role directly from each layout node's `SemanticsConfiguration`, which avoids a separate join for basic data. A production extractor can also build the existing semantics tree from `SemanticsOwner` and correlate nodes by id, bounds, and preorder.

7. **Does the approach work for both Android app sample and CMP Android target sample?**
   Yes. The same reflection approach passed in both the Android app spike and the CMP Android target spike. CMP Android target still renders through `AndroidComposeView`, so the runtime layout-node path is effectively the same.

8. **What Compose compiler/runtime flags or dependencies are required?**
   For layout-node traversal and bounds: no test tags, no Compose tooling dependency, and no compiler source-information flag were required. Required runtime preconditions are Android Compose UI on the classpath, a measured/laided-out `AndroidComposeView`, and reflective access to internal Compose UI classes/methods.

   For composable names/source hints: expect to add `androidx.compose.ui:ui-tooling-data`/tooling support on the render classpath and enable Compose compiler source information in debuggable/test builds. This should be optional: the layout tree should still emit measure-policy/modifier/semantics hints when source information is absent.

9. **What stability/API risks exist?**
   High. This depends on internal Compose UI APIs and JVM-mangled method names (`get_children$ui`, `localToRoot-*`) that can change across Compose versions. Measure-policy class names are implementation details and do not provide stable component identity. Material components are lowered into implementation layouts, so names like `Card` may not be recoverable from layout nodes. Packed `Offset` decoding is Compose-internal representation knowledge. Reflection can also be blocked or behave differently if Compose internals change.

## Recommendation

Build production support as an optional, version-tolerant extractor layered alongside the existing semantics extractor:

1. Add an internal `LayoutTreeExtractor` invoked inside the Robolectric render process after draw/idle.
2. Reflect `AndroidComposeView.getRoot()` and traverse `LayoutNode.getZSortedChildren()`.
3. Emit experimental layout nodes with:
   - stable generated id and reflected `semanticsId`
   - `componentHint` from measure-policy class name
   - `modifierHint`/modifier element class names where cheap
   - semantics summary from `SemanticsConfiguration`
   - bounds in px and dp using runtime density
4. Correlate existing semantics snapshots by `semanticsId`; fall back to bounds/preorder when ids are missing.
5. Gate the feature behind an opt-in config/property until it is tested across a Compose version matrix.
6. Treat tooling/composition-data source names as a second phase. Add them only as nullable hints and keep the layout tree useful without them.

This should satisfy no-test-tag wrapper/bounds extraction while avoiding a hard production dependency on unstable source-name availability.
