package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.a4sizepaper.typing.texttopdf.domain.PageSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PageSetupDialog(initial: PageSettings, onDismiss: () -> Unit, onSave: (PageSettings) -> Unit) {
    val c = AppTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var landscape by rememberSaveable { mutableStateOf(initial.landscape) }
    var top by rememberSaveable { mutableStateOf(initial.marginTop.toString()) }
    var right by rememberSaveable { mutableStateOf(initial.marginRight.toString()) }
    var bottom by rememberSaveable { mutableStateOf(initial.marginBottom.toString()) }
    var left by rememberSaveable { mutableStateOf(initial.marginLeft.toString()) }
    var font by rememberSaveable { mutableStateOf(initial.fontFamily) }
    var fontSize by rememberSaveable { mutableStateOf(initial.fontSize.toString()) }
    var line by rememberSaveable { mutableStateOf(initial.lineHeight.toString()) }
    var spacing by rememberSaveable { mutableStateOf(initial.paragraphSpacing.toString()) }
    val value =
        runCatching {
                PageSettings(
                        landscape,
                        top.toDouble(),
                        right.toDouble(),
                        bottom.toDouble(),
                        left.toDouble(),
                        font,
                        fontSize.toInt(),
                        line.toDouble(),
                        spacing.toInt(),
                    )
                    .validate()
            }
            .getOrNull()
    val layoutValid = listOf(top, right, bottom, left).all { validNumber(it, 5.0, 60.0) }
    val textValid =
        validNumber(fontSize, 8.0, 36.0, true) &&
            validNumber(line, 1.0, 2.5) &&
            validNumber(spacing, 0.0, 30.0, true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Page setup", "पेज सेटअप"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr("Make the page yours", "पेज को अपनी ज़रूरत के अनुसार बनाएँ"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, tr("Close", "बंद करें"))
                }
            }
            TabRow(selectedTabIndex = tab, containerColor = c.surface) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(tr("Layout", "लेआउट")) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(tr("Text", "टेक्स्ट")) },
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (tab == 0) {
                    PaperPreview(
                        landscape,
                        top.toDoubleOrNull(),
                        right.toDoubleOrNull(),
                        bottom.toDoubleOrNull(),
                        left.toDoubleOrNull(),
                    )
                    SetupHeading(tr("Orientation", "पेज की दिशा"))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            !landscape,
                            { landscape = false },
                            label = { Text(tr("Portrait", "पोर्ट्रेट")) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                        FilterChip(
                            landscape,
                            { landscape = true },
                            label = { Text(tr("Landscape", "लैंडस्केप")) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                    SetupHeading(tr("Margins", "मार्जिन"))
                    Text(
                        tr(
                            "Space between your content and the paper edge. All values in mm.",
                            "टेक्स्ट और पेज के किनारे के बीच की जगह। सभी मान मिमी में।",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                                10 to tr("Narrow · 10", "कम · 10"),
                                15 to tr("Normal · 15", "सामान्य · 15"),
                                25 to tr("Wide · 25", "चौड़ा · 25"),
                            )
                            .forEach { (amount, label) ->
                                FilterChip(
                                    selected =
                                        listOf(top, right, bottom, left).all {
                                            it.toDoubleOrNull() == amount.toDouble()
                                        },
                                    onClick = {
                                        top = "$amount"
                                        right = "$amount"
                                        bottom = "$amount"
                                        left = "$amount"
                                    },
                                    label = { Text(label) },
                                )
                            }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetupNumber(
                            top,
                            { top = it },
                            tr("Top", "ऊपर"),
                            "5–60 mm",
                            5.0,
                            60.0,
                            modifier = Modifier.weight(1f),
                        )
                        SetupNumber(
                            bottom,
                            { bottom = it },
                            tr("Bottom", "नीचे"),
                            "5–60 mm",
                            5.0,
                            60.0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetupNumber(
                            left,
                            { left = it },
                            tr("Left", "बाएँ"),
                            "5–60 mm",
                            5.0,
                            60.0,
                            modifier = Modifier.weight(1f),
                        )
                        SetupNumber(
                            right,
                            { right = it },
                            tr("Right", "दाएँ"),
                            "5–60 mm",
                            5.0,
                            60.0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    SetupHeading(tr("Default font", "डिफ़ॉल्ट फ़ॉन्ट"))
                    Text(
                        tr(
                            "Base text style for the document. Individually formatted text keeps its own style.",
                            "दस्तावेज़ की मूल टेक्स्ट शैली। अलग से फ़ॉर्मैट किए टेक्स्ट की शैली वही रहेगी।",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PageSettings.FONTS.forEach { family ->
                            FilterChip(font == family, { font = family }, label = { Text(family) })
                        }
                    }
                    SetupNumber(
                        fontSize,
                        { fontSize = it },
                        tr("Font size", "फ़ॉन्ट आकार"),
                        "8–36 pt",
                        8.0,
                        36.0,
                        true,
                    )
                    SetupHeading(tr("Spacing", "अंतर"))
                    SetupNumber(
                        line,
                        { line = it },
                        tr("Line spacing", "पंक्तियों का अंतर"),
                        "1–2.5",
                        1.0,
                        2.5,
                    )
                    SetupNumber(
                        spacing,
                        { spacing = it },
                        tr("After each paragraph", "हर अनुच्छेद के बाद"),
                        "0–30 pt",
                        0.0,
                        30.0,
                        true,
                    )
                }
            }
            HorizontalDivider(color = c.line)
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (value == null)
                    Text(
                        if (!layoutValid)
                            tr(
                                "Check the margin values in Layout.",
                                "लेआउट में मार्जिन के मान सही करें।",
                            )
                        else if (!textValid)
                            tr(
                                "Check the font and spacing values in Text.",
                                "टेक्स्ट में फ़ॉन्ट और अंतर के मान सही करें।",
                            )
                        else
                            tr(
                                "These settings leave too little writing space.",
                                "इन सेटिंग्स से लिखने की जगह कम हो रही है।",
                            ),
                        color = c.danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(tr("Cancel", "रद्द करें"))
                    }
                    Button(
                        onClick = { value?.let(onSave) },
                        enabled = value != null,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = c.accent,
                                contentColor = c.onAccent,
                            ),
                    ) {
                        Text(tr("Apply changes", "लागू करें"))
                    }
                }
            }
        }
    }
}

