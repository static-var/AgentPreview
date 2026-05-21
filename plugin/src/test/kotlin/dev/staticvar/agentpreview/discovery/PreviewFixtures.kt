/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.discovery

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Phone", group = "Auth", widthDp = 393, heightDp = 852)
annotation class PhonePreview

@Preview(
    name = "Login",
    group = "Auth",
    widthDp = 411,
    heightDp = 891,
    locale = "en",
    uiMode = 33,
    fontScale = 1.2f,
)
fun loginPreview() = Unit

object PreviewFixtureObject {
    @PhonePreview
    fun objectPreview() = Unit
}
