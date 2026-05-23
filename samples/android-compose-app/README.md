# Android Compose Sample

This sample validates AgentPreview discovery and fake capture against a real Android Compose application module.

```bash
./gradlew :app:listComposePreviews
./gradlew :app:captureComposePreviews -PagentPreview.fakeRenderer=true
```

Expected outputs are written under:

```text
app/build/agentPreviewSnapshots/
```
