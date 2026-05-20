/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.spike

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Login", group = "Auth", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun LoginPreview() {
    Column(modifier = Modifier.padding(24.dp).semantics { contentDescription = "Login screen" }) {
        Text("Welcome back", modifier = Modifier.testTag("headline"))
        Button(onClick = {}, modifier = Modifier.testTag("continue_button")) {
            Text("Continue")
        }
    }
}
