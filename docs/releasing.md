# Releasing

`preview-scanner` is published to Maven Central as `dev.staticvar:preview-scanner`.

The Gradle plugin is published to the Gradle Plugin Portal as `dev.staticvar.agentpreview`.

## Maintainer checklist

1. Choose a semantic release version, for example `0.1.0` or `0.1.0-rc.1`.
2. Release `preview-scanner` first with the manual **Publish preview-scanner** workflow for the same version.
3. Confirm the scanner artifact is available from Maven Central.
4. Run the normal local quality gate when practical:

```bash
./gradlew spotlessCheck detekt :plugin:test :preview-scanner:test validatePlugins
```

5. Optionally validate non-portal publication locally:

```bash
./gradlew :preview-scanner:publishToMavenLocal :plugin:publishToMavenLocal
```

6. Run the manual-only **Publish Gradle Plugin** workflow with the same version.

The plugin depends on the published `dev.staticvar:preview-scanner:0.1.0` artifact. Do not place secrets in source files or docs, and do not run `publishPlugins` locally unless you are intentionally performing a maintainer release.
