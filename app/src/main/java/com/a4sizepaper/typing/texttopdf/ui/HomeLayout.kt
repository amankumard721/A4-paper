package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.a4sizepaper.typing.texttopdf.data.DocumentEntry
import com.a4sizepaper.typing.texttopdf.domain.Template
import com.a4sizepaper.typing.texttopdf.domain.Templates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

internal val HomeRed: Color @Composable get() = MaterialTheme.colorScheme.error
internal val HomeBlue: Color @Composable get() = AppTheme.colors.accentText
private val HomePurple: Color @Composable get() = AppTheme.colors.accentText

@Composable
internal fun homeBackdrop(): Brush {
    val bg = AppTheme.colors.bg
    return remember(bg) { Brush.linearGradient(listOf(bg, bg)) }
}
@Composable private fun homePanel() = AppTheme.colors.surface

@Composable
internal fun HomeHeader(
    query: String, onQuery: (String) -> Unit, unread: Boolean,
    onUpdates: () -> Unit, onSettings: () -> Unit, onSearch: () -> Unit,
) {
    Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("A4 Paper Typing", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("Write, scan, make PDF", "लिखें, स्कैन करें, PDF बनाएँ"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, tr("Search", "खोजें"), Modifier.size(20.dp)) }
        IconButton(onClick = onSettings, modifier = Modifier.background(AppTheme.colors.iconSoft, CircleShape)) {
            Icon(Icons.Outlined.Person, tr("Settings", "सेटिंग्स"), Modifier.size(20.dp), tint = HomeBlue)
        }
    }
}

// The search field scrolls with the list. The fixed header never resizes during a fling.
@Composable
internal fun HomeSearchRow(query: String, onQuery: (String) -> Unit) {
    BouncySearchBar(
        query = query,
        onQuery = onQuery,
        placeholder = tr("Search your documents…", "दस्तावेज़ खोजें…"),
        accentColor = HomeBlue,
        containerColor = homePanel(),
        shape = CircleShape,
        motionEnabled = true,
    )
}

