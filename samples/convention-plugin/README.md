# AgentPreview Convention Plugin Sample

This sample shows how a consumer project can wrap AgentPreview in its own Gradle convention plugin.

The convention plugin lives in `build-logic` and applies `dev.staticvar.agentpreview` to the Android app module, then configures shared preview defaults through the existing `AgentPreviewExtension`.

Run:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:listComposePreviews
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:captureComposePreviews -PagentPreview.dryRun=true
```

Adjust `ANDROID_HOME` for your local Android SDK location. This sample uses `includeBuild("../..")` so it consumes the local AgentPreview checkout.
