/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnapshotSerializationTest {
    private val json =
        Json {
            prettyPrint = true
            explicitNulls = false
        }

    @Test
    fun `snapshot serializes compact agent model`() {
        val snapshot =
            PreviewSnapshot(
                schemaVersion = 2,
                preview =
                    PreviewMetadata(
                        id = ":app:commonMain:LoginPreview",
                        name = "Login",
                        group = "Auth",
                        source = "LoginPreview.kt:12",
                        sourceSet = "commonMain",
                    ),
                viewport = Viewport(width = 393, height = 852, density = 3.0f),
                nodes =
                    listOf(
                        SnapshotNode(
                            id = "n1",
                            role = "button",
                            text = "Continue",
                            bounds = Bounds(x = 48, y = 720, width = 297, height = 56),
                            actions = listOf("click"),
                            tag = "continue_button",
                            source = "LoginScreen.kt:84",
                        ),
                    ),
                render = SnapshotRenderMetadata(mode = "robolectric"),
            )

        val encoded = json.encodeToString(PreviewSnapshot.serializer(), snapshot)

        assertTrue(encoded.contains("\"schemaVersion\": 2"))
        assertTrue(encoded.contains("\"screenshot\"").not())
        assertTrue(encoded.contains("\"rawSemantics\"").not())
        assertTrue(encoded.contains("\"render\""))
        assertTrue(encoded.contains("\"mode\": \"robolectric\""))
        assertEquals(snapshot, json.decodeFromString(PreviewSnapshot.serializer(), encoded))
    }

    @Test
    fun `snapshot serializes experimental layout tree when present`() {
        val snapshot =
            PreviewSnapshot(
                schemaVersion = 2,
                preview =
                    PreviewMetadata(
                        id = "Preview",
                        name = "Preview",
                        sourceSet = "main",
                        previewParameter =
                            PreviewParameterDescriptor(
                                providerClassName = "dev.example.Provider",
                                parameterType = "kotlin.String",
                                index = 0,
                            ),
                    ),
                viewport = Viewport(width = 100, height = 50, density = 2.0f),
                nodes = emptyList(),
                layoutTree =
                    listOf(
                        SnapshotLayoutNode(
                            id = "layout-1",
                            boundsPx = Bounds(x = 8, y = 12, width = 40, height = 20),
                            boundsDp = DpBounds(x = 4.0f, y = 6.0f, width = 20.0f, height = 10.0f),
                            componentHint = "androidx.compose.foundation.layout.RowMeasurePolicy",
                            sourceName = "LoginButton",
                            sourceFile = "LoginPreview.kt",
                            sourceLine = 42,
                            sourceHintKind = "tooling-nearest-app-ancestor",
                            modifierHint = "androidx.compose.ui.Modifier",
                            classHint = "androidx.compose.ui.node.LayoutNode",
                            semanticsId = "7",
                            semantics =
                                SnapshotLayoutSemanticsSummary(
                                    text = "Continue",
                                    contentDescription = "Primary action",
                                    role = "Button",
                                    actions = listOf("OnClick"),
                                ),
                        ),
                    ),
            )

        val encoded = json.encodeToString(PreviewSnapshot.serializer(), snapshot)

        assertTrue(encoded.contains("\"previewParameter\""))
        assertTrue(encoded.contains("\"index\": 0"))
        assertTrue(encoded.contains("\"layoutTree\""))
        assertTrue(encoded.contains("\"boundsPx\""))
        assertTrue(encoded.contains("\"boundsDp\""))
        assertTrue(encoded.contains("\"componentHint\""))
        assertTrue(encoded.contains("\"sourceName\""))
        assertTrue(encoded.contains("\"sourceFile\""))
        assertTrue(encoded.contains("\"sourceLine\": 42"))
        assertTrue(encoded.contains("\"sourceHintKind\""))
        assertEquals(snapshot, json.decodeFromString(PreviewSnapshot.serializer(), encoded))
    }

    @Test
    fun `snapshot decodes without render metadata or layout tree for backwards compatibility`() {
        val encoded =
            """
            {
              "schemaVersion": 1,
              "preview": {
                "id": ":app:commonMain:LoginPreview",
                "name": "Login",
                "group": null,
                "source": null,
                "sourceSet": "commonMain"
              },
              "viewport": {
                "platform": "android",
                "name": "phone",
                "width": 393,
                "height": 852,
                "density": 3.0
              },
              "nodes": []
            }
            """.trimIndent()

        val snapshot = json.decodeFromString(PreviewSnapshot.serializer(), encoded)

        assertEquals(null, snapshot.render)
        assertEquals(emptyList<SnapshotLayoutNode>(), snapshot.layoutTree)
    }
}
