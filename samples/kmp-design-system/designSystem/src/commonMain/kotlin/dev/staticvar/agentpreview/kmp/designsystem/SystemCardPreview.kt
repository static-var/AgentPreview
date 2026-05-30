/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.kmp.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "System Card", group = "Design System", showBackground = true)
@Composable
fun SystemCardPreview() {
    DesignSystemSurface {
        SystemCard(
            title = "Preview pipeline",
            status = "Android target",
            value = "KMP lib",
        )
    }
}

@Preview(name = "Dense System Card", group = "Design System", widthDp = 320, heightDp = 220, showBackground = true)
@Composable
fun DenseSystemCardPreview() {
    DesignSystemSurface {
        SystemCard(
            title = "Color tokens",
            status = "commonMain",
            value = "Ready",
        )
    }
}

@Composable
private fun DesignSystemSurface(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(
            modifier =
                Modifier
                    .background(Color(0xFFF5F7FA))
                    .padding(20.dp)
                    .testTag("kmp_design_system_surface"),
            color = Color(0xFFF5F7FA),
        ) {
            content()
        }
    }
}

@Composable
private fun SystemCard(
    title: String,
    status: String,
    value: String,
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(status)
                Text(value, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
