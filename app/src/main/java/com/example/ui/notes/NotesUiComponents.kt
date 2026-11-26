package com.example.ui.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.notes.NoteColor
import kotlin.math.abs

internal val NotesTealAccent = Color(0xFF00897B)
private val NotesBottomBarInactiveContent = Color(0xFF64748B)
private val NotesBottomBarContourBorder = Color(0xFFE2E8F0)

private val FAB_SIZE = 56.dp
private val FAB_RAISED_OFFSET = 22.dp
private val CRADLE_HALF_SPAN = 46.dp
private val CRADLE_DEPTH = 38.dp
private val BAR_CONTENT_HEIGHT = 62.dp
private val CENTER_CRADLE_SPACER_WIDTH = 72.dp

/**
 * Computes the vertical concave cradle dip (`0f` on the flat horizontal shoulders, up to
 * `depthPx` at `centerXPx`) matching the cubic Bezier valley rendered by [buildConcaveCradleTopPath].
 */
internal fun computeConcaveCradleOffsetY(
    xPx: Float,
    centerXPx: Float,
    halfSpanPx: Float,
    depthPx: Float
): Float {
    if (halfSpanPx <= 0f || depthPx <= 0f) return 0f
    val dx = abs(xPx - centerXPx)
    if (dx >= halfSpanPx) return 0f
    if (dx <= 1e-4f) return depthPx

    val p0x = halfSpanPx
    val p1x = halfSpanPx * 0.70f
    val p2x = halfSpanPx * 0.60f

    var low = 0f
    var high = 1f
    repeat(20) {
        val t = (low + high) * 0.5f
        val u = 1f - t
        val bx = (u * u * u * p0x) +
            (3f * u * u * t * p1x) +
            (3f * u * t * t * p2x)
        if (bx > dx) {
            low = t
        } else {
            high = t
        }
    }
    val t = (low + high) * 0.5f
    val u = 1f - t
    val normalizedY = (3f * u * t * t) + (t * t * t)
    return (normalizedY * depthPx).coerceIn(0f, depthPx)
}

internal fun buildConcaveCradleTopPath(
    width: Float,
    halfSpanPx: Float,
    depthPx: Float,
    path: Path = Path()
): Path {
    path.reset()
    val centerX = width * 0.5f
    val clampedHalfSpan = halfSpanPx.coerceIn(0f, centerX)
    val clampedDepth = depthPx.coerceAtLeast(0f)

    path.moveTo(0f, 0f)
    if (clampedHalfSpan <= 0f || clampedDepth <= 0f) {
        path.lineTo(width, 0f)
        return path
    }

    val leftShoulder = centerX - clampedHalfSpan
    val rightShoulder = centerX + clampedHalfSpan
    val leftC1x = centerX - clampedHalfSpan * 0.70f
    val leftC2x = centerX - clampedHalfSpan * 0.60f
    val rightC2x = centerX + clampedHalfSpan * 0.60f
    val rightC1x = centerX + clampedHalfSpan * 0.70f

    path.lineTo(leftShoulder, 0f)
    path.cubicTo(
        leftC1x, 0f,
        leftC2x, clampedDepth,
        centerX, clampedDepth
    )
    path.cubicTo(
        rightC2x, clampedDepth,
        rightC1x, 0f,
        rightShoulder, 0f
    )
    path.lineTo(width, 0f)
    return path
}

internal fun buildConcaveCradleOutlinePath(
    width: Float,
    height: Float,
    halfSpanPx: Float,
    depthPx: Float,
    path: Path = Path()
): Path {
    val safeDepth = depthPx.coerceIn(0f, (height - 1f).coerceAtLeast(0f))
    buildConcaveCradleTopPath(
        width = width,
        halfSpanPx = halfSpanPx,
        depthPx = safeDepth,
        path = path
    )
    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()
    return path
}

/**
 * Five-slot ColorNote-inspired Notes workspace bottom bar:
 * Left: Notes | Habits   Center: Raised Teal + inside concave cradle   Right: Expense | Calendar
 */