@Composable
internal fun BouncySearchBar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accentColor: Color = HomeRed,
    containerColor: Color = homePanel(),
    shape: Shape = CircleShape,
    motionEnabled: Boolean = true,
) {
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isFocused by remember { mutableStateOf(false) }

    var bounceTrigger by remember { mutableIntStateOf(0) }
    var lastBounceTime by remember { mutableLongStateOf(0L) }
    val triggerBounce = {
        val now = System.currentTimeMillis()
        if (motionEnabled && now - lastBounceTime > 160L) {
            lastBounceTime = now
            bounceTrigger++
        }
    }

    // Bouncy jump translation (upward spring jump & elastic landing)
    val jumpAnim = remember { Animatable(0f) }
    // Scale bounce (subtle expansion & spring settle)
    val scaleAnim = remember { Animatable(1f) }
    // Magnifying glass micro-motion
    val iconBounce = remember { Animatable(1f) }
    val iconRotation = remember { Animatable(0f) }

    // Tap release detector for instant tactile spring
    var wasPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed) {
        if (wasPressed && !isPressed) {
            triggerBounce()
        }
        wasPressed = isPressed
    }

    // Spring animation launcher
    LaunchedEffect(bounceTrigger) {
        if (bounceTrigger > 0) {
            launch {
                // Playful jump up and soft bouncy rebound
                jumpAnim.animateTo(
                    targetValue = -7f,
                    animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
                )
                jumpAnim.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                )
            }
            launch {
                // Spring scale overshoot & settle
                scaleAnim.animateTo(
                    targetValue = 1.028f,
                    animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
                )
                scaleAnim.animateTo(
                    targetValue = if (isFocused) 1.012f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                )
            }
            launch {
                // Search icon tilt & spring pop
                iconRotation.animateTo(-16f, animationSpec = tween(90))
                iconRotation.animateTo(
                    0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )
            }
            launch {
                // Search icon scale pop
                iconBounce.animateTo(1.25f, animationSpec = tween(90))
                iconBounce.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )
            }
        }
    }

    LaunchedEffect(isFocused) {
        if (!isFocused && bounceTrigger > 0) {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
            jumpAnim.animateTo(0f)
        }
    }

    // When physically pressed, apply tactile squash
    val currentScale = if (isPressed) scaleAnim.value * 0.972f else scaleAnim.value

    val shadowElevation by
        animateDpAsState(
            targetValue = if (isFocused) 8.dp else 2.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "searchElevation",
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = with(density) { jumpAnim.value.dp.toPx() }
                    scaleX = currentScale
                    scaleY = currentScale
                }
                .shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    spotColor =
                        if (isFocused) accentColor.copy(alpha = 0.32f)
                        else Color.Black.copy(alpha = 0.08f),
                    ambientColor =
                        if (isFocused) accentColor.copy(alpha = 0.16f)
                        else Color.Black.copy(alpha = 0.04f),
                )
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQuery(it)
                if (it.length == 1 && query.isEmpty()) {
                    triggerBounce()
                }
            },
            interactionSource = interactionSource,
            modifier =
                Modifier.fillMaxWidth().onFocusChanged { focusState ->
                    if (focusState.isFocused != isFocused) {
                        isFocused = focusState.isFocused
                        if (focusState.isFocused) {
                            triggerBounce()
                        }
                    }
                },
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint =
                        if (isFocused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = iconBounce.value
                            scaleY = iconBounce.value
                            rotationZ = iconRotation.value
                        },
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter =
                        fadeIn() +
                            scaleIn(
                                initialScale = 0.4f,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                            ),
                    exit = fadeOut() + scaleOut(targetScale = 0.4f),
                ) {
                    IconButton(
                        onClick = {
                            onQuery("")
                            triggerBounce()
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            tr("Clear search", "खोज हटाएँ"),
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            shape = shape,
            singleLine = true,
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = containerColor,
                    focusedContainerColor = containerColor,
                    unfocusedBorderColor =
                        if (query.isNotEmpty()) accentColor.copy(alpha = 0.35f)
                        else Color.Transparent,
                    focusedBorderColor = accentColor,
                    cursorColor = accentColor,
                ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
    }
}

@Composable
internal fun HomeCreateActions(
    importing: Boolean,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onGallery: () -> Unit,
    onMerge: () -> Unit,
    onRemove: () -> Unit,
    onCompress: () -> Unit,
    onSplit: () -> Unit,
) {
    val colors = AppTheme.colors
    val dark = colors == DarkColors
    val blue = if (dark) colors.iconSoft else Color(0xFFCCE2F8)
    val yellow = if (dark) Color(0xFF44361F) else Color(0xFFF9DDA6)
    val goldInk = if (dark) Color(0xFFFFD58C) else Color(0xFF714600)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NewDocumentHero(onCreate, null, {})
        Text(tr("Import", "इम्पोर्ट"), style = MaterialTheme.typography.titleMedium, color = colors.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            HomeImportCard(tr("Import file", "फ़ाइल खोलें"), "Word, Excel, PDF", Icons.Outlined.NoteAdd, blue, HomeBlue, Modifier.weight(1f), !importing, onImport)
            HomeImportCard(tr("From gallery", "गैलरी से"), tr("Pick images", "तस्वीरें चुनें"), Icons.Outlined.Image, yellow, goldInk, Modifier.weight(1f), !importing, onGallery)
        }
        Text(tr("Quick actions", "त्वरित टूल्स"), style = MaterialTheme.typography.titleMedium, color = colors.muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeQuickAction(tr("Merge PDF", "PDF जोड़ें"), Icons.Outlined.ContentCopy, if (dark) Color(0xFF482A30) else Color(0xFFF8D5D7), colors.danger, Modifier.weight(1f), !importing, onMerge)
            HomeQuickAction(tr("Remove pages", "पेज हटाएँ"), Icons.Outlined.DeleteOutline, if (dark) Color(0xFF253E2C) else Color(0xFFC8E8CA), if (dark) Color(0xFFA8E2AE) else Color(0xFF205829), Modifier.weight(1f), !importing, onRemove)
            HomeQuickAction(tr("Compress", "साइज़ घटाएँ"), Icons.Outlined.Archive, yellow, goldInk, Modifier.weight(1f), !importing, onCompress)
            HomeQuickAction(tr("Split", "अलग करें"), Icons.Outlined.ContentCut, blue, HomeBlue, Modifier.weight(1f), !importing, onSplit)
        }
    }
}

