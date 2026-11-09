package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Immutable
internal data class EditorFormatState(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strike: Boolean,
    val color: String,
    val highlight: String,
    val alignment: String,
    val heading: Int,
    val bullets: Boolean,
    val numbers: Boolean,
    val fontSize: Int,
    val fontFamily: String,
    val lineHeight: Double,
    val paragraphSpacing: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditorToolsSheet(
    state: EditorFormatState,
    initialTab: Int,
    onDismiss: () -> Unit,
    command: (String, String?) -> Unit,
    onPickImage: (Boolean) -> Unit,
    onTable: () -> Unit,
    onSetup: () -> Unit,
    onFind: () -> Unit,
    onReview: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var sizeText by remember(state.fontSize) { mutableStateOf(state.fontSize.toString()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("Document tools", "दस्तावेज़ के उपकरण"),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, tr("Close tools", "उपकरण बंद करें"))
                }
            }
            TabRow(selectedTabIndex = tab) {
                listOf(
                        tr("Text", "टेक्स्ट"),
                        tr("Paragraph", "अनुच्छेद"),
                        tr("Insert", "जोड़ें"),
                        tr("Page", "पेज"),
                    )
                    .forEachIndexed { index, label ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(label) },
                        )
                    }
            }
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tab) {
                    0 -> {
                        ToolHeading(tr("Font", "फ़ॉन्ट"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Noto Sans", "Noto Serif", "Monospace").forEach { font ->
                                FilterChip(
                                    selected = state.fontFamily.equals(font, true),
                                    onClick = { command("fontFamily", font) },
                                    label = { Text(font) },
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            IconButton(
                                onClick = { command("fontSize", (state.fontSize - 1).toString()) },
                                enabled = state.fontSize > 8,
                            ) {
                                Icon(Icons.Rounded.Remove, tr("Smaller font", "छोटा फ़ॉन्ट"))
                            }
                            OutlinedTextField(
                                sizeText,
                                { sizeText = it.filter(Char::isDigit).take(2) },
                                Modifier.weight(1f),
                                label = { Text(tr("Size · pt", "आकार · pt")) },
                                singleLine = true,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = sizeText.toIntOrNull() !in 8..72,
                            )
                            IconButton(
                                onClick = { command("fontSize", (state.fontSize + 1).toString()) },
                                enabled = state.fontSize < 72,
                            ) {
                                Icon(Icons.Rounded.Add, tr("Larger font", "बड़ा फ़ॉन्ट"))
                            }
                            TextButton(
                                onClick = { command("fontSize", sizeText) },
                                enabled = sizeText.toIntOrNull() in 8..72,
                            ) {
                                Text(tr("Apply", "लागू"))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 12, 14, 18, 24, 36, 48, 72).forEach { size ->
                                FilterChip(
                                    state.fontSize == size,
                                    { command("fontSize", "$size") },
                                    label = { Text("$size") },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolToggle(Icons.Rounded.FormatBold, tr("Bold", "बोल्ड"), state.bold) {
                                command("bold", null)
                            }
                            ToolToggle(
                                Icons.Rounded.FormatItalic,
                                tr("Italic", "इटैलिक"),
                                state.italic,
                            ) {
                                command("italic", null)
                            }
                            ToolToggle(
                                Icons.Rounded.FormatUnderlined,
                                tr("Underline", "रेखांकित"),
                                state.underline,
                            ) {
                                command("underline", null)
                            }
                            ToolToggle(
                                Icons.Rounded.FormatStrikethrough,
                                tr("Strikethrough", "काटा हुआ"),
                                state.strike,
                            ) {
                                command("strike", null)
                            }
                        }
                        ToolHeading(tr("Text color", "टेक्स्ट का रंग"))
                        ColorChoices(
                            state.color,
                            listOf(
                                "#111827",
                                "#4b5563",
                                "#2563eb",
                                "#0f766e",
                                "#15803d",
                                "#b91c1c",
                                "#d97706",
                                "#7c3aed",
                            ),
                        ) {
                            command("color", it)
                        }
                        ToolHeading(tr("Highlight", "हाइलाइट"))
                        ColorChoices(
                            state.highlight,
                            listOf("#fff59d", "#a5d6a7", "#90caf9", "#f8bbd0", "#ffcc80"),
                        ) {
                            command("highlight", it)
                        }
                        TextButton(onClick = { command("highlight", "none") }) {
                            Text(tr("Remove highlight", "हाइलाइट हटाएँ"))
                        }
                        OutlinedButton(
                            onClick = { command("clear", null) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tr("Clear formatting", "फ़ॉर्मैटिंग हटाएँ"))
                        }
                    }
                    1 -> {
                        ToolHeading(tr("Paragraph style", "अनुच्छेद शैली"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0..3).forEach { level ->
                                FilterChip(
                                    state.heading == level,
                                    { command("heading", "$level") },
                                    label = {
                                        Text(
                                            if (level == 0) tr("Normal", "सामान्य")
                                            else tr("Heading $level", "शीर्षक $level")
                                        )
                                    },
                                )
                            }
                        }
                        ToolHeading(tr("Alignment", "संरेखण"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                    Triple(
                                        "left",
                                        Icons.AutoMirrored.Rounded.FormatAlignLeft,
                                        tr("Left", "बाएँ"),
                                    ),
                                    Triple(
                                        "center",
                                        Icons.Rounded.FormatAlignCenter,
                                        tr("Center", "बीच"),
                                    ),
                                    Triple(
                                        "right",
                                        Icons.AutoMirrored.Rounded.FormatAlignRight,
                                        tr("Right", "दाएँ"),
                                    ),
                                    Triple(
                                        "justify",
                                        Icons.Rounded.FormatAlignJustify,
                                        tr("Justify", "दोनों ओर"),
                                    ),
                                )
                                .forEach { (value, icon, label) ->
                                    ToolToggle(icon, label, state.alignment == value) {
                                        command("align", value)
                                    }
                                }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                state.bullets,
                                { command("bulletList", null) },
                                label = { Text(tr("Bullets", "बुलेट सूची")) },
                            )
                            FilterChip(
                                state.numbers,
                                { command("orderedList", null) },
                                label = { Text(tr("Numbering", "क्रमांक सूची")) },
                            )
                        }
                        ToolHeading(tr("Line spacing", "पंक्ति अंतर"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1.0, 1.15, 1.5, 2.0, 2.5).forEach { value ->
                                FilterChip(
                                    kotlin.math.abs(state.lineHeight - value) < .01,
                                    { command("lineHeight", "$value") },
                                    label = { Text("$value") },
                                )
                            }
                        }
                        ToolHeading(tr("Space after paragraph", "अनुच्छेद के बाद अंतर"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 4, 8, 12, 18, 24, 30).forEach { value ->
                                FilterChip(
                                    state.paragraphSpacing == value,
                                    { command("paragraphSpacing", "$value") },
                                    label = { Text("$value pt") },
                                )
                            }
                        }
                    }
                    2 -> {
                        ToolAction(
                            Icons.Rounded.GridOn,
                            tr("Table", "टेबल"),
                            tr("Choose rows and columns", "पंक्तियाँ और कॉलम चुनें"),
                            onTable,
                        )
                        ToolAction(
                            Icons.Rounded.Photo,
                            tr("Photo", "फ़ोटो"),
                            tr("Insert and resize on the page", "पेज पर जोड़ें और आकार बदलें"),
                        ) {
                            onPickImage(false)
                        }
                        ToolAction(
                            Icons.Rounded.Draw,
                            tr("Signature image", "हस्ताक्षर का चित्र"),
                            tr("Choose a saved signature", "सेव किया हस्ताक्षर चुनें"),
                        ) {
                            onPickImage(true)
                        }
                        ToolAction(
                            Icons.Rounded.InsertPageBreak,
                            tr("Page break", "पेज ब्रेक"),
                            tr("Start a new printed page", "नया प्रिंट पेज शुरू करें"),
                        ) {
                            command("pageBreak", null)
                            onDismiss()
                        }
                        ToolAction(
                            Icons.Rounded.CalendarToday,
                            tr("Today's date", "आज की तारीख"),
                            "",
                        ) {
                            command("date", null)
                            onDismiss()
                        }
                    }
                    3 -> {
                        ToolAction(
                            Icons.Rounded.Tune,
                            tr("Page setup", "पेज सेटअप"),
                            tr(
                                "A4 margins, orientation and defaults",
                                "A4 मार्जिन, दिशा और डिफ़ॉल्ट",
                            ),
                            onSetup,
                        )
                        ToolAction(
                            Icons.Rounded.FindReplace,
                            tr("Find & replace", "खोजें और बदलें"),
                            tr(
                                "Search words throughout the document",
                                "पूरे दस्तावेज़ में शब्द खोजें",
                            ),
                            onFind,
                        )
                        ToolAction(
                            Icons.Rounded.Visibility,
                            tr("Review & export", "देखें और PDF बनाएँ"),
                            tr(
                                "Check your document before saving PDF",
                                "PDF बनाने से पहले दस्तावेज़ देखें",
                            ),
                            onReview,
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(tr("Back to writing", "लिखना जारी रखें"))
            }
        }
    }
}

@Composable
private fun ToolHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ToolToggle(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color =
            if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(48.dp).semantics { selected = active },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoices(selectedColor: String, colors: List<String>, onSelect: (String) -> Unit) {
    val colorLabel = tr("Color", "रंग")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        colors.forEach { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            val active = selectedColor.equals(hex, true)
            Surface(
                onClick = { onSelect(hex) },
                shape = CircleShape,
                color = color,
                border =
                    BorderStroke(if (active) 3.dp else 1.dp, MaterialTheme.colorScheme.outline),
                modifier =
                    Modifier.size(48.dp).semantics {
                        contentDescription = "$colorLabel $hex"
                        selected = active
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (active)
                        Icon(
                            Icons.Rounded.Check,
                            null,
                            tint = if (color.luminance() > .45f) Color.Black else Color.White,
                        )
                }
            }
        }
    }
}

@Composable
private fun ToolAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle.isNotEmpty())
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}
