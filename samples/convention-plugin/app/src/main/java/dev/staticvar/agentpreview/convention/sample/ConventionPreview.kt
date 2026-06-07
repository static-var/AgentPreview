/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.convention.sample

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Convention Preview", widthDp = 240, heightDp = 120, showBackground = true)
@Composable
fun ConventionPreview() {
    MaterialTheme {
        Button(onClick = {}) {
            Text("Convention")
        }
    }
}