@Composable
fun NotesFiveSlotBottomBar(
    activeTab: NotesBottomTab,
    onSelectTab: (NotesBottomTab) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val halfSpanPx = with(density) { CRADLE_HALF_SPAN.toPx() }
    val depthPx = with(density) { CRADLE_DEPTH.toPx() }

    val sculptedBarShape = remember(halfSpanPx, depthPx) {
        GenericShape { size, _ ->
            buildConcaveCradleOutlinePath(
                width = size.width,
                height = size.height,
                halfSpanPx = halfSpanPx,
                depthPx = depthPx,
                path = this
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notes_bottom_bar_container")
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FAB_RAISED_OFFSET)
                .align(Alignment.BottomCenter)
                .testTag("notes_bottom_bar_surface"),
            shape = sculptedBarShape,
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val topContourPath = buildConcaveCradleTopPath(
                            width = size.width,
                            halfSpanPx = halfSpanPx,
                            depthPx = depthPx.coerceIn(0f, (size.height - 1f).coerceAtLeast(0f))
                        )
                        val strokeStyle = Stroke(
                            width = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        onDrawWithContent {
                            drawContent()
                            drawPath(
                                path = topContourPath,
                                color = NotesBottomBarContourBorder,
                                style = strokeStyle
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(BAR_CONTENT_HEIGHT)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NotesBottomNavTabItem(
                        label = stringResource(id = R.string.notes_tab_notes),
                        icon = if (activeTab == NotesBottomTab.NOTES) {
                            Icons.Filled.Description
                        } else {
                            Icons.Outlined.Description
                        },
                        selected = activeTab == NotesBottomTab.NOTES,
                        testTag = "notes_bottom_nav_notes",
                        onClick = { onSelectTab(NotesBottomTab.NOTES) },
                        modifier = Modifier.weight(1f)
                    )

                    NotesBottomNavTabItem(
                        label = stringResource(id = R.string.notes_tab_habits),
                        icon = if (activeTab == NotesBottomTab.HABITS) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Outlined.CheckCircle
                        },
                        selected = activeTab == NotesBottomTab.HABITS,
                        testTag = "notes_bottom_nav_habits",
                        onClick = { onSelectTab(NotesBottomTab.HABITS) },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(CENTER_CRADLE_SPACER_WIDTH))

                    NotesBottomNavTabItem(
                        label = stringResource(id = R.string.notes_tab_expense),
                        icon = if (activeTab == NotesBottomTab.EXPENSE) {
                            Icons.Filled.AccountBalanceWallet
                        } else {
                            Icons.Outlined.AccountBalanceWallet
                        },
                        selected = activeTab == NotesBottomTab.EXPENSE,
                        testTag = "notes_bottom_nav_expense",
                        onClick = { onSelectTab(NotesBottomTab.EXPENSE) },
                        modifier = Modifier.weight(1f)
                    )

                    NotesBottomNavTabItem(
                        label = stringResource(id = R.string.notes_tab_calendar),
                        icon = if (activeTab == NotesBottomTab.CALENDAR) {
                            Icons.Filled.CalendarMonth
                        } else {
                            Icons.Outlined.CalendarMonth
                        },
                        selected = activeTab == NotesBottomTab.CALENDAR,
                        testTag = "notes_bottom_nav_calendar",
                        onClick = { onSelectTab(NotesBottomTab.CALENDAR) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Surface(
            onClick = onAddClick,
            shape = CircleShape,
            color = NotesTealAccent,
            contentColor = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(FAB_SIZE)
                .testTag("notes_bottom_nav_add")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.notes_add_button_cd),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun NotesBottomNavTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) {
        NotesTealAccent
    } else {
        NotesBottomBarInactiveContent
    }

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Clean Material 3 Add chooser offering only Stage 1 note creation types: Text Note and Checklist.
 */
@Composable
fun NoteAddChooserDialog(
    onCreateTextNote: () -> Unit,
    onCreateChecklist: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("notes_add_chooser_dialog"),
        title = {
            Text(text = stringResource(id = R.string.notes_add_chooser_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AddChooserOptionCard(
                    title = stringResource(id = R.string.notes_add_text_note),
                    subtitle = stringResource(id = R.string.notes_add_text_note_desc),
                    icon = Icons.Outlined.Description,
                    testTag = "notes_add_text_note",
                    onClick = onCreateTextNote
                )
                AddChooserOptionCard(
                    title = stringResource(id = R.string.notes_add_checklist),
                    subtitle = stringResource(id = R.string.notes_add_checklist_desc),
                    icon = Icons.Outlined.Checklist,
                    testTag = "notes_add_checklist",
                    onClick = onCreateChecklist
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(id = R.string.notes_action_cancel))
            }
        }
    )
}

@Composable
private fun AddChooserOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact 3x3 Color Picker dialog presenting all nine unified palette colors in fixed palette order.
 */
@Composable
fun NoteColorPickerDialog(
    selectedColor: NoteColor?,
    onSelectColor: (NoteColor) -> Unit,
    onDismiss: () -> Unit,
    testTag: String = "note_color_picker_dialog"
) {
    val rows = NoteColor.orderedColors.chunked(3)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(testTag),
        title = {
            Text(text = stringResource(id = R.string.notes_color_picker_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowColors.forEach { color ->
                            val isSelected = selectedColor == color
                            Surface(
                                onClick = { onSelectColor(color) },
                                shape = RoundedCornerShape(10.dp),
                                color = color.listBackground,
                                border = BorderStroke(
                                    width = if (isSelected) 2.5.dp else 1.5.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else color.saturatedStrip
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("note_color_swatch_${color.key}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(color.saturatedStrip)
                                            .border(
                                                1.dp,
                                                Color(0x33000000),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = color.editorHeaderForeground,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = color.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = Color(0xFF1A1A1A),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(id = R.string.notes_action_cancel))
            }
        }
    )
}

/**
 * Honest Stage-2 placeholder content for Habits, Expense, and Calendar tabs.
 * Contains no fake sample data, charts, or non-functional buttons.
 */
@Composable
fun NotesStage2PlaceholderContent(
    tab: NotesBottomTab,
    modifier: Modifier = Modifier
) {
    val icon = when (tab) {
        NotesBottomTab.HABITS -> Icons.Outlined.CheckCircle
        NotesBottomTab.EXPENSE -> Icons.Outlined.AccountBalanceWallet
        NotesBottomTab.CALENDAR -> Icons.Outlined.CalendarMonth
        NotesBottomTab.NOTES -> Icons.AutoMirrored.Outlined.ListAlt
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .testTag("notes_stage2_placeholder_${tab.name.lowercase()}"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.notes_future_module_title, tab.label),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.notes_future_module_subtitle, tab.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
