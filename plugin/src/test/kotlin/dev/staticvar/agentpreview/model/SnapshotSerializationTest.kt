package dev.staticvar.agentpreview.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnapshotSerializationTest {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    @Test
    fun `snapshot serializes compact agent model`() {
        val snapshot = PreviewSnapshot(
            schemaVersion = 1,
            preview = PreviewMetadata(
                id = ":app:commonMain:LoginPreview",
                name = "Login",
                group = "Auth",
                source = "LoginPreview.kt:12",
                sourceSet = "commonMain",
            ),
            viewport = Viewport(width = 393, height = 852, density = 3.0f),
            nodes = listOf(
                SnapshotNode(
                    id = "n1",
                    role = "button",
                    text = "Continue",
                    bounds = Bounds(x = 48, y = 720, width = 297, height = 56),
                    actions = listOf("click"),
                    tag = "continue_button",
                    source = "LoginScreen.kt:84",
                )
            )
        )

        val encoded = json.encodeToString(PreviewSnapshot.serializer(), snapshot)

        assertTrue(encoded.contains("\"schemaVersion\": 1"))
        assertTrue(encoded.contains("\"screenshot\"").not())
        assertTrue(encoded.contains("\"rawSemantics\"").not())
        assertEquals(snapshot, json.decodeFromString(PreviewSnapshot.serializer(), encoded))
    }
}
