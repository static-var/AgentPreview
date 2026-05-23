/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.cmp.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Dashboard", group = "CMP", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun DashboardPreview() {
    MaterialTheme {
        Column(
            modifier =
                Modifier
                    .background(Color(0xFFF7F3EA))
                    .padding(24.dp)
                    .testTag("cmp_dashboard"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "AgentPreview CMP",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("CommonMain preview discovered through the Android target")
            Card(shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Previews")
                    Text("Android-backed")
                }
            }
        }
    }
}
