# Releasing

`preview-scanner` is configured for Maven Central as `dev.staticvar:preview-scanner`.

## Maintainer checklist

Before publishing a release:

1. Verify the Maven Central namespace is active.
2. Confirm publishing and signing credentials are configured in the repository environment.
3. Run the normal quality gate locally or in CI:

```bash
build-brief ./gradlew spotlessCheck detekt :preview-scanner:test
```

4. Optionally validate publication metadata locally without publishing:

```bash
build-brief ./gradlew :preview-scanner:publishToMavenLocal
```

5. Use the manual GitHub Actions workflow **Publish preview-scanner** and enter the release version, for example `0.1.0`.

The workflow is manual-only and runs preview-scanner quality gates before publishing. Do not run Maven Central publishing commands locally unless you are intentionally performing a maintainer release.
