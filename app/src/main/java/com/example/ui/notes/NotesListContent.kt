package com.example.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.notes.NoteFormatting
import com.example.data.notes.NoteRecord
import com.example.data.notes.NoteType

/**
 * Legacy ColorNote-style vertical flat list:
 * - Full-width rows with zero outer card corners or card gaps
 * - Thin horizontal dividers
 * - 7dp saturated left color strip + soft pastel row background
 * - Bold display title (max 2 lines, untitled fallback) + right-aligned date
 * - Subtle checklist indicator, zero body previews, zero tags
 */
@Composable
fun NotesListContent(
    notes: List<NoteRecord>,
    searchQuery: String,
    isSelectionMode: Boolean,
    selectedNoteIds: Set<String>,
    onNoteClick: (NoteRecord) -> Unit,
    onNoteLongClick: (NoteRecord) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        val isSearching = searchQuery.isNotBlank()
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .testTag(if (isSearching) "notes_search_empty_state" else "notes_empty_state"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Outlined.SearchOff else Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isSearching) {
                    stringResource(id = R.string.notes_search_empty_title)
                } else {
                    stringResource(id = R.string.notes_empty_title)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearching) {
                    stringResource(id = R.string.notes_search_empty_subtitle)
                } else {
                    stringResource(id = R.string.notes_empty_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (isSearching) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onClearSearch,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(id = R.string.notes_search_clear))
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("notes_list")
    ) {
        items(
            items = notes,
            key = { it.id }
        ) { note ->
            val isSelected = selectedNoteIds.contains(note.id)
            NoteListRow(
                note = note,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onClick = { onNoteClick(note) },
                onLongClick = { onNoteLongClick(note) }
            )
            HorizontalDivider(
                thickness = 0.75.dp,
                color = Color(0x1F0F172A)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteListRow(
    note: NoteRecord,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val rowBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    } else {
        note.color.listBackground
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 68.dp)
            .testTag("note_row_${note.id}")
            .background(rowBg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Saturated vertical left strip (ColorNote signature)
        Box(
            modifier = Modifier
                .width(7.dp)
                .fillMaxHeight()
                .background(note.color.saturatedStrip)
                .testTag("note_row_strip_${note.id}")
        )

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF64748B),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(22.dp)
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (note.noteType == NoteType.CHECKLIST) {
                Icon(
                    imageVector = Icons.Outlined.CheckBox,
                    contentDescription = "Checklist",
                    tint = Color(0xFF475569),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(17.dp)
                        .testTag("note_row_checklist_icon_${note.id}")
                )
            }

            Text(
                text = note.displayTitle,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = NoteFormatting.formatListRowDate(note.modifiedAt),
                fontSize = 12.sp,
                color = Color(0xFF525252),
                maxLines = 1
            )
        }
    }
}

/**
 * Bulk selection action bar displaying ONLY the two approved Stage 1 actions:
 * Change Color and Delete (soft-delete with Undo).
 */
@Composable
fun NotesMultiSelectBottomBar(
    selectedCount: Int,
    onChangeColorClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notes_bulk_action_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column {
            HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onChangeColorClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("notes_bulk_color_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.notes_bulk_change_color))
                }

                TextButton(
                    onClick = onDeleteClick,
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("notes_bulk_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.notes_bulk_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
