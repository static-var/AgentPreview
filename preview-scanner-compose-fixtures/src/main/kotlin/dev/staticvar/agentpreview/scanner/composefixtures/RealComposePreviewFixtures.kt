/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
@file:Suppress("MatchingDeclarationName")

package dev.staticvar.agentpreview.scanner.composefixtures

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class RealNameProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = sequenceOf("Ada")
}

@Composable
@Preview
fun realComposeParameterizedPreview(
    @PreviewParameter(RealNameProvider::class, limit = 1) name: String,
) {
    check(name.isNotEmpty())
}
