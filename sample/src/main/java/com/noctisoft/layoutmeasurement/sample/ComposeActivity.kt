package com.noctisoft.layoutmeasurement.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ComposeScreen() }
    }
}

@Composable
fun ComposeScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Compose screen", Modifier.testTag("compose_title"))
        Spacer(Modifier.height(24.dp))
        Button(onClick = {}, Modifier.testTag("compose_button").size(160.dp, 56.dp)) {
            Text("Tagged button")
        }
        Spacer(Modifier.height(24.dp))
        // Ordinary styling and a testTag; no inspector-specific color annotation is required.
        Text(
            "Inspect my colors",
            modifier = Modifier.testTag("compose_colors")
                .background(Color(0xFFF1EAFE))
                .border(2.dp, Color(0xFF4F46E5))
                .padding(12.dp),
            color = Color(0xFF25236D),
        )
        Spacer(Modifier.height(32.dp))
        // Deliberately untagged: must NOT appear as its own node (spec).
        Button(onClick = {}) { Text("Untagged button") }
    }
}