private fun validNumber(raw: String, min: Double, max: Double, integer: Boolean = false): Boolean {
    val number = raw.toDoubleOrNull() ?: return false
    return number.isFinite() && number in min..max && (!integer || raw.toIntOrNull() != null)
}

@Composable
private fun SetupHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = AppTheme.colors.ink,
    )
}

@Composable
private fun SetupNumber(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    range: String,
    min: Double,
    max: Double,
    integer: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val valid = validNumber(value, min, max, integer)
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.replace(',', '.').take(8)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        supportingText = { Text(if (valid) range else tr("Enter", "भरें") + " " + range) },
        isError = !valid,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal
            ),
    )
}

@Composable
private fun PaperPreview(
    landscape: Boolean,
    top: Double?,
    right: Double?,
    bottom: Double?,
    left: Double?,
) {
    val c = AppTheme.colors
    val widthMm = if (landscape) 297f else 210f
    val heightMm = if (landscape) 210f else 297f
    Surface(
        color = c.cardFooter,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, c.line),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.height(100.dp).aspectRatio(widthMm / heightMm)) {
                drawRect(c.paper)
                drawRect(c.line, style = Stroke(1.dp.toPx()))
                fun margin(value: Double?) =
                    value?.takeIf { it.isFinite() && it in 5.0..60.0 }?.toFloat() ?: 15f
                val x = size.width * margin(left) / widthMm
                val y = size.height * margin(top) / heightMm
                val w = size.width - x - size.width * margin(right) / widthMm
                val h = size.height - y - size.height * margin(bottom) / heightMm
                drawRect(c.accentSoft, Offset(x, y), Size(w, h))
                drawRect(c.accentText, Offset(x, y), Size(w, h), style = Stroke(1.dp.toPx()))
                repeat(5) { index ->
                    val yy = y + h * (index + 1) / 7
                    drawLine(
                        c.paperTitle,
                        Offset(x + w * .12f, yy),
                        Offset(x + w * .85f, yy),
                        1.dp.toPx(),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "A4",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (landscape) "297 × 210 mm" else "210 × 297 mm",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
                Text(
                    tr("Blue area = writing space", "नीला भाग = लिखने की जगह"),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                )
            }
        }
    }
}
