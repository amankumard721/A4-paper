package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal data class MediaSelection(
    val table: Boolean = false,
    val canMerge: Boolean = false,
    val canSplit: Boolean = false,
    val image: Boolean = false,
    val imageWidth: Int = 50,
    val imageRotation: Float = 0f,
    val imageAspectLocked: Boolean = true,
    val imageAlign: String = "left",
    val border: Int = 1,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MediaToolsSheet(
    selection: MediaSelection,
    onDismiss: () -> Unit,
    command: (String, String?) -> Unit,
    pick: (Boolean) -> Unit,
) {
    var tab by remember { mutableIntStateOf(if (selection.image) 1 else 0) }
    var rows by remember { mutableIntStateOf(3) }
    var columns by remember { mutableIntStateOf(3) }
    var imageWidth by
        remember(selection.imageWidth) { mutableFloatStateOf(selection.imageWidth.toFloat()) }
    var imageRotation by
        remember(selection.imageRotation) { mutableFloatStateOf(selection.imageRotation) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("Insert & adjust", "जोड़ें और आकार बदलें"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, tr("Close", "बंद करें"))
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(tr("Table", "टेबल")) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(tr("Image", "चित्र")) },
                )
            }
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tab == 0) {
                    if (!selection.table) {
                        Text(
                            tr("New table", "नई टेबल"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        CountPicker(tr("Rows", "पंक्तियाँ"), rows, 20) { rows = it }
                        CountPicker(tr("Columns", "कॉलम"), columns, 8) { columns = it }
                        FluentButton(
                            onClick = {
                                command(
                                    "table",
                                    JSONObject().put("rows", rows).put("cols", columns).toString(),
                                )
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.GridOn, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr("Insert $rows × $columns table", "$rows × $columns टेबल जोड़ें")
                            )
                        }
                        Text(
                            tr(
                                "Tap inside an existing table to see its editing tools. Drag ↔ for columns, ↕ for row height, or the corner handle to resize the table.",
                                "मौजूदा टेबल के सेल पर टैप करें। कॉलम के लिए ↔, पंक्ति की ऊँचाई के लिए ↕, या कोने का हैंडल खींचकर टेबल का आकार बदलें।",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            tr("Selected table", "चुनी हुई टेबल"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            tr(
                                "Close this panel and drag ↔ on column borders, ↕ on row borders, or the corner handle to resize.",
                                "यह पैनल बंद करके कॉलम सीमा पर ↔, पंक्ति सीमा पर ↕, या कोने का हैंडल खींचकर आकार बदलें।",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        ToolPair(
                            tr("Add row", "पंक्ति जोड़ें"),
                            { command("addRowAfter", null) },
                            tr("Remove row", "पंक्ति हटाएँ"),
                            { command("deleteRow", null) },
                        )
                        ToolPair(
                            tr("Add column", "कॉलम जोड़ें"),
                            { command("addColumnAfter", null) },
                            tr("Remove column", "कॉलम हटाएँ"),
                            { command("deleteColumn", null) },
                        )
                        HorizontalDivider()
                        Text(tr("Cells", "सेल"), style = MaterialTheme.typography.titleSmall)
                        ToolPair(
                            tr("Merge", "मिलाएँ"),
                            { command("mergeCells", null) },
                            tr("Split", "अलग करें"),
                            { command("splitCell", null) },
                            firstEnabled = selection.canMerge,
                            secondEnabled = selection.canSplit,
                        )
                        TextButton(onClick = { command("toggleHeaderRow", null) }) {
                            Text(tr("Toggle heading row", "शीर्षक पंक्ति बदलें"))
                        }
                        Text(
                            tr("Border thickness", "सीमा की मोटाई"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 1, 2).forEach { n ->
                                FilterChip(
                                    selection.border == n,
                                    { command("cellBorder", "$n") },
                                    label = { Text("$n px") },
                                )
                            }
                        }
                        Text(
                            tr("Text alignment", "टेक्स्ट संरेखण"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                    "left" to tr("Left", "बाएँ"),
                                    "center" to tr("Center", "बीच"),
                                    "right" to tr("Right", "दाएँ"),
                                )
                                .forEach { (v, label) ->
                                    TextButton(onClick = { command("align", v) }) { Text(label) }
                                }
                        }
                        TextButton(
                            onClick = {
                                command("deleteTable", null)
                                onDismiss()
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Icon(Icons.Rounded.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Delete table", "टेबल हटाएँ"))
                        }
                    }
                } else {
                    if (!selection.image) {
                        Text(
                            tr("Add an image", "चित्र जोड़ें"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        FluentButton(
                            onClick = { pick(false) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Choose photo", "फ़ोटो चुनें"))
                        }
                        FluentOutlinedButton(
                            onClick = { pick(true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tr("Add signature image", "हस्ताक्षर का चित्र जोड़ें"))
                        }
                        Text(
                            tr(
                                "Tap an image: corners resize, sides stretch, and the round handle rotates.",
                                "चित्र टैप करें: कोनों से आकार, किनारों से खिंचाव और गोल हैंडल से घुमाएँ।",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            tr("Selected image", "चुना हुआ चित्र"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            tr(
                                "Use corner and side handles on the page. Set a precise angle below.",
                                "पेज के कोनों और किनारों से आकार बदलें। नीचे सही कोण चुनें।",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            tr("Width: ${imageWidth.toInt()}%", "चौड़ाई: ${imageWidth.toInt()}%"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Slider(
                            value = imageWidth,
                            onValueChange = { imageWidth = it },
                            valueRange = 10f..100f,
                            onValueChangeFinished = {
                                command("imageWidth", imageWidth.toInt().toString())
                            },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 50, 75, 100).forEach { v ->
                                FilterChip(
                                    selection.imageWidth == v,
                                    { command("imageWidth", "$v") },
                                    label = { Text("$v%") },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr("Keep corner proportions", "कोनों से अनुपात बनाए रखें"),
                                Modifier.weight(1f),
                            )
                            Switch(
                                selection.imageAspectLocked,
                                { command("imageAspectLocked", it.toString()) },
                            )
                        }
                        Text(
                            tr(
                                "Rotation: ${"%.1f".format(imageRotation)}°",
                                "घुमाव: ${"%.1f".format(imageRotation)}°",
                            )
                        )
                        Slider(
                            value = imageRotation,
                            onValueChange = { imageRotation = it },
                            valueRange = 0f..360f,
                            onValueChangeFinished = {
                                command("imageRotation", imageRotation.toString())
                            },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 45, 90, 180, 270).forEach { angle ->
                                FilterChip(
                                    selection.imageRotation.toInt() == angle,
                                    { command("imageRotation", angle.toString()) },
                                    label = { Text("$angle°") },
                                )
                            }
                        }
                        TextButton(onClick = { command("imageReset", null) }) {
                            Text(tr("Reset shape & rotation", "आकार और घुमाव रीसेट करें"))
                        }
                        Text(tr("Position", "स्थिति"), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                    "left" to tr("Left", "बाएँ"),
                                    "center" to tr("Center", "बीच"),
                                    "right" to tr("Right", "दाएँ"),
                                )
                                .forEach { (v, label) ->
                                    FilterChip(
                                        selection.imageAlign == v,
                                        { command("imageAlign", v) },
                                        label = { Text(label) },
                                    )
                                }
                        }
                        TextButton(
                            onClick = {
                                command("deleteImage", null)
                                onDismiss()
                            },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                        ) {
                            Icon(Icons.Rounded.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("Delete image", "चित्र हटाएँ"))
                        }
                    }
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 48.dp),
            ) {
                Text(tr("Done", "पूर्ण"))
            }
        }
    }
}

@Composable
private fun CountPicker(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > 1) {
            Icon(Icons.Rounded.Remove, tr("Decrease", "घटाएँ") + " " + label)
        }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(value + 1) }, enabled = value < maximum) {
            Icon(Icons.Rounded.Add, tr("Increase", "बढ़ाएँ") + " " + label)
        }
    }
}

@Composable
private fun ToolPair(
    first: String,
    onFirst: () -> Unit,
    second: String,
    onSecond: () -> Unit,
    firstEnabled: Boolean = true,
    secondEnabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluentOutlinedButton(
            onClick = onFirst,
            modifier = Modifier.weight(1f),
            enabled = firstEnabled,
        ) {
            Text(first)
        }
        FluentOutlinedButton(
            onClick = onSecond,
            modifier = Modifier.weight(1f),
            enabled = secondEnabled,
        ) {
            Text(second)
        }
    }
}
