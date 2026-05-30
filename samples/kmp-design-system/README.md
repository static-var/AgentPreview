# KMP Design System Sample

This sample validates AgentPreview against an Android Kotlin Multiplatform library module with commonMain design-system previews and no Android app module.

The sample uses `includeBuild("../..")` so it exercises this repository checkout. Published users should apply `id("dev.staticvar.agentpreview") version "0.1.0"` in their Android KMP library or design-system module instead.

Run from the repository root with the root Gradle wrapper:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/kmp-design-system :designSystem:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/kmp-design-system :designSystem:captureComposePreviews
```

Adjust `ANDROID_HOME` for your local Android SDK location. Add `-PagentPreview.fakeRenderer=true` to the capture command only for discovery/debugging; do not use fake-renderer output as visual evidence.

Expected outputs are written under:

```text
samples/kmp-design-system/designSystem/build/agentPreviewSnapshots/
```
