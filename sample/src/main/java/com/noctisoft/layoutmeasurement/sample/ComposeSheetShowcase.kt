package com.noctisoft.layoutmeasurement.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeSheetShowcase() {
    var open by rememberSaveable { mutableStateOf(false) }
    Button(onClick = { open = true }, modifier = Modifier.testTag("show_compose_sheet")) {
        Text(stringResource(R.string.show_compose_sheet))
    }
    if (open) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = state) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.compose_sheet_title), Modifier.testTag("modal_sheet_title"), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.sheet_instructions))
                SheetColorCard("modal_sheet_colors")
                Row {
                    Text(stringResource(R.string.sheet_box_a), Modifier.testTag("modal_box_a").width(100.dp).height(48.dp).background(Color(0xFFDDE7FF)).padding(8.dp))
                    Spacer(Modifier.width(24.dp))
                    Text(stringResource(R.string.sheet_box_b), Modifier.testTag("modal_box_b").width(100.dp).height(48.dp).background(Color(0xFFE4F5E8)).padding(8.dp))
                }
                Text(stringResource(R.string.sheet_gap_hint))
                ContainedGapSample()
                Button(onClick = { open = false }, modifier = Modifier.testTag("close_compose_sheet")) {
                    Text(stringResource(R.string.showcase_close_sheet))
                }
            }
        }
    }
}

@Composable
internal fun SheetColorCard(tag: String) {
    Text(
        stringResource(R.string.sheet_compose_colors),
        modifier = Modifier.testTag(tag).fillMaxWidth()
            .background(Color(0xFFF1EAFE)).border(2.dp, Color(0xFF4F46E5)).padding(12.dp),
        color = Color(0xFF25236D),
        fontSize = 18.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        letterSpacing = 0.02.em,
    )
}
