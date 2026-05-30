# KMP Design System Sample

This sample validates AgentPreview against an Android Kotlin Multiplatform library module with commonMain design-system previews and no Android app module.

Run from the repository root with the root Gradle wrapper:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/kmp-design-system :designSystem:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/kmp-design-system :designSystem:captureComposePreviews -PagentPreview.fakeRenderer=true
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/kmp-design-system :designSystem:captureComposePreviews
```

Adjust `ANDROID_HOME` for your local Android SDK location. For discovery-only debugging, keep `-PagentPreview.fakeRenderer=true` on the capture command.

Expected outputs are written under:

```text
samples/kmp-design-system/designSystem/build/agentPreviewSnapshots/
```