@Composable
private fun HomeImportCard(title: String, subtitle: String, icon: ImageVector, tile: Color, tint: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxHeight(), shape = RoundedCornerShape(18.dp),
        color = AppTheme.colors.surface, border = BorderStroke(1.dp, AppTheme.colors.line)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(tile, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun HomeQuickAction(label: String, icon: ImageVector, tile: Color, tint: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(tile, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(25.dp), tint = tint)
        }
        Text(label, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NewDocumentHero(onCreate: () -> Unit, latestDocument: DocumentEntry?, onResumeDocument: (DocumentEntry) -> Unit) {
    var cursorVisible by remember { mutableStateOf(true) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val state by lifecycle.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(state) {
        cursorVisible = true
        if (state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) while (true) {
            kotlinx.coroutines.delay(550); cursorVisible = !cursorVisible
        }
    }
    val palette = AppTheme.colors
    Surface(modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(18.dp), ambientColor = palette.shadowTint.copy(alpha = if (palette == DarkColors) .5f else .25f), spotColor = palette.shadowTint.copy(alpha = if (palette == DarkColors) .5f else .25f)), shape = RoundedCornerShape(18.dp), color = AppTheme.colors.surface,
        border = BorderStroke(1.dp, AppTheme.colors.line), shadowElevation = 0.dp) {
        Box {
            Column {
                Column(Modifier.fillMaxWidth().clickable(onClick = onCreate).padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("New page", "नया पेज"), Modifier.weight(1f), color = HomeBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp), tint = HomeBlue)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Write your first line", "अपनी पहली पंक्ति लिखें"), modifier = Modifier.weight(1f, fill = false), color = if (AppTheme.colors == DarkColors) AppTheme.colors.muted else AppTheme.colors.hint, fontSize = 18.sp)
                        Box(Modifier.padding(start = 3.dp).width(2.dp).height(21.dp).graphicsLayer { alpha = if (cursorVisible) 1f else 0f }.background(HomeBlue))
                    }
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = AppTheme.colors.line)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = AppTheme.colors.line)
                    Spacer(Modifier.height(4.dp))
                }
                if (latestDocument != null) {
                    HorizontalDivider(color = AppTheme.colors.line)
                    Row(Modifier.fillMaxWidth().background(AppTheme.colors.cardFooter).clickable { onResumeDocument(latestDocument) }
                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(22.dp), tint = HomeBlue)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Continue where you left", "जहाँ छोड़ा था, वहीं से जारी रखें"), fontSize = 12.sp, color = AppTheme.colors.muted)
                            Text(latestDocument.title.ifBlank { tr("Untitled document", "नया दस्तावेज़") }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp), tint = HomeBlue)
                    }
                }
            }
            val marginColor = AppTheme.colors.margin
            Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                drawLine(marginColor, androidx.compose.ui.geometry.Offset(11.dp.toPx(), 0f), androidx.compose.ui.geometry.Offset(11.dp.toPx(), size.height), 1.5.dp.toPx())
            }
        }
    }
}

@Composable
private fun ImportConvertPanel(
    importing: Boolean,
    onImport: () -> Unit,
    onExcel: () -> Unit,
    onPlanned: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            tr("Import and convert", "खोलें और बदलें"),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConvertTile(
                "DOCX",
                AppTheme.colors.accentText,
                Icons.Outlined.TextSnippet,
                Modifier.weight(1f),
                !importing,
                onImport,
            )
            ConvertTile(
                "Excel",
                AppTheme.colors.accentText,
                Icons.Outlined.TableChart,
                Modifier.weight(1f),
                !importing,
            ) {
                onExcel()
            }
            ConvertTile(
                tr("Image", "चित्र"),
                AppTheme.colors.accentText,
                Icons.Outlined.Image,
                Modifier.weight(1f),
                !importing,
            ) {
                onPlanned("Image")
            }
            ConvertTile(
                tr("More", "अन्य"),
                AppTheme.colors.accentText,
                Icons.Outlined.MoreHoriz,
                Modifier.weight(1f),
                !importing,
            ) {
                onPlanned("More")
            }
        }
    }
}

