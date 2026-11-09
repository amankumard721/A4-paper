package com.a4sizepaper.typing.texttopdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    preparing: Boolean,
    pageLimit: Int,
    onPageLimit: (Int) -> Unit,
    enhancedCleanup: Boolean,
    onCleanup: (Boolean) -> Unit,
    allowGallery: Boolean,
    onGallery: (Boolean) -> Unit,
    available: Boolean = true,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onImportPdf: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = Modifier.background(homeBackdrop()),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(tr("Scan studio", "स्कैन स्टूडियो"), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back", "वापस जाएँ"))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr("From paper to a clear PDF.", "कागज़ से साफ़ PDF बनाएँ।"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr(
                            "Scan notes, forms and receipts. Keep several pages together in one document.",
                            "नोट्स, फ़ॉर्म और रसीदें स्कैन करें। कई पेज एक ही दस्तावेज़ में रखें।",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("Capture settings", "स्कैन सेटिंग्स"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to tr("Single", "एक पेज"), 2 to tr("Two sides", "दो तरफ़"), 50 to tr("Batch", "कई पेज")).forEach { (limit, label) ->
                                FilterChip(selected = pageLimit == limit, onClick = { onPageLimit(limit) }, enabled = !preparing,
                                    label = { Text(label) }, shape = RoundedCornerShape(16.dp))
                            }
                        }
                        Text(tr("Maximum $pageLimit pages per scan", "हर स्कैन में अधिकतम $pageLimit पेज"), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Enhanced cleanup", "बेहतर सफ़ाई"), fontWeight = FontWeight.SemiBold)
                                Text(tr("ML-powered stain and finger cleanup tools", "दाग और उँगली हटाने के ML टूल"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(enhancedCleanup, onCheckedChange = onCleanup, enabled = !preparing)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Gallery import", "गैलरी से जोड़ें"), fontWeight = FontWeight.SemiBold)
                                Text(tr("Turn off for camera-only capture", "केवल कैमरा के लिए बंद करें"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(allowGallery, onCheckedChange = onGallery, enabled = !preparing)
                        }
                    }
                }
            }
            item {
                Surface(
                    color = HomeBlue.copy(alpha = .10f),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ScanPaperIllustration(Modifier.fillMaxWidth().height(190.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                null,
                                Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tr(
                                    "Auto crop · Filters · Up to $pageLimit pages",
                                    "ऑटो क्रॉप · फ़िल्टर · $pageLimit पेज तक",
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        FluentButton(
                            onClick = onScan,
                            enabled = !preparing && available,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        ) {
                            if (preparing)
                                CircularProgressIndicator(
                                    Modifier.size(21.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            else Icon(Icons.Rounded.DocumentScanner, null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (preparing) tr("Preparing scanner…", "स्कैनर तैयार हो रहा है…")
                                else tr("Scan a document", "दस्तावेज़ स्कैन करें")
                            )
                        }
                        Text(
                            tr(
                                "Use the camera, or choose photos from the gallery inside the scanner.",
                                "स्कैनर में कैमरा इस्तेमाल करें या गैलरी से तस्वीरें चुनें।",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (!available)
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                tr(
                                    "The scanner is unavailable on this device. You can still open an existing PDF below.",
                                    "इस डिवाइस पर स्कैनर उपलब्ध नहीं है। नीचे से मौजूदा PDF खोल सकते हैं।",
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        tr("A simple scan, start to finish", "स्कैन से PDF तक आसान कदम"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    listOf(
                            listOf(
                                Triple(
                                    Icons.Rounded.CameraAlt,
                                    tr("Capture", "तस्वीर लें"),
                                    tr("Camera or gallery", "कैमरा या गैलरी"),
                                ),
                                Triple(
                                    Icons.Rounded.Crop,
                                    tr("Crop", "क्रॉप करें"),
                                    tr("Adjust the page edges", "पेज के किनारे चुनें"),
                                ),
                            ),
                            listOf(
                                Triple(
                                    Icons.Rounded.Tune,
                                    tr("Clean up", "साफ़ करें"),
                                    tr("Rotate and apply filters", "घुमाएँ और फ़िल्टर लगाएँ"),
                                ),
                                Triple(
                                    Icons.Rounded.PictureAsPdf,
                                    tr("Save PDF", "PDF सेव करें"),
                                    tr("Review, save or share", "देखें, सेव या शेयर करें"),
                                ),
                            ),
                        )
                        .forEach { steps ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                steps.forEach { (icon, title, detail) ->
                                    Surface(
                                        Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(18.dp),
                                    ) {
                                        Column(
                                            Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                icon,
                                                null,
                                                Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }
            item {
                FluentOutlinedButton(
                    onClick = onImportPdf,
                    enabled = !preparing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.FileOpen, null)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Open an existing PDF", "मौजूदा PDF खोलें"))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Rounded.PhoneAndroid,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            tr(
                                "Google Play services is required. First-time scanner setup may need an internet connection.",
                                "Google Play services ज़रूरी है। पहली बार स्कैनर तैयार करने के लिए इंटरनेट लग सकता है।",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        tr(
                            "Scans are saved as image-based PDFs. Text recognition (OCR) is not included.",
                            "स्कैन तस्वीरों वाली PDF के रूप में सेव होता है। टेक्स्ट पहचान (OCR) शामिल नहीं है।",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeScanCard(busy: Boolean, onScan: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(18.dp)) {
            val showPreview = maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.3f
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (showPreview) ScanPaperIllustration(Modifier.width(74.dp).height(106.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        tr("Scan to PDF", "स्कैन से PDF"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        tr(
                            "Camera or gallery · Crop & clean",
                            "कैमरा या गैलरी · क्रॉप और साफ़ करें",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    FluentButton(onClick = onScan, enabled = !busy) {
                        Icon(Icons.Rounded.DocumentScanner, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Scan pages", "पेज स्कैन करें"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanPaperIllustration(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.clearAndSetSemantics {}) {
        val paperWidth = minOf(size.width * .65f, size.height * .64f)
        val paperHeight = paperWidth * 1.38f
        val left = (size.width - paperWidth) / 2
        val top = (size.height - paperHeight) / 2
        drawRoundRect(
            Color(0x1A00205B),
            Offset(left + 5.dp.toPx(), top + 6.dp.toPx()),
            Size(paperWidth, paperHeight),
            CornerRadius(9.dp.toPx()),
        )
        drawRoundRect(
            Color.White,
            Offset(left, top),
            Size(paperWidth, paperHeight),
            CornerRadius(9.dp.toPx()),
        )
        val inset = paperWidth * .17f
        drawRoundRect(
            accent.copy(alpha = .16f),
            Offset(left + inset, top + paperHeight * .17f),
            Size(paperWidth * .27f, paperHeight * .13f),
            CornerRadius(3.dp.toPx()),
        )
        repeat(5) { index ->
            val lineTop = top + paperHeight * (.40f + index * .09f)
            val width = paperWidth * if (index == 4) .40f else .66f
            drawLine(
                Color(0xFFCAD5E6),
                Offset(left + inset, lineTop),
                Offset(left + inset + width, lineTop),
                paperWidth * .035f,
                StrokeCap.Round,
            )
        }
        val padding = 9.dp.toPx()
        val l = left - padding
        val t = top - padding
        val r = left + paperWidth + padding
        val b = top + paperHeight + padding
        val corner = paperWidth * .16f
        val path =
            Path().apply {
                moveTo(l, t + corner)
                lineTo(l, t)
                lineTo(l + corner, t)
                moveTo(r - corner, t)
                lineTo(r, t)
                lineTo(r, t + corner)
                moveTo(r, b - corner)
                lineTo(r, b)
                lineTo(r - corner, b)
                moveTo(l + corner, b)
                lineTo(l, b)
                lineTo(l, b - corner)
            }
        drawPath(path, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        val scanY = top + paperHeight * .57f
        drawLine(
            accent.copy(alpha = .7f),
            Offset(l - 4.dp.toPx(), scanY),
            Offset(r + 4.dp.toPx(), scanY),
            1.5.dp.toPx(),
            StrokeCap.Round,
        )
    }
}
