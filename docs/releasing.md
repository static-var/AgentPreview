# Releasing

`preview-scanner` is published to Maven Central as `dev.staticvar:preview-scanner`.

The Gradle plugin is published to the Gradle Plugin Portal as `dev.staticvar.agentpreview`.

## Maintainer checklist

1. Choose a semantic release version, for example `0.1.1` or `0.2.0`.
2. Decide whether the release needs a new `preview-scanner` artifact.
   - If scanner code changed or the plugin should depend on a newer scanner, release `preview-scanner` first with the manual **Publish preview-scanner** workflow.
   - The workflow publishes to Maven Central and creates a `preview-scanner-v<version>` git tag. It does not create a GitHub Release.
   - If scanner code did not change, keep the plugin dependency pinned to the existing scanner version.
3. Confirm any scanner artifact required by the plugin is available from Maven Central.
4. Run the normal local quality gate when practical:

```bash
./gradlew spotlessCheck detekt :plugin:test :preview-scanner:test validatePlugins
```

5. Optionally validate non-portal publication locally:

```bash
./gradlew :preview-scanner:publishToMavenLocal :plugin:publishToMavenLocal
```

6. Run the manual-only **Publish Gradle Plugin** workflow with the chosen plugin version.
   - The workflow publishes to the Gradle Plugin Portal, creates an `agentpreview-gradle-plugin-v<version>` GitHub release tag, and generates release notes.

The current plugin build depends on `dev.staticvar:preview-scanner:0.1.0`. Bump that dependency intentionally before a plugin release if the release needs scanner changes. Do not place secrets in source files or docs, and do not run `publishPlugins` locally unless you are intentionally performing a maintainer release.
