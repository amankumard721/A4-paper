package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FontColorSheet(
    selectedColor: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors =
        listOf(
            "#111827" to tr("Black", "काला"),
            "#6b7280" to tr("Gray", "स्लेटी"),
            "#ffffff" to tr("White", "सफ़ेद"),
            "#b91c1c" to tr("Dark red", "गहरा लाल"),
            "#ff0000" to tr("Red", "लाल"),
            "#ea580c" to tr("Orange", "नारंगी"),
            "#f59e0b" to tr("Amber", "अंबर"),
            "#facc15" to tr("Yellow", "पीला"),
            "#15803d" to tr("Green", "हरा"),
            "#0f766e" to tr("Teal", "नीलहरित"),
            "#0284c7" to tr("Sky blue", "आसमानी"),
            "#2563eb" to tr("Blue", "नीला"),
            "#1e3a8a" to tr("Navy", "गहरा नीला"),
            "#7c3aed" to tr("Purple", "बैंगनी"),
            "#db2777" to tr("Pink", "गुलाबी"),
        )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("Font color", "टेक्स्ट का रंग"), style = MaterialTheme.typography.titleLarge)
            Text(
                tr(
                    "Choose a color for selected text or your next words.",
                    "चुने हुए टेक्स्ट या आगे लिखने के लिए रंग चुनें।",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 5,
            ) {
                colors.forEach { (hex, name) ->
                    val active = hex.equals(selectedColor, ignoreCase = true)
                    val swatch = Color(android.graphics.Color.parseColor(hex))
                    Surface(
                        onClick = { onSelect(hex) },
                        modifier =
                            Modifier.size(48.dp).semantics {
                                contentDescription = name
                                selected = active
                                role = Role.Button
                            },
                        shape = MaterialTheme.shapes.small,
                        color = swatch,
                        border =
                            BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                            ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (active)
                                Icon(
                                    Icons.Rounded.Check,
                                    null,
                                    tint =
                                        if (swatch.luminance() > 0.45f) Color.Black else Color.White,
                                )
                        }
                    }
                }
            }
            TextButton(onClick = { onSelect("#111827") }) {
                Text(tr("Default black", "डिफ़ॉल्ट काला"))
            }
        }
    }
}
