package com.example.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.notes.NoteSortOrder
import com.example.data.notes.NoteType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Standalone Notes workspace root composable hosted inside AppHostShell.
 * Completely independent from MapFeatureActivation, MapView, and Memory Palace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesHostScreen(
    onReturnToMap: () -> Unit,
    modifier: Modifier = Modifier,
    notesViewModel: NotesViewModel = viewModel()
) {
    val uiState by notesViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val undoActionLabel = stringResource(id = R.string.notes_action_undo)

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(notesViewModel) {
        notesViewModel.events.collectLatest { event ->
            when (event) {
                is NotesUiEvent.ShowUndoDeleteSnackbar -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = undoActionLabel,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        notesViewModel.restoreDeletedNotes(event.deletedIds)
                    }
                }
                is NotesUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val openedEditor = uiState.editor

    BackHandler {
        when {
            openedEditor != null -> {
                when {
                    openedEditor.checklistDialogState != null -> notesViewModel.cancelChecklistDialog()
                    openedEditor.showColorPicker -> notesViewModel.dismissEditorColorPicker()
                    else -> notesViewModel.onEditorBackPressed()
                }
            }
            uiState.showBulkColorPicker -> notesViewModel.dismissBulkColorPicker()
            uiState.showAddChooser -> notesViewModel.dismissAddChooser()
            uiState.isSelectionMode -> notesViewModel.exitSelectionMode()
            uiState.isSearchActive -> notesViewModel.toggleSearch(false)
            uiState.activeTab != NotesBottomTab.NOTES -> notesViewModel.selectBottomTab(NotesBottomTab.NOTES)
            else -> onReturnToMap()
        }
    }

    if (openedEditor != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("notes_workspace_screen")
        ) {
            NoteViewerEditorScreen(
                editorState = openedEditor,
                onBack = { notesViewModel.onEditorBackPressed() },
                onEnterEditMode = { notesViewModel.enterEditMode() },
                onDoneEditing = { notesViewModel.finishEditingToViewMode() },
                onTitleChange = { notesViewModel.updateEditorTitle(it) },
                onBodyChange = { notesViewModel.updateEditorBody(it) },
                onOpenColorPicker = { notesViewModel.openEditorColorPicker() },
                onDismissColorPicker = { notesViewModel.dismissEditorColorPicker() },
                onSelectColor = { notesViewModel.updateEditorColor(it) },
                onUndo = { notesViewModel.undoEditorChange() },
                onRedo = { notesViewModel.redoEditorChange() },
                onToggleChecklistItem = { notesViewModel.toggleChecklistItemInViewOrEdit(it) },
                onOpenAddChecklistDialog = { notesViewModel.openAddChecklistItemDialog() },
                onOpenEditChecklistDialog = { notesViewModel.openEditChecklistItemDialog(it) },
                onDeleteChecklistItem = { notesViewModel.deleteChecklistItem(it) },
                onDialogTextChange = { notesViewModel.updateChecklistDialogText(it) },
                onDialogNext = { notesViewModel.confirmChecklistDialogNext() },
                onDialogOk = { notesViewModel.confirmChecklistDialogOk() },
                onDialogCancel = { notesViewModel.cancelChecklistDialog() },
                onDeleteNote = { notesViewModel.deleteOpenedNote() },
                onDiscard = { notesViewModel.discardEditorChangesOrNewNote() },
                onBuildShareIntent = { notesViewModel.buildShareIntentForOpenedNote() }
            )
        }
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("notes_workspace_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = { notesViewModel.exitSelectionMode() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("notes_selection_close")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(id = R.string.notes_selection_close_cd)
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(
                                id = R.string.notes_selection_count,
                                uiState.selectedNoteIds.size
                            )
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = { notesViewModel.selectAllVisibleNotes() },
                            modifier = Modifier.testTag("notes_selection_select_all")
                        ) {
                            Text(text = stringResource(id = R.string.notes_selection_select_all))
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    notesViewModel.closeEditorSafely()
                                    onReturnToMap()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("notes_return_to_map_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.notes_return_to_map)
                            )
                        }
                    },
                    title = {
                        Text(text = uiState.activeTab.label)
                    },
                    actions = {
                        if (uiState.activeTab == NotesBottomTab.NOTES) {
                            IconButton(
                                onClick = { notesViewModel.toggleSearch(!uiState.isSearchActive) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("notes_search_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringResource(id = R.string.notes_search_cd)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("notes_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(id = R.string.notes_sort_cd)
                                    )
                                }

                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    NoteSortOrder.entries.forEach { order ->
                                        val sortTag = when (order) {
                                            NoteSortOrder.MODIFIED -> "notes_sort_modified"
                                            NoteSortOrder.CREATED -> "notes_sort_created"
                                            NoteSortOrder.ALPHABETICAL -> "notes_sort_alphabetical"
                                            NoteSortOrder.COLOR -> "notes_sort_color"
                                        }
                                        DropdownMenuItem(
                                            text = { Text(text = order.label) },
                                            trailingIcon = {
                                                if (uiState.sortOrder == order) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            onClick = {
                                                sortMenuExpanded = false
                                                notesViewModel.setSortOrder(order)
                                            },
                                            modifier = Modifier.testTag(sortTag)
                                        )
                                    }
                                }
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { overflowMenuExpanded = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("notes_overflow_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(id = R.string.notes_more_options_cd)
                                )
                            }

                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false }
                            ) {
                                if (uiState.activeTab == NotesBottomTab.NOTES && uiState.visibleNotes.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(id = R.string.notes_menu_select_notes)) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Outlined.CheckBox, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowMenuExpanded = false
                                            notesViewModel.enterSelectionMode()
                                        },
                                        modifier = Modifier.testTag("notes_menu_select_notes")
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(id = R.string.notes_return_to_map)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        onReturnToMap()
                                    },
                                    modifier = Modifier.testTag("notes_menu_return_to_map")
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (uiState.isSelectionMode) {
                NotesMultiSelectBottomBar(
                    selectedCount = uiState.selectedNoteIds.size,
                    onChangeColorClick = { notesViewModel.openBulkColorPicker() },
                    onDeleteClick = { notesViewModel.deleteSelectedNotes() }
                )
            } else {
                NotesFiveSlotBottomBar(
                    activeTab = uiState.activeTab,
                    onSelectTab = { notesViewModel.selectBottomTab(it) },
                    onAddClick = { notesViewModel.openAddChooser() }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.activeTab == NotesBottomTab.NOTES && uiState.isSearchActive && !uiState.isSelectionMode) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { notesViewModel.updateSearchQuery(it) },
                    singleLine = true,
                    placeholder = {
                        Text(text = stringResource(id = R.string.notes_search_placeholder))
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { notesViewModel.updateSearchQuery("") },
                                modifier = Modifier.testTag("notes_search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(id = R.string.notes_search_clear)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("notes_search_input")
                )
                HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }

            when (uiState.activeTab) {
                NotesBottomTab.NOTES -> {
                    NotesListContent(
                        notes = uiState.visibleNotes,
                        searchQuery = uiState.searchQuery,
                        isSelectionMode = uiState.isSelectionMode,
                        selectedNoteIds = uiState.selectedNoteIds,
                        onNoteClick = { note ->
                            if (uiState.isSelectionMode) {
                                notesViewModel.toggleNoteSelection(note.id)
                            } else {
                                notesViewModel.openExistingNote(note.id)
                            }
                        },
                        onNoteLongClick = { note ->
                            if (uiState.isSelectionMode) {
                                notesViewModel.toggleNoteSelection(note.id)
                            } else {
                                notesViewModel.enterSelectionMode(note.id)
                            }
                        },
                        onClearSearch = { notesViewModel.toggleSearch(false) },
                        modifier = Modifier.weight(1f)
                    )
                }
                NotesBottomTab.HABITS,
                NotesBottomTab.EXPENSE,
                NotesBottomTab.CALENDAR -> {
                    NotesStage2PlaceholderContent(
                        tab = uiState.activeTab,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (uiState.showAddChooser) {
        NoteAddChooserDialog(
            onCreateTextNote = { notesViewModel.createNewNote(NoteType.TEXT) },
            onCreateChecklist = { notesViewModel.createNewNote(NoteType.CHECKLIST) },
            onDismiss = { notesViewModel.dismissAddChooser() }
        )
    }

    if (uiState.showBulkColorPicker) {
        NoteColorPickerDialog(
            selectedColor = null,
            onSelectColor = { notesViewModel.applyBulkColor(it) },
            onDismiss = { notesViewModel.dismissBulkColorPicker() },
            testTag = "notes_bulk_color_dialog"
        )
    }
}
