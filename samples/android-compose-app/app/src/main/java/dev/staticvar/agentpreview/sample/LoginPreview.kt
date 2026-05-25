/*
 * MIT License
 *
 * Copyright (c) 2026 Shreyansh Lodha
 */
package dev.staticvar.agentpreview.sample

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

@Composable
fun LoginCard(email: String = "agent@example.com") {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = "Welcome back", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFECE6D8))
                            .padding(16.dp),
                ) {
                    Text(email)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {},
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("continue-button")
                            .semantics { contentDescription = "Continue to account" },
                ) {
                    Text("Continue")
                }
            }
        }
    }
}

@Preview(
    name = "Login",
    group = "Auth",
    widthDp = 393,
    heightDp = 852,
    showBackground = true,
    backgroundColor = 0xFFFFFBFE,
    fontScale = 1.15f,
    locale = "en-rUS",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Composable
fun LoginPreview() {
    LoginCard()
}

@Preview(name = "Responsive Login", group = "Auth", showBackground = true)
@Composable
fun ResponsiveLoginPreview() {
    LoginCard()
}

class LoginEmailProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = sequenceOf("agent@example.com", "designer@example.com")
}

@Preview(name = "Parameterized Login", group = "Auth", showBackground = true)
@Composable
fun ParameterizedLoginPreview(@PreviewParameter(LoginEmailProvider::class) email: String) {
    LoginCard(email)
}
