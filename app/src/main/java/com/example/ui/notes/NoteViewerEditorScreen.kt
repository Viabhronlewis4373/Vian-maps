package com.example.ui.notes

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.notes.NoteChecklistItem
import com.example.data.notes.NoteColor
import com.example.data.notes.NoteFormatting
import com.example.data.notes.NoteType

@Composable
fun NoteViewerEditorScreen(
    editorState: NoteEditorUiState,
    onBack: () -> Unit,
    onEnterEditMode: () -> Unit,
    onDoneEditing: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onOpenColorPicker: () -> Unit,
    onDismissColorPicker: () -> Unit,
    onSelectColor: (NoteColor) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onOpenAddChecklistDialog: () -> Unit,
    onOpenEditChecklistDialog: (NoteChecklistItem) -> Unit,
    onDeleteChecklistItem: (String) -> Unit,
    onDialogTextChange: (String) -> Unit,
    onDialogNext: () -> Unit,
    onDialogOk: () -> Unit,
    onDialogCancel: () -> Unit,
    onDeleteNote: () -> Unit,
    onDiscard: () -> Unit,
    onBuildShareIntent: () -> Intent?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val note = editorState.note
    val activeColor = note.color
    var overflowExpanded by remember { mutableStateOf(false) }

    val bodyFocusRequester = remember { FocusRequester() }
    LaunchedEffect(note.id, editorState.isNewNoteSession) {
        if (editorState.isNewNoteSession && editorState.isEditing && note.noteType == NoteType.TEXT) {
            runCatching { bodyFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(activeColor.editorPaperBackground)
            .imePadding()
            .testTag("note_editor_screen")
    ) {
        // Saturated contrast-safe header
        Surface(
            color = activeColor.editorHeaderBackground,
            contentColor = activeColor.editorHeaderForeground,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("note_editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.notes_editor_back_cd),
                        tint = activeColor.editorHeaderForeground
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (editorState.isEditing) {
                        BasicTextField(
                            value = note.title,
                            onValueChange = onTitleChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = activeColor.editorHeaderForeground,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(activeColor.editorHeaderForeground),
                            decorationBox = { innerTextField ->
                                if (note.title.isEmpty()) {
                                    Text(
                                        text = stringResource(id = R.string.notes_title_placeholder),
                                        color = activeColor.editorHeaderForeground.copy(alpha = 0.65f),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_editor_title_input")
                        )
                    } else {
                        Text(
                            text = note.title.ifBlank { note.displayTitle },
                            color = activeColor.editorHeaderForeground,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onEnterEditMode)
                                .padding(vertical = 8.dp)
                                .testTag("note_viewer_title")
                        )
                    }
                }

                IconButton(
                    onClick = onOpenColorPicker,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("note_editor_color_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(activeColor.listBackground)
                            .border(
                                width = 2.dp,
                                color = activeColor.editorHeaderForeground,
                                shape = CircleShape
                            )
                    )
                }

                if (editorState.isEditing) {
                    IconButton(
                        onClick = onDoneEditing,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("note_editor_done_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(id = R.string.notes_editor_done_cd),
                            tint = activeColor.editorHeaderForeground
                        )
                    }
                } else {
                    IconButton(
                        onClick = onEnterEditMode,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("note_editor_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(id = R.string.notes_editor_edit_cd),
                            tint = activeColor.editorHeaderForeground
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { overflowExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("note_editor_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(id = R.string.notes_more_options_cd),
                            tint = activeColor.editorHeaderForeground
                        )
                    }

                    DropdownMenu(
                        expanded = overflowExpanded,
                        onDismissRequest = { overflowExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(id = R.string.notes_action_share)) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                            },
                            onClick = {
                                overflowExpanded = false
                                val sendIntent = onBuildShareIntent()
                                if (sendIntent != null) {
                                    val chooser = Intent.createChooser(
                                        sendIntent,
                                        context.getString(R.string.notes_share_chooser_title)
                                    )
                                    runCatching { context.startActivity(chooser) }
                                }
                            },
                            modifier = Modifier.testTag("note_editor_share_action")
                        )

                        if (editorState.canDiscardSessionChanges) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (editorState.isNewNoteSession) {
                                            stringResource(id = R.string.notes_action_discard)
                                        } else {
                                            stringResource(id = R.string.notes_action_discard_changes)
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Outlined.RestorePage, contentDescription = null)
                                },
                                onClick = {
                                    overflowExpanded = false
                                    onDiscard()
                                },
                                modifier = Modifier.testTag("note_editor_discard_action")
                            )
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(id = R.string.notes_action_delete),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                overflowExpanded = false
                                onDeleteNote()
                            },
                            modifier = Modifier.testTag("note_editor_delete_action")
                        )
                    }
                }
            }
        }

        // Subtle status and timestamp sub-bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(activeColor.editorPaperBackground)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (editorState.isEditing) {
                    stringResource(id = R.string.notes_mode_editing)
                } else {
                    stringResource(id = R.string.notes_mode_viewing)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = activeColor.saturatedStrip,
                modifier = Modifier.testTag("note_editor_mode_badge")
            )
            Text(
                text = stringResource(
                    id = R.string.notes_modified_timestamp,
                    NoteFormatting.formatDateTimeMedium(note.modifiedAt)
                ),
                fontSize = 12.sp,
                color = activeColor.editorPaperMutedText
            )
        }
        HorizontalDivider(thickness = 0.75.dp, color = activeColor.editorHeaderDividerColor)

        // Main note content surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (note.noteType) {
                NoteType.TEXT -> {
                    TextNotePaperBody(
                        body = note.body,
                        isEditing = editorState.isEditing,
                        activeColor = activeColor,
                        focusRequester = bodyFocusRequester,
                        onBodyChange = onBodyChange,
                        onEnterEditMode = onEnterEditMode
                    )
                }
                NoteType.CHECKLIST -> {
                    ChecklistNoteBody(
                        items = note.items,
                        isEditing = editorState.isEditing,
                        activeColor = activeColor,
                        onToggleItem = onToggleChecklistItem,
                        onOpenAddDialog = onOpenAddChecklistDialog,
                        onOpenEditDialog = onOpenEditChecklistDialog,
                        onDeleteItem = onDeleteChecklistItem,
                        onEnterEditMode = onEnterEditMode
                    )
                }
            }
        }

        // Edit-mode bottom toolbar (Undo / Redo)
        if (editorState.isEditing) {
            Surface(
                color = activeColor.editorPaperBackground,
                tonalElevation = 1.dp,
                shadowElevation = 4.dp
            ) {
                Column {
                    HorizontalDivider(thickness = 0.75.dp, color = activeColor.editorHeaderDividerColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onUndo,
                                enabled = editorState.canUndo,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("note_editor_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = stringResource(id = R.string.notes_editor_undo_cd),
                                    tint = if (editorState.canUndo) {
                                        activeColor.editorPaperText
                                    } else {
                                        activeColor.editorPaperMutedText.copy(alpha = 0.35f)
                                    }
                                )
                            }
                            IconButton(
                                onClick = onRedo,
                                enabled = editorState.canRedo,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("note_editor_redo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = stringResource(id = R.string.notes_editor_redo_cd),
                                    tint = if (editorState.canRedo) {
                                        activeColor.editorPaperText
                                    } else {
                                        activeColor.editorPaperMutedText.copy(alpha = 0.35f)
                                    }
                                )
                            }
                        }

                        val summaryText = when (note.noteType) {
                            NoteType.TEXT -> stringResource(
                                id = R.string.notes_char_count,
                                note.body.length
                            )
                            NoteType.CHECKLIST -> stringResource(
                                id = R.string.notes_checklist_count,
                                note.items.size
                            )
                        }
                        Text(
                            text = summaryText,
                            fontSize = 12.sp,
                            color = activeColor.editorPaperMutedText,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (editorState.showColorPicker) {
        NoteColorPickerDialog(
            selectedColor = activeColor,
            onSelectColor = onSelectColor,
            onDismiss = onDismissColorPicker
        )
    }

    val checklistDialog = editorState.checklistDialogState
    if (checklistDialog != null) {
        ChecklistItemDialog(
            state = checklistDialog,
            onTextChange = onDialogTextChange,
            onNext = onDialogNext,
            onOk = onDialogOk,
            onCancel = onDialogCancel
        )
    }
}

internal fun computeRuledLinePositions(
    lineBaselinesPx: List<Float>,
    topOffsetPx: Float,
    fallbackStepPx: Float,
    snugDescentPx: Float,
    totalHeightPx: Float
): List<Float> {
    if (totalHeightPx <= 0f || fallbackStepPx <= 1f) return emptyList()
    val positions = mutableListOf<Float>()
    val stepPx = if (lineBaselinesPx.size >= 2) {
        (lineBaselinesPx.last() - lineBaselinesPx[lineBaselinesPx.size - 2])
            .takeIf { it > 1f } ?: fallbackStepPx
    } else {
        fallbackStepPx
    }

    if (lineBaselinesPx.isNotEmpty()) {
        var lastRuleY = 0f
        for (baseline in lineBaselinesPx) {
            val ruleY = topOffsetPx + baseline + snugDescentPx
            lastRuleY = ruleY
            if (ruleY in 0f..totalHeightPx) {
                positions.add(ruleY)
            }
        }
        var nextY = lastRuleY + stepPx
        while (nextY < totalHeightPx) {
            if (nextY >= 0f) {
                positions.add(nextY)
            }
            nextY += stepPx
        }
    } else {
        var y = topOffsetPx + fallbackStepPx
        while (y < totalHeightPx) {
            if (y >= 0f) {
                positions.add(y)
            }
            y += fallbackStepPx
        }
    }
    return positions
}

@Composable
private fun TextNotePaperBody(
    body: String,
    isEditing: Boolean,
    activeColor: NoteColor,
    focusRequester: FocusRequester,
    onBodyChange: (String) -> Unit,
    onEnterEditMode: () -> Unit
) {
    val lineHeightSp = 30.sp
    val fontSizeSp = 16.sp
    val topPad = 10.dp
    val density = LocalDensity.current
    val lineHeightPx = with(density) { lineHeightSp.toPx() }
    val topPadPx = with(density) { topPad.toPx() }
    val snugDescentPx = with(density) { 3.5.sp.toPx() }
    val scrollState = rememberScrollState()
    val textLayoutState = remember { mutableStateOf<TextLayoutResult?>(null) }

    val paperTextStyle = TextStyle(
        color = activeColor.editorPaperText,
        fontSize = fontSizeSp,
        lineHeight = lineHeightSp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Bottom,
            trim = LineHeightStyle.Trim.None
        )
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .testTag("note_paper_scroll_container")
    ) {
        val minHeightDp = maxHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeightDp)
                .drawBehind {
                    val layout = textLayoutState.value
                    val baselines = if (layout != null && layout.lineCount > 0) {
                        List(layout.lineCount) { index -> layout.getLineBaseline(index) }
                    } else {
                        emptyList()
                    }
                    val rulePositions = computeRuledLinePositions(
                        lineBaselinesPx = baselines,
                        topOffsetPx = topPadPx,
                        fallbackStepPx = lineHeightPx,
                        snugDescentPx = snugDescentPx,
                        totalHeightPx = size.height
                    )
                    val ruleColor = activeColor.editorRuleLineColor
                    val stroke = 1.dp.toPx()
                    for (ruleY in rulePositions) {
                        drawLine(
                            color = ruleColor,
                            start = Offset(0f, ruleY),
                            end = Offset(size.width, ruleY),
                            strokeWidth = stroke
                        )
                    }
                }
                .padding(horizontal = 18.dp, vertical = topPad)
                .testTag("note_paper_surface")
        ) {
            if (isEditing) {
                BasicTextField(
                    value = body,
                    onValueChange = onBodyChange,
                    textStyle = paperTextStyle,
                    cursorBrush = SolidColor(activeColor.saturatedStrip),
                    onTextLayout = { textLayoutState.value = it },
                    decorationBox = { innerTextField ->
                        if (body.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.notes_body_placeholder),
                                style = paperTextStyle.copy(
                                    color = activeColor.editorPaperMutedText.copy(alpha = 0.65f)
                                )
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (minHeightDp - (topPad * 2)).coerceAtLeast(120.dp))
                        .focusRequester(focusRequester)
                        .testTag("note_editor_body_input")
                )
            } else {
                val viewModifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = (minHeightDp - (topPad * 2)).coerceAtLeast(120.dp))
                    .pointerInput(onEnterEditMode) {
                        detectTapGestures(onDoubleTap = { onEnterEditMode() })
                    }
                    .testTag("note_viewer_body")
                if (body.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.notes_viewer_empty_body),
                        style = paperTextStyle.copy(
                            color = activeColor.editorPaperMutedText.copy(alpha = 0.7f)
                        ),
                        onTextLayout = { textLayoutState.value = it },
                        modifier = viewModifier
                    )
                } else {
                    Text(
                        text = body,
                        style = paperTextStyle,
                        onTextLayout = { textLayoutState.value = it },
                        modifier = viewModifier
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistAddItemRow(
    activeColor: NoteColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = activeColor.editorPaperBackground,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = activeColor.saturatedStrip,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(id = R.string.notes_checklist_add_item),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = activeColor.saturatedStrip
            )
        }
    }
}

