# Android Compose Sample

This sample validates AgentPreview against a real Android Compose application module.

Run from the repository root with the root Gradle wrapper:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/android-compose-app :app:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/android-compose-app :app:captureComposePreviews
```

Adjust `ANDROID_HOME` for your local Android SDK location. For discovery-only debugging, add `-PagentPreview.fakeRenderer=true` to the capture command.

Expected outputs are written under:

```text
samples/android-compose-app/app/build/agentPreviewSnapshots/
```
