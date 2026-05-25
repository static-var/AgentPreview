# Compose Multiplatform Sample

This sample validates AgentPreview against a real Compose Multiplatform application module routed through its Android target.

Run from the repository root with the root Gradle wrapper:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/cmp-compose-app :composeApp:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/cmp-compose-app :composeApp:captureComposePreviews
```

Adjust `ANDROID_HOME` for your local Android SDK location. For discovery-only debugging, add `-PagentPreview.fakeRenderer=true` to the capture command.

Expected outputs are written under:

```text
samples/cmp-compose-app/composeApp/build/agentPreviewSnapshots/
```
