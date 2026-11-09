package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorFindBar(
    query: String,
    onQuery: (String) -> Unit,
    replacement: String,
    onReplacement: (String) -> Unit,
    matchCase: Boolean,
    onMatchCase: (Boolean) -> Unit,
    wholeWord: Boolean,
    onWholeWord: (Boolean) -> Unit,
    total: Int,
    index: Int,
    limited: Boolean,
    replaceMode: Boolean,
    onReplaceMode: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
    enabled: Boolean,
    editable: Boolean,
) {
    val compact = LocalConfiguration.current.screenHeightDp < 500
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = if (compact) 140.dp else 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query,
                    { onQuery(it.take(256)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled,
                    label = { Text(tr("Find in document", "दस्तावेज़ में खोजें")) },
                    suffix = { Text(if (limited) "$index / $total+" else "$index / $total") },
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, tr("Close search", "खोज बंद करें"))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (editable)
                    TextButton(onClick = { onReplaceMode(!replaceMode) }) {
                        Text(
                            if (replaceMode) tr("Hide replace", "बदलना छिपाएँ")
                            else tr("Replace", "बदलें")
                        )
                    }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPrevious, enabled = enabled && total > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, tr("Previous match", "पिछला परिणाम"))
                }
                IconButton(onClick = onNext, enabled = enabled && total > 0) {
                    Icon(Icons.Rounded.KeyboardArrowDown, tr("Next match", "अगला परिणाम"))
                }
            }
            if (replaceMode && editable) {
                OutlinedTextField(
                    replacement,
                    { onReplacement(it.take(10000)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    label = {
                        Text(tr("Replace with (blank deletes)", "इससे बदलें (खाली रखने पर हटेगा)"))
                    },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onReplace, enabled = enabled && total > 0) {
                        Text(tr("Replace one", "एक बदलें"))
                    }
                    TextButton(onClick = onReplaceAll, enabled = enabled && total > 0 && !limited) {
                        Text(tr("Replace all", "सभी बदलें"))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = matchCase,
                    onClick = { onMatchCase(!matchCase) },
                    label = { Text(tr("Match case", "अक्षर केस मिलाएँ")) },
                )
                FilterChip(
                    selected = wholeWord,
                    onClick = { onWholeWord(!wholeWord) },
                    label = { Text(tr("Whole word", "पूरा शब्द")) },
                )
            }
        }
    }
}
