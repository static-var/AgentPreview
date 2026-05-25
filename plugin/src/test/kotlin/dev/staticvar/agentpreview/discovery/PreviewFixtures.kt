/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

@Preview(name = "Phone", group = "Auth", widthDp = 393, heightDp = 852)
annotation class PhonePreview

@Preview(name = "Small", group = "Multi", widthDp = 320, heightDp = 640)
@Preview(name = "Large", group = "Multi", widthDp = 800, heightDp = 1280)
annotation class MultiDevicePreview

@Preview(name = "Phone", group = "Duplicate", widthDp = 393, heightDp = 852)
@Preview(name = "Phone", group = "Duplicate", widthDp = 411, heightDp = 891)
annotation class DuplicateNamePreview

@Preview(
    name = "Login",
    group = "Auth",
    widthDp = 411,
    heightDp = 891,
    locale = "en",
    uiMode = 33,
    fontScale = 1.2f,
    showBackground = true,
    backgroundColor = 0xFFFF_FFFFL,
)
fun loginPreview() = Unit

object ProviderExecutionProbe {
    var instantiationCount: Int = 0
}

class StringProvider : PreviewParameterProvider<String> {
    init {
        ProviderExecutionProbe.instantiationCount++
    }

    override val values: Sequence<String> = sequenceOf("first", "second")
}

class InfiniteStringProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = generateSequence(0) { it + 1 }.map { "value-$it" }
}

class EmptyStringProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = emptySequence()
}

@Preview(name = "Parameterized")
fun parameterizedPreview(
    @PreviewParameter(StringProvider::class) name: String,
) {
    check(name.isNotEmpty())
}

object PreviewFixtureObject {
    @PhonePreview
    fun objectPreview() = Unit

    @MultiDevicePreview
    fun multiPreview() = Unit

    @DuplicateNamePreview
    fun duplicateNamePreview() = Unit
}
