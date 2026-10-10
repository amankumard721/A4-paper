package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalDocumentPreview =
    staticCompositionLocalOf<suspend (String) -> List<String>> { { emptyList() } }

@Composable
internal fun PdfBadge() {
    Surface(
        color = Color(0xFFFDE7E9),
        contentColor = Color(0xFFA4262C),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            "PDF",
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

// A decorative content thumbnail, not an exact PDF pagination render.
@Composable
internal fun MiniPaper(lines: List<String>, modifier: Modifier = Modifier) {
    Surface(
        modifier.clearAndSetSemantics {},
        color = Color.White,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, Color(0xFFD1D1D1)),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.take(8).forEachIndexed { index, line ->
                Text(
                    line,
                    color = Color(0xFF242424),
                    fontSize = 4.sp,
                    lineHeight = 5.sp,
                    maxLines = if (index == 0) 2 else 1,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
