# Android Compose Sample

This sample validates AgentPreview discovery and fake capture against a real Android Compose application module.

Run from the repository root with the root Gradle wrapper:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/android-compose-app :app:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/android-compose-app :app:captureComposePreviews -PagentPreview.fakeRenderer=true
```

Adjust `ANDROID_HOME` for your local Android SDK location.

Expected outputs are written under:

```text
samples/android-compose-app/app/build/agentPreviewSnapshots/
```