@Composable
private fun ConvertTile(
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.widthIn(max = 64.dp).fillMaxWidth()
                .heightIn(min = 48.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, AppTheme.colors.line, RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (!enabled)
                CircularProgressIndicator(
                    Modifier.size(24.dp),
                    color = HomeBlue,
                    strokeWidth = 2.dp,
                )
            else Icon(icon, label, Modifier.size(24.dp), tint = HomeBlue)
        }
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ImportTile(
    label: String,
    extension: String,
    color: Color,
    icon: ImageVector,
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(width)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .height(52.dp)
                .background(color.copy(alpha = .09f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            FileGlyph(color, Modifier.size(35.dp), icon = icon)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            extension,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FileGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    word: String? = null,
    whitePaper: Boolean = false,
) {
    val palette = AppTheme.colors
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val fold = size.width * .28f
            val paper =
                Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width - fold, 0f)
                    lineTo(size.width, fold)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(paper, if (whitePaper) palette.surface else color)
            drawPath(
                Path().apply {
                    moveTo(size.width - fold, 0f)
                    lineTo(size.width - fold, fold)
                    lineTo(size.width, fold)
                    close()
                },
                if (whitePaper) palette.margin else palette.ink.copy(alpha = .13f),
            )
        }
        if (icon != null)
            Icon(icon, null, Modifier.fillMaxSize(.58f).padding(top = 3.dp), tint = AppTheme.colors.onAccent)
        if (word != null)
            Text(
                word,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 7.dp),
            )
    }
}

@Composable
internal fun HomeSectionTitle(title: String, icon: ImageVector, onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onViewAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(tr("View all", "सभी देखें"), color = HomeBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.ChevronRight, null, tint = HomeBlue, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun HomeRecentGroup(
    documents: List<DocumentEntry>,
    onOpen: (DocumentEntry) -> Unit,
    onPdf: (DocumentEntry) -> Unit,
    onShare: (DocumentEntry) -> Unit,
    onRename: (DocumentEntry) -> Unit,
    onDuplicate: (DocumentEntry) -> Unit,
    onDelete: (DocumentEntry) -> Unit,
    onViewAll: () -> Unit,
    afterThirdDocument: @Composable () -> Unit = {},
) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeSectionTitle(
                tr("Recent Documents", "हाल के दस्तावेज़"),
                Icons.Rounded.Schedule,
                onViewAll,
            )
            if (documents.isEmpty())
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        tr(
                            "Your first document starts here",
                            "आपका पहला दस्तावेज़ यहाँ से शुरू होगा",
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        tr(
                            "Create a document or import Word, Excel or PDF.",
                            "नया दस्तावेज़ बनाएँ या Word, Excel या PDF खोलें।",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            documents.forEachIndexed { index, doc ->
                key(doc.id) {
                    HomeRecentRow(
                        doc,
                        { onOpen(doc) },
                        { onPdf(doc) },
                        { onShare(doc) },
                        { onRename(doc) },
                        { onDuplicate(doc) },
                        { onDelete(doc) },
                    )
                }
                if (index == minOf(2, documents.lastIndex)) key("recent-ad") { afterThirdDocument() }
            }
        }
    }
}

@Composable
private fun HomeRecentRow(
    doc: DocumentEntry,
    onOpen: () -> Unit,
    onPdf: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val date = Instant.ofEpochMilli(doc.modifiedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    val edited =
        when (date) {
            today -> tr("Edited today", "आज बदला गया")
            today.minusDays(1) -> tr("Edited yesterday", "कल बदला गया")
            else ->
                tr("Edited ", "बदला: ") +
                    date.format(
                        DateTimeFormatter.ofPattern(
                            "d MMM",
                            if (LocalHindi.current) Locale.forLanguageTag("hi-IN")
                            else Locale.ENGLISH,
                        )
                    )
        }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(homePanel())
            .border(
                1.dp,
                AppTheme.colors.line,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClickLabel = tr("Open document", "दस्तावेज़ खोलें"), onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(38.dp).height(46.dp).clip(RoundedCornerShape(6.dp)).background(AppTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp).background(AppTheme.colors.margin))
            Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = HomeBlue)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                doc.title.ifBlank { tr("Untitled Document", "नया दस्तावेज़") },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                edited + if (doc.isDraft) tr(" · Draft", " · ड्राफ़्ट") else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(tr("Continue", "जारी रखें"), color = HomeBlue)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    tr("Document actions", "दस्तावेज़ विकल्प"),
                    Modifier.size(21.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                        Triple(tr("Create PDF", "PDF बनाएँ"), Icons.Rounded.PictureAsPdf, onPdf),
                        Triple(tr("Rename", "नाम बदलें"), Icons.Rounded.Edit, onRename),
                        Triple(
                            tr("Duplicate", "कॉपी बनाएँ"),
                            Icons.Rounded.ContentCopy,
                            onDuplicate,
                        ),
                        Triple(tr("Delete", "हटाएँ"), Icons.Rounded.Delete, onDelete),
                    )
                    .forEach { (label, icon, action) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { Icon(icon, null) },
                            onClick = {
                                menu = false
                                action()
                            },
                            colors =
                                MenuDefaults.itemColors(
                                    textColor =
                                        if (icon == Icons.Rounded.Delete)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                                    leadingIconColor =
                                        if (icon == Icons.Rounded.Delete)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
                    }
            }
        }
    }
}

