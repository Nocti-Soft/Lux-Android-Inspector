package com.noctisoft.layoutmeasurement.sample

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

class MixedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_mixed)
        findViewById<ComposeView>(R.id.compose_island).setContent {
            Row {
                Text(
                    "island A",
                    Modifier.testTag("island_a").size(120.dp, 40.dp)
                        .background(Color(0xFF25236D))
                        .border(2.dp, Color(0xFF4F46E5)),
                    color = Color.White,
                )
                Spacer(Modifier.width(20.dp))
                Text("island B", Modifier.testTag("island_b").size(120.dp, 40.dp))
            }
        }
    }
}
