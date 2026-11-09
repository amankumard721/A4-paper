package com.a4sizepaper.typing.texttopdf.ui

import android.content.res.Configuration
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorExportSheet(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onExport: (String?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        EditorExportOptions(title, busy, onDismiss, onExport)
    }
}

@Composable
private fun EditorExportOptions(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onExport: (String?) -> Unit,
) {
    val c = AppTheme.colors
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
                    tr("Your PDF, ready to go", "आपका PDF तैयार करें"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = c.ink,
                )
                Text(
                    title.ifBlank { tr("Untitled document", "नया दस्तावेज़") },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = c.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            IconButton(onClick = onDismiss, enabled = !busy) {
                Icon(Icons.Rounded.Close, tr("Close", "बंद करें"), tint = c.muted)
            }
        }
        Text(
            tr(
                "Choose what you want to do with your PDF.",
                "अपने PDF को सहेजें, शेयर करें या पहले देखें।",
            ),
            color = c.muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        ExportOption(
            Icons.Rounded.SaveAlt,
            tr("Save PDF", "PDF सहेजें"),
            tr("Choose a folder on your phone", "फ़ोन में फ़ोल्डर चुनें"),
            true,
            !busy,
        ) {
            onExport("save")
        }
        ExportOption(
            Icons.Rounded.Share,
            tr("Share PDF", "PDF शेयर करें"),
            tr("Send to WhatsApp, email or another app", "WhatsApp, ईमेल या दूसरे ऐप पर भेजें"),
            false,
            !busy,
        ) {
            onExport("share")
        }
        ExportOption(
            Icons.Rounded.Visibility,
            tr("Preview PDF", "PDF देखें"),
            tr("Check the finished pages before saving", "सहेजने से पहले तैयार पेज देखें"),
            false,
            !busy,
        ) {
            onExport(null)
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accentText)
            Text(
                tr("Preparing your document…", "दस्तावेज़ तैयार हो रहा है…"),
                color = c.muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            tr(
                "Your editable document stays in Documents.",
                "लिखने वाला दस्तावेज़ दस्तावेज़ सेक्शन में रहेगा।",
            ),
            color = c.muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ExportOption(
    icon: ImageVector,
    title: String,
    detail: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (primary) c.accent else c.cardFooter,
        contentColor = if (primary) c.onAccent else c.ink,
        border = if (primary) null else BorderStroke(1.dp, c.line),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (primary) c.onAccent else c.accentText)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (primary) c.onAccent else c.muted,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
        }
    }
}

@Preview(name = "PDF actions · Light", widthDp = 390)
@Composable
private fun ExportLightPreview() {
    A4Theme(false) { Surface { EditorExportOptions("Meeting notes", false, {}, {}) } }
}

@Preview(name = "PDF actions · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 390)
@Composable
private fun ExportDarkPreview() {
    A4Theme(true) { Surface { EditorExportOptions("Meeting notes", false, {}, {}) } }
}
