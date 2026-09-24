package com.noctisoft.layoutmeasurement.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ordinary tagged Compose nodes; no dependency on inspector APIs in shared sample code. */
@Composable
internal fun ContainedGapSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.contained_gap_compose_title), fontSize = 18.sp)
        Text(stringResource(R.string.contained_gap_instructions))
        Box(
            modifier = Modifier.fillMaxWidth().height(160.dp)
                .testTag("gap_parent_compose")
                .background(Color(0xFFF1EAFE)).border(2.dp, Color(0xFF4F46E5))
                // Physical edges keep the expected left/right distances stable in RTL.
                .absolutePadding(left = 24.dp, top = 32.dp, right = 40.dp, bottom = 48.dp),
        ) {
            Box(Modifier.fillMaxSize().testTag("gap_inner_compose").background(Color(0xFFE4F5E8)), contentAlignment = Alignment.Center) {
                // Untagged text leaves the whole inner box as the selectable Compose node.
                Text(stringResource(R.string.contained_gap_inner), color = Color(0xFF25236D), textAlign = TextAlign.Center)
            }
        }
        Text(stringResource(R.string.contained_gap_expected))
    }
}
