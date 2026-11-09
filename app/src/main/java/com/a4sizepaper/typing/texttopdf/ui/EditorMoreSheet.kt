package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditorMoreSheet(
    title: String,
    review: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    val c = AppTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Document options", "दस्तावेज़ विकल्प"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = c.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, tr("Close", "बंद करें"))
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = c.cardFooter,
                border = BorderStroke(1.dp, c.line),
            ) {
                Column {
                    MoreRow(
                        Icons.Rounded.Tune,
                        tr("Page setup", "पेज सेटअप"),
                        tr(
                            "Orientation, margins and default text",
                            "दिशा, मार्जिन और डिफ़ॉल्ट टेक्स्ट",
                        ),
                    ) {
                        onAction("page")
                    }
                    HorizontalDivider(color = c.line)
                    MoreRow(
                        Icons.Rounded.FindReplace,
                        if (review) tr("Find in document", "दस्तावेज़ में खोजें")
                        else tr("Find & replace", "खोजें और बदलें"),
                        tr("Jump to the text you need", "ज़रूरी टेक्स्ट तक पहुँचें"),
                    ) {
                        onAction("find")
                    }
                    HorizontalDivider(color = c.line)
                    MoreRow(
                        Icons.Rounded.Analytics,
                        tr("Word count", "शब्दों की गिनती"),
                        tr("Words, characters and paragraphs", "शब्द, अक्षर और अनुच्छेद"),
                    ) {
                        onAction("stats")
                    }
                }
            }
            if (!review) {
                Text(
                    tr("Editing", "संपादन"),
                    color = c.muted,
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(
                            Triple(
                                "selectAll",
                                tr("Select all", "सभी चुनें"),
                                Icons.Rounded.SelectAll,
                            ),
                            Triple("copy", tr("Copy", "कॉपी"), Icons.Rounded.ContentCopy),
                            Triple("cut", tr("Cut", "काटें"), Icons.Rounded.ContentCut),
                            Triple("paste", tr("Paste", "चिपकाएँ"), Icons.Rounded.ContentPaste),
                        )
                        .forEach { (key, label, icon) ->
                            OutlinedButton(
                                onClick = { onAction(key) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Icon(icon, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                }
                MoreRow(
                    Icons.Rounded.FormatClear,
                    tr("Clear formatting", "फ़ॉर्मैट हटाएँ"),
                    tr("Remove styles from selected text", "चुने हुए टेक्स्ट की सजावट हटाएँ"),
                ) {
                    onAction("clear")
                }
                MoreRow(
                    Icons.Rounded.Save,
                    tr("Save document now", "दस्तावेज़ अभी सहेजें"),
                    tr(
                        "Save your editable copy on this device",
                        "इस फ़ोन में लिखने वाली कॉपी सहेजें",
                    ),
                ) {
                    onAction("save")
                }
            }
            HorizontalDivider(color = c.line)
            TextButton(
                onClick = { onAction("delete") },
                colors = ButtonDefaults.textButtonColors(contentColor = c.danger),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(tr("Delete document", "दस्तावेज़ हटाएँ"))
            }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick,
        color = c.cardFooter,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = c.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
                Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = c.muted)
        }
    }
}