@Composable
internal fun HomeTemplateShelf(
    onCreate: () -> Unit,
    onTemplate: (Template) -> Unit,
    onViewAll: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = homePanel()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Box(Modifier.padding(horizontal = 12.dp)) {
                HomeSectionTitle(tr("Templates", "टेम्पलेट"), Icons.Rounded.Star, onViewAll)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = ((maxWidth - 48.dp) / 5).coerceAtLeast(64.dp)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        TemplateTile(
                            null,
                            tr("Blank Document", "खाली दस्तावेज़"),
                            AppTheme.colors.iconSoft,
                            width,
                            onCreate,
                        )
                    }
                    listOf("school", "resume", "invoice", "formal").forEach { id ->
                        Templates.all
                            .find { it.id == id }
                            ?.let { template ->
                                item(key = id) {
                                    val label =
                                        when (id) {
                                            "school" -> tr("Application", "आवेदन")
                                            "resume" -> tr("Resume", "रेज़्यूमे")
                                            "invoice" -> tr("Invoice", "बिल")
                                            else -> tr("Letter", "पत्र")
                                        }
                                    val color =
                                        when (id) {
                                            "school" -> AppTheme.colors.iconSoft
                                            "resume" -> AppTheme.colors.iconSoft
                                            "invoice" -> AppTheme.colors.iconSoft
                                            else -> AppTheme.colors.iconSoft
                                        }
                                    TemplateTile(template, label, color, width) {
                                        onTemplate(template)
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateTile(
    template: Template?,
    label: String,
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Column(
        Modifier.width(width)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .height(66.dp)
                .background(color, RoundedCornerShape(12.dp))
                .then(
                    if (template == null)
                        Modifier.border(1.5.dp, HomeRed, RoundedCornerShape(12.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            MiniPaper(
                if (template == null) listOf("", "", "")
                else remember(template.id) { template.filledParagraphs() },
                Modifier.width(43.dp).height(56.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HomeScreen() {
    val latest = DocumentEntry("preview", "Untitled document", System.currentTimeMillis(), System.currentTimeMillis(), false)
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
        HomeHeader("", {}, false, {}, {}, {})
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            HomeCreateActions(false, {}, {}, {}, {}, {}, {}, {})
            HomeRecentGroup(listOf(latest), {}, {}, {}, {}, {}, {}, {})
        }
        HomeNavigation("home", {}, {}, {}, {})
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Light", widthDp = 390, heightDp = 800)
@Composable
private fun HomeLightPreview() { A4Theme(darkTheme = false) { HomeScreen() } }

@androidx.compose.ui.tooling.preview.Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, widthDp = 390, heightDp = 800)
@Composable
private fun HomeDarkPreview() { A4Theme(darkTheme = true) { HomeScreen() } }
