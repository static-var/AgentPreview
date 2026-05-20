/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.cmp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Profile", group = "Account", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun ProfilePreview() {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Static Var", modifier = Modifier.testTag("profile_name"))
        Text("Compose Multiplatform preview", modifier = Modifier.testTag("profile_subtitle"))
    }
}
