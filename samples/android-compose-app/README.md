# Android Compose Sample

This sample validates AgentPreview against a real Android Compose application module.

The sample uses `includeBuild("../..")` so it exercises this repository checkout. Published users should apply `id("dev.staticvar.agentpreview") version "0.1.0"` in their Android app or library module instead.

Run from the repository root with the root Gradle wrapper:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/android-compose-app :app:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew -p samples/android-compose-app :app:captureComposePreviews
```

SDK lookup also checks `ANDROID_SDK_ROOT` and root `local.properties` `sdk.dir`. Install `platforms;android-35`. For discovery-only debugging, add `-PagentPreview.fakeRenderer=true`.

Expected outputs are written under:

```text
samples/android-compose-app/app/build/agentPreviewSnapshots/
```
