/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.scanner.fixtures

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

@Preview(name = "Phone", group = "Auth", widthDp = 393, heightDp = 852, showBackground = true)
annotation class PhonePreview

@Preview(name = "Small", group = "Multi", widthDp = 320, heightDp = 640)
@Preview(name = "Large", group = "Multi", widthDp = 800, heightDp = 1280)
annotation class MultiDevicePreview

@Preview(
    name = "Top Level",
    group = "Auth",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
    backgroundColor = 0xFFFF_FFFFL,
    fontScale = 1.2f,
    locale = "en",
    device = "spec:width=411dp,height=891dp",
    uiMode = 33,
)
fun topLevelPreview() = Unit

object ObjectPreviewFixtures {
    @PhonePreview
    fun objectPreview() = Unit

    object Nested {
        @Preview(name = "Nested", group = "Nested")
        fun nestedObjectPreview() = Unit
    }
}

class NameProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = sequenceOf("Ada", "Grace")
}

class ClassPreviewFixtures {
    @MultiDevicePreview
    fun classPreview() = Unit

    @Preview(name = "Parameterized")
    fun parameterizedPreview(
        @PreviewParameter(NameProvider::class, limit = 1) name: String,
    ) {
        check(name.isNotEmpty())
    }

    @Preview(name = "Needs Args")
    fun unsupportedPreview(name: String) {
        check(name.isNotEmpty())
    }

    @Preview(name = "Needs Int")
    fun unsupportedIntPreview(count: Int) {
        check(count > 0)
    }
}