@Composable
private fun ChecklistNoteBody(
    items: List<NoteChecklistItem>,
    isEditing: Boolean,
    activeColor: NoteColor,
    onToggleItem: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (NoteChecklistItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onEnterEditMode: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(isEditing) {
        if (isEditing && listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (isEditing) "checklist_edit_list" else "checklist_view_list")
    ) {
        if (isEditing) {
            item(key = "add_item_header") {
                ChecklistAddItemRow(
                    activeColor = activeColor,
                    onClick = onOpenAddDialog,
                    modifier = Modifier.testTag("checklist_add_item_button")
                )
                HorizontalDivider(thickness = 0.75.dp, color = activeColor.editorRuleLineColor)
            }
        }

        if (items.isEmpty()) {
            if (isEditing) {
                item(key = "edit_empty_space") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("checklist_empty_gap")
                    )
                }
            } else {
                item(key = "empty_checklist") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.notes_checklist_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = activeColor.editorPaperMutedText
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        TextButton(
                            onClick = onEnterEditMode,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(text = stringResource(id = R.string.notes_checklist_tap_to_edit))
                        }
                    }
                }
            }
        } else {
            items(
                items = items.sortedBy { it.sortOrder },
                key = { it.id }
            ) { item ->
                if (isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clickable { onOpenEditDialog(item) }
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            .testTag("checklist_edit_item_${item.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.text,
                            fontSize = 16.sp,
                            color = if (item.isChecked) {
                                activeColor.editorPaperMutedText
                            } else {
                                activeColor.editorPaperText
                            },
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        )
                        IconButton(
                            onClick = { onDeleteItem(item.id) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("checklist_delete_item_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.notes_checklist_delete_item_cd),
                                tint = activeColor.editorPaperMutedText
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { onToggleItem(item.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("checklist_view_item_${item.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isChecked,
                            onCheckedChange = { onToggleItem(item.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = activeColor.saturatedStrip,
                                uncheckedColor = activeColor.editorPaperMutedText
                            ),
                            modifier = Modifier.testTag("checklist_checkbox_${item.id}")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.text,
                            fontSize = 16.sp,
                            color = if (item.isChecked) {
                                activeColor.editorPaperMutedText
                            } else {
                                activeColor.editorPaperText
                            },
                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp, top = 8.dp, bottom = 8.dp)
                        )
                    }
                }
                HorizontalDivider(thickness = 0.75.dp, color = activeColor.editorRuleLineColor)
            }
        }

        if (isEditing) {
            item(key = "add_item_footer") {
                if (items.isEmpty()) {
                    HorizontalDivider(thickness = 0.75.dp, color = activeColor.editorRuleLineColor)
                }
                ChecklistAddItemRow(
                    activeColor = activeColor,
                    onClick = onOpenAddDialog,
                    modifier = Modifier.testTag("checklist_add_item_bottom_button")
                )
                HorizontalDivider(thickness = 0.75.dp, color = activeColor.editorRuleLineColor)
            }
        }
    }
}

/**
 * ColorNote-style Add / Edit checklist item dialog with NEXT, CANCEL, and OK.
 */
@Composable
fun ChecklistItemDialog(
    state: ChecklistItemDialogState,
    onTextChange: (String) -> Unit,
    onNext: () -> Unit,
    onOk: () -> Unit,
    onCancel: () -> Unit
) {
    val inputFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.editingItemId) {
        runCatching { inputFocusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("checklist_item_dialog"),
        title = {
            Text(
                text = if (state.isEditExisting) {
                    stringResource(id = R.string.notes_checklist_edit_item_title)
                } else {
                    stringResource(id = R.string.notes_checklist_add_item_title)
                }
            )
        },
        text = {
            OutlinedTextField(
                value = state.text,
                onValueChange = onTextChange,
                singleLine = true,
                placeholder = {
                    Text(text = stringResource(id = R.string.notes_checklist_item_hint))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester)
                    .testTag("checklist_item_dialog_input")
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("checklist_item_dialog_cancel")
                ) {
                    Text(text = stringResource(id = R.string.notes_action_cancel))
                }
                if (!state.isEditExisting) {
                    TextButton(
                        onClick = onNext,
                        enabled = state.text.isNotBlank(),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("checklist_item_dialog_next")
                    ) {
                        Text(text = stringResource(id = R.string.notes_action_next))
                    }
                }
                TextButton(
                    onClick = onOk,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("checklist_item_dialog_ok")
                ) {
                    Text(text = stringResource(id = R.string.notes_action_ok))
                }
            }
        }
    )
}
