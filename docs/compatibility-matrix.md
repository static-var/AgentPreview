# AgentPreview Compatibility Matrix

Use `scripts/agentpreview-compatibility-matrix.sh` to generate disposable projects and test AgentPreview against different Gradle, AGP, Kotlin, Compose, CMP, and Android SDK configurations.

The harness creates one temporary project per matrix cell under `build/agentpreview-compat`, creates a Gradle wrapper for that cell, includes this checkout as an included build, and runs:

```bash
<module>:listComposePreviews
<module>:captureComposePreviews -PagentPreview.fakeRenderer=true
```

Fake rendering is the default because it proves Gradle wiring, preview discovery, classpath setup, and task execution without paying the cost of real Robolectric rendering for every cell. Add `--real` when you need to prove screenshot/resource behavior for a smaller set of cells.

Because the generated projects use this checkout through Gradle `includeBuild`, the lowest practical Gradle version is constrained by this repository's own included builds and samples. To probe lower Gradle versions than the checkout can evaluate, publish the plugin artifact first and adapt the generated project to apply the published version instead of using `includeBuild`.

## Commands

List the default current-version matrix:

```bash
scripts/agentpreview-compatibility-matrix.sh --list
```

Run the quick matrix:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk scripts/agentpreview-compatibility-matrix.sh --quick
```

Run one cell and keep its generated project for debugging:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk scripts/agentpreview-compatibility-matrix.sh \
  --case android-current \
  --keep
```

Run real Robolectric capture for the selected cell:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk scripts/agentpreview-compatibility-matrix.sh \
  --case android-current \
  --real \
  --keep
```

Add probing cells that may expose upstream version incompatibilities:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk scripts/agentpreview-compatibility-matrix.sh --extended
```

By default, the script uses `build-brief` when available. Set `AGENTPREVIEW_COMPAT_USE_BUILD_BRIEF=false` to run the generated wrappers directly when debugging wrapper or build-brief behavior.

## Output

The harness writes:

- `build/agentpreview-compat/report.md`: Markdown table for humans.
- `build/agentpreview-compat/summary.tsv`: tab-separated results for scripts.
- `build/agentpreview-compat/logs/<case>/wrapper.log`: wrapper creation output.
- `build/agentpreview-compat/logs/<case>/fake.log`: fake capture output.
- `build/agentpreview-compat/logs/<case>/real.log`: real capture output when `--real` is used.
- `build/agentpreview-compat/projects/<case>/`: generated project only when `--keep` is used.

## Reading Failures

Classify failures before changing plugin code:

- Wrapper failure: Gradle distribution or bootstrap problem.
- Plugin resolution failure: included-build or version plugin-management problem.
- Android/Kotlin/CMP configuration failure: upstream version combination is invalid before AgentPreview runs.
- `listComposePreviews` failure: discovery, classpath, or variant wiring problem.
- Fake `captureComposePreviews` failure: capture planning, preview parameter, output, or task wiring problem.
- Real capture failure: renderer, Robolectric, Android resources/assets, or runtime classpath problem.

Prefer narrowing from quick matrix failures first, then dispatch independent investigations by failing project shape or version family.
