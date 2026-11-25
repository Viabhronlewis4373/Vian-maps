package com.example.ui.notes

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.notes.NoteChecklistItem
import com.example.data.notes.NoteColor
import com.example.data.notes.NoteFormatting
import com.example.data.notes.NoteRecord
import com.example.data.notes.NoteSortOrder
import com.example.data.notes.NoteType
import com.example.data.notes.NotesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class NotesBottomTab(val label: String) {
    NOTES("Notes"),
    HABITS("Habits"),
    EXPENSE("Expense"),
    CALENDAR("Calendar")
}

data class NoteEditorSnapshot(
    val title: String,
    val body: String,
    val color: NoteColor,
    val items: List<NoteChecklistItem>
) {
    companion object {
        fun fromNote(note: NoteRecord): NoteEditorSnapshot = NoteEditorSnapshot(
            title = note.title,
            body = note.body,
            color = note.color,
            items = note.items.map { it.copy() }
        )
    }
}

data class NoteEditorUiState(
    val note: NoteRecord,
    val isEditing: Boolean,
    val isNewNoteSession: Boolean,
    val isPersistedInDb: Boolean,
    val baselineForExistingEdit: NoteEditorSnapshot? = null,
    val undoStack: List<NoteEditorSnapshot> = emptyList(),
    val redoStack: List<NoteEditorSnapshot> = emptyList(),
    val editRevision: Long = 0L,
    val showColorPicker: Boolean = false,
    val checklistDialogState: ChecklistItemDialogState? = null
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val canDiscardSessionChanges: Boolean
        get() = isNewNoteSession || (isEditing && baselineForExistingEdit != null &&
            NoteEditorSnapshot.fromNote(note) != baselineForExistingEdit)
}

data class ChecklistItemDialogState(
    val editingItemId: String? = null,
    val text: String = ""
) {
    val isEditExisting: Boolean get() = editingItemId != null
}

data class NotesWorkspaceUiState(
    val activeTab: NotesBottomTab = NotesBottomTab.NOTES,
    val allActiveNotes: List<NoteRecord> = emptyList(),
    val visibleNotes: List<NoteRecord> = emptyList(),
    val sortOrder: NoteSortOrder = NoteSortOrder.MODIFIED,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isSelectionMode: Boolean = false,
    val selectedNoteIds: Set<String> = emptySet(),
    val showAddChooser: Boolean = false,
    val showBulkColorPicker: Boolean = false,
    val editor: NoteEditorUiState? = null,
    val isLoading: Boolean = true
)

sealed class NotesUiEvent {
    data class ShowUndoDeleteSnackbar(
        val deletedIds: List<String>,
        val message: String
    ) : NotesUiEvent()

    data class ShowMessage(val message: String) : NotesUiEvent()
}

object NotesShareHelper {
    fun buildShareIntent(note: NoteRecord): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, NoteFormatting.formatShareSubject(note))
            putExtra(Intent.EXTRA_TEXT, NoteFormatting.formatShareBody(note))
        }
    }
}

@OptIn(FlowPreview::class)
class NotesViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NotesRepository = NotesRepository(AppDatabase.getInstance(application).notesDao()),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val autosaveDebounceMs: Long = 350L
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NotesWorkspaceUiState())
    val uiState: StateFlow<NotesWorkspaceUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<NotesUiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NotesUiEvent> = _events.asSharedFlow()

    private val saveMutex = Mutex()
    private var autosaveJob: Job? = null
    private var lastPersistedRevision: Long = -1L

    init {
        viewModelScope.launch {
            repository.observeActiveNotes().collectLatest { activeNotes ->
                val current = _uiState.value
                val activeIdSet = activeNotes.map { it.id }.toSet()
                val cleanedSelection = current.selectedNoteIds.intersect(activeIdSet)
                val keepSelectionMode = current.isSelectionMode && cleanedSelection.isNotEmpty()
                val nextVisible = computeVisibleNotes(
                    notes = activeNotes,
                    sortOrder = current.sortOrder,
                    searchQuery = current.searchQuery
                )
                _uiState.value = current.copy(
                    allActiveNotes = activeNotes,
                    visibleNotes = nextVisible,
                    selectedNoteIds = cleanedSelection,
                    isSelectionMode = keepSelectionMode,
                    showBulkColorPicker = if (keepSelectionMode) current.showBulkColorPicker else false,
                    isLoading = false
                )
            }
        }
    }

    private fun computeVisibleNotes(
        notes: List<NoteRecord>,
        sortOrder: NoteSortOrder,
        searchQuery: String
    ): List<NoteRecord> {
        val filtered = NoteFormatting.filterActiveNotes(notes, searchQuery)
        return NoteFormatting.sortNotes(filtered, sortOrder)
    }

    fun selectBottomTab(tab: NotesBottomTab) {
        _uiState.value = _uiState.value.copy(
            activeTab = tab,
            showAddChooser = false
        )
    }

    fun openAddChooser() {
        _uiState.value = _uiState.value.copy(
            showAddChooser = true,
            isSelectionMode = false,
            selectedNoteIds = emptySet(),
            showBulkColorPicker = false
        )
    }

    fun dismissAddChooser() {
        _uiState.value = _uiState.value.copy(showAddChooser = false)
    }

    fun toggleSearch(active: Boolean) {
        val current = _uiState.value
        val nextQuery = if (active) current.searchQuery else ""
        _uiState.value = current.copy(
            isSearchActive = active,
            searchQuery = nextQuery,
            visibleNotes = computeVisibleNotes(
                notes = current.allActiveNotes,
                sortOrder = current.sortOrder,
                searchQuery = nextQuery
            )
        )
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            searchQuery = query,
            visibleNotes = computeVisibleNotes(
                notes = current.allActiveNotes,
                sortOrder = current.sortOrder,
                searchQuery = query
            )
        )
    }

    fun setSortOrder(sortOrder: NoteSortOrder) {
        val current = _uiState.value
        _uiState.value = current.copy(
            sortOrder = sortOrder,
            visibleNotes = computeVisibleNotes(
                notes = current.allActiveNotes,
                sortOrder = sortOrder,
                searchQuery = current.searchQuery
            )
        )
    }

    // ---- Multi-select actions ----

    fun enterSelectionMode(initialNoteId: String? = null) {
        val current = _uiState.value
        if (current.editor != null) return
        val activeIds = current.allActiveNotes.map { it.id }.toSet()
        val initialSelection = if (initialNoteId != null && activeIds.contains(initialNoteId)) {
            setOf(initialNoteId)
        } else {
            emptySet()
        }
        _uiState.value = current.copy(
            isSelectionMode = true,
            selectedNoteIds = initialSelection,
            showBulkColorPicker = false
        )
    }

    fun toggleNoteSelection(noteId: String) {
        val current = _uiState.value
        val updated = if (current.selectedNoteIds.contains(noteId)) {
            current.selectedNoteIds - noteId
        } else {
            current.selectedNoteIds + noteId
        }
        _uiState.value = current.copy(
            isSelectionMode = updated.isNotEmpty() || current.isSelectionMode,
            selectedNoteIds = updated
        )
    }

    fun selectAllVisibleNotes() {
        val current = _uiState.value
        val ids = current.visibleNotes.map { it.id }.toSet()
        _uiState.value = current.copy(
            isSelectionMode = true,
            selectedNoteIds = ids
        )
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = false,
            selectedNoteIds = emptySet(),
            showBulkColorPicker = false
        )
    }

    fun openBulkColorPicker() {
        val current = _uiState.value
        if (current.selectedNoteIds.isEmpty()) return
        _uiState.value = current.copy(showBulkColorPicker = true)
    }

    fun dismissBulkColorPicker() {
        _uiState.value = _uiState.value.copy(showBulkColorPicker = false)
    }

    fun applyBulkColor(color: NoteColor) {
        val targetIds = _uiState.value.selectedNoteIds.toList()
        if (targetIds.isEmpty()) {
            dismissBulkColorPicker()
            return
        }
        viewModelScope.launch {
            repository.updateNotesColor(targetIds, color)
            _uiState.value = _uiState.value.copy(
                isSelectionMode = false,
                selectedNoteIds = emptySet(),
                showBulkColorPicker = false
            )
        }
    }

    fun deleteSelectedNotes() {
        val targetIds = _uiState.value.selectedNoteIds.toList()
        if (targetIds.isEmpty()) {
            exitSelectionMode()
            return
        }
        viewModelScope.launch {
            val deletedCount = repository.softDeleteNotes(targetIds)
            _uiState.value = _uiState.value.copy(
                isSelectionMode = false,
                selectedNoteIds = emptySet(),
                showBulkColorPicker = false
            )
            if (deletedCount > 0) {
                val msg = if (deletedCount == 1) "Note deleted" else "$deletedCount notes deleted"
                _events.tryEmit(NotesUiEvent.ShowUndoDeleteSnackbar(targetIds, msg))
            }
        }
    }

    fun restoreDeletedNotes(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            repository.restoreNotes(noteIds)
        }
    }

    // ---- Note opening & creation ----

    fun createNewNote(type: NoteType) {
        val now = clock()
        val draft = NoteRecord(
            id = UUID.randomUUID().toString(),
            title = "",
            body = "",
            noteType = type,
            color = NoteColor.DEFAULT,
            isArchived = false,
            isDeleted = false,
            deletedAt = null,
            createdAt = now,
            modifiedAt = now,
            items = emptyList()
        )
        autosaveJob?.cancel()
        lastPersistedRevision = 0L
        _uiState.value = _uiState.value.copy(
            activeTab = NotesBottomTab.NOTES,
            showAddChooser = false,
            isSelectionMode = false,
            selectedNoteIds = emptySet(),
            showBulkColorPicker = false,
            editor = NoteEditorUiState(
                note = draft,
                isEditing = true,
                isNewNoteSession = true,
                isPersistedInDb = false,
                baselineForExistingEdit = null,
                undoStack = emptyList(),
                redoStack = emptyList(),
                editRevision = 0L
            )
        )
    }

    fun openExistingNote(noteId: String) {
        viewModelScope.launch {
            val loaded = _uiState.value.allActiveNotes.firstOrNull { it.id == noteId }
                ?: repository.getNoteById(noteId)
                ?: return@launch
            autosaveJob?.cancel()
            lastPersistedRevision = 0L
            _uiState.value = _uiState.value.copy(
                showAddChooser = false,
                isSelectionMode = false,
                selectedNoteIds = emptySet(),
                showBulkColorPicker = false,
                editor = NoteEditorUiState(
                    note = loaded,
                    isEditing = false,
                    isNewNoteSession = false,
                    isPersistedInDb = true,
                    baselineForExistingEdit = null,
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    editRevision = 0L
                )
            )
        }
    }

    fun enterEditMode() {
        val editor = _uiState.value.editor ?: return
        if (editor.isEditing) return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                isEditing = true,
                baselineForExistingEdit = NoteEditorSnapshot.fromNote(editor.note)
            )
        )
    }

    fun finishEditingToViewMode() {
        val editor = _uiState.value.editor ?: return
        if (!editor.isEditing) return
        viewModelScope.launch {
            val flushed = flushPendingEditorSave()
            val latest = _uiState.value.editor ?: return@launch
            if (latest.isNewNoteSession && !latest.note.hasAnyUserContent && !latest.isPersistedInDb) {
                // Nothing entered on a brand-new note: close cleanly without saving a blank note
                _uiState.value = _uiState.value.copy(editor = null)
                return@launch
            }
            if (flushed != null) {
                _uiState.value = _uiState.value.copy(
                    editor = latest.copy(
                        note = flushed,
                        isEditing = false,
                        isNewNoteSession = false,
                        isPersistedInDb = true,
                        baselineForExistingEdit = null
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    editor = latest.copy(
                        isEditing = false,
                        baselineForExistingEdit = null
                    )
                )
            }
        }
    }

    suspend fun closeEditorSafely() {
        val editor = _uiState.value.editor ?: return
        autosaveJob?.cancel()
        val persisted = flushPendingEditorSave()
        val latest = _uiState.value.editor ?: editor
        if (latest.isNewNoteSession && !latest.note.hasAnyUserContent) {
            if (latest.isPersistedInDb || persisted != null) {
                saveMutex.withLock {
                    withContext(NonCancellable) {
                        repository.discardNewUnsavedNote(latest.note.id)
                    }
                }
            }
        }
        _uiState.value = _uiState.value.copy(editor = null)
    }

    fun onEditorBackPressed() {
        viewModelScope.launch {
            closeEditorSafely()
        }
    }

    fun discardEditorChangesOrNewNote() {
        val editor = _uiState.value.editor ?: return
        autosaveJob?.cancel()
        viewModelScope.launch {
            if (editor.isNewNoteSession) {
                saveMutex.withLock {
                    withContext(NonCancellable) {
                        repository.discardNewUnsavedNote(editor.note.id)
                    }
                }
                _uiState.value = _uiState.value.copy(editor = null)
            } else {
                val baseline = editor.baselineForExistingEdit
                if (baseline != null) {
                    val reverted = editor.note.copy(
                        title = baseline.title,
                        body = baseline.body,
                        color = baseline.color,
                        items = baseline.items
                    )
                    applySnapshotDirect(
                        note = reverted,
                        isEditing = false,
                        baseline = null,
                        clearHistory = true,
                        persistImmediately = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        editor = editor.copy(isEditing = false)
                    )
                }
            }
        }
    }

    fun deleteOpenedNote() {
        val editor = _uiState.value.editor ?: return
        autosaveJob?.cancel()
        viewModelScope.launch {
            if (editor.isNewNoteSession && !editor.isPersistedInDb) {
                _uiState.value = _uiState.value.copy(editor = null)
                return@launch
            }
            val id = editor.note.id
            val deleted = saveMutex.withLock {
                withContext(NonCancellable) {
                    repository.softDeleteNotes(listOf(id))
                }
            }
            _uiState.value = _uiState.value.copy(editor = null)
            if (deleted > 0) {
                _events.tryEmit(NotesUiEvent.ShowUndoDeleteSnackbar(listOf(id), "Note deleted"))
            }
        }
    }

    // ---- Editor mutations & Undo/Redo ----

    private fun applyNoteMutation(
        recordUndo: Boolean = true,
        persistImmediately: Boolean = false,
        transform: (NoteRecord) -> NoteRecord
    ) {
        val editor = _uiState.value.editor ?: return
        val beforeSnapshot = NoteEditorSnapshot.fromNote(editor.note)
        val now = clock()
        val mutatedRaw = transform(editor.note)
        val normalized = mutatedRaw.copy(
            modifiedAt = now.coerceAtLeast(editor.note.modifiedAt)
        )
        val afterSnapshot = NoteEditorSnapshot.fromNote(normalized)
        if (beforeSnapshot == afterSnapshot) return

        val nextUndo = if (recordUndo) {
            (editor.undoStack + beforeSnapshot).takeLast(MAX_UNDO_HISTORY)
        } else {
            editor.undoStack
        }
        val nextRedo = if (recordUndo) emptyList() else editor.redoStack
        val nextRevision = editor.editRevision + 1L

        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                note = normalized,
                undoStack = nextUndo,
                redoStack = nextRedo,
                editRevision = nextRevision
            )
        )

        if (persistImmediately) {
            autosaveJob?.cancel()
            viewModelScope.launch {
                flushPendingEditorSave()
            }
        } else {
            scheduleDebouncedAutosave()
        }
    }

    private fun applySnapshotDirect(
        note: NoteRecord,
        isEditing: Boolean,
        baseline: NoteEditorSnapshot?,
        clearHistory: Boolean,
        persistImmediately: Boolean,
        undoStack: List<NoteEditorSnapshot> = emptyList(),
        redoStack: List<NoteEditorSnapshot> = emptyList()
    ) {
        val editor = _uiState.value.editor ?: return
        val nextRevision = editor.editRevision + 1L
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                note = note.copy(modifiedAt = clock().coerceAtLeast(note.modifiedAt)),
                isEditing = isEditing,
                baselineForExistingEdit = baseline,
                undoStack = if (clearHistory) emptyList() else undoStack,
                redoStack = if (clearHistory) emptyList() else redoStack,
                editRevision = nextRevision
            )
        )
        if (persistImmediately) {
            autosaveJob?.cancel()
            viewModelScope.launch {
                flushPendingEditorSave()
            }
        } else {
            scheduleDebouncedAutosave()
        }
    }

    fun updateEditorTitle(newTitle: String) {
        applyNoteMutation { note ->
            note.copy(title = newTitle)
        }
    }

    fun updateEditorBody(newBody: String) {
        applyNoteMutation { note ->
            note.copy(body = newBody)
        }
    }

    fun openEditorColorPicker() {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(showColorPicker = true)
        )
    }

    fun dismissEditorColorPicker() {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(showColorPicker = false)
        )
    }

    fun updateEditorColor(color: NoteColor) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(showColorPicker = false)
        )
        applyNoteMutation(
            recordUndo = true,
            persistImmediately = editor.isPersistedInDb || editor.note.hasAnyUserContent
        ) { note ->
            note.copy(color = color)
        }
    }

    fun undoEditorChange() {
        val editor = _uiState.value.editor ?: return
        if (editor.undoStack.isEmpty()) return
        val target = editor.undoStack.last()
        val currentSnapshot = NoteEditorSnapshot.fromNote(editor.note)
        val restoredNote = editor.note.copy(
            title = target.title,
            body = target.body,
            color = target.color,
            items = target.items.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        )
        applySnapshotDirect(
            note = restoredNote,
            isEditing = editor.isEditing,
            baseline = editor.baselineForExistingEdit,
            clearHistory = false,
            persistImmediately = true,
            undoStack = editor.undoStack.dropLast(1),
            redoStack = (editor.redoStack + currentSnapshot).takeLast(MAX_UNDO_HISTORY)
        )
    }

    fun redoEditorChange() {
        val editor = _uiState.value.editor ?: return
        if (editor.redoStack.isEmpty()) return
        val target = editor.redoStack.last()
        val currentSnapshot = NoteEditorSnapshot.fromNote(editor.note)
        val restoredNote = editor.note.copy(
            title = target.title,
            body = target.body,
            color = target.color,
            items = target.items.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        )
        applySnapshotDirect(
            note = restoredNote,
            isEditing = editor.isEditing,
            baseline = editor.baselineForExistingEdit,
            clearHistory = false,
            persistImmediately = true,
            undoStack = (editor.undoStack + currentSnapshot).takeLast(MAX_UNDO_HISTORY),
            redoStack = editor.redoStack.dropLast(1)
        )
    }

    // ---- Checklist interactions ----

    fun toggleChecklistItemInViewOrEdit(itemId: String) {
        val editor = _uiState.value.editor ?: return
        if (editor.note.noteType != NoteType.CHECKLIST) return
        applyNoteMutation(
            recordUndo = true,
            persistImmediately = true
        ) { note ->
            val updatedItems = note.items.map { item ->
                if (item.id == itemId) {
                    item.copy(isChecked = !item.isChecked, updatedAt = clock())
                } else {
                    item
                }
            }
            note.copy(items = updatedItems)
        }
    }

    fun openAddChecklistItemDialog() {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                checklistDialogState = ChecklistItemDialogState(editingItemId = null, text = "")
            )
        )
    }

    fun openEditChecklistItemDialog(item: NoteChecklistItem) {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(
                checklistDialogState = ChecklistItemDialogState(
                    editingItemId = item.id,
                    text = item.text
                )
            )
        )
    }

    fun updateChecklistDialogText(text: String) {
        val editor = _uiState.value.editor ?: return
        val dialog = editor.checklistDialogState ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(checklistDialogState = dialog.copy(text = text))
        )
    }

    /**
     * ColorNote-style NEXT action:
     * Saves the current non-empty item, clears the dialog text, and keeps the dialog open.
     * Prevents blank duplicate items.
     */
    fun confirmChecklistDialogNext() {
        val editor = _uiState.value.editor ?: return
        val dialog = editor.checklistDialogState ?: return
        val trimmed = dialog.text.trim()
        if (trimmed.isEmpty()) return

        commitChecklistItemFromDialog(editingItemId = null, itemText = trimmed)
        val latestEditor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = latestEditor.copy(
                checklistDialogState = ChecklistItemDialogState(editingItemId = null, text = "")
            )
        )
    }

    /**
     * ColorNote-style OK action:
     * Saves the current non-empty item (or edits existing item) and closes the dialog.
     * If adding and text is blank (e.g., closing after NEXT entries), closes safely without adding blank item.
     */
    fun confirmChecklistDialogOk() {
        val editor = _uiState.value.editor ?: return
        val dialog = editor.checklistDialogState ?: return
        val trimmed = dialog.text.trim()
        if (trimmed.isNotEmpty()) {
            commitChecklistItemFromDialog(editingItemId = dialog.editingItemId, itemText = trimmed)
        }
        val latestEditor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = latestEditor.copy(checklistDialogState = null)
        )
    }

    /**
     * ColorNote-style CANCEL action:
     * Discards ONLY the unsaved text currently in the dialog and closes it.
     * Never undoes items previously saved via NEXT.
     */
    fun cancelChecklistDialog() {
        val editor = _uiState.value.editor ?: return
        _uiState.value = _uiState.value.copy(
            editor = editor.copy(checklistDialogState = null)
        )
    }

    private fun commitChecklistItemFromDialog(editingItemId: String?, itemText: String) {
        val now = clock()
        applyNoteMutation(
            recordUndo = true,
            persistImmediately = true
        ) { note ->
            val nextItems = if (editingItemId == null) {
                val nextOrder = (note.items.maxOfOrNull { it.sortOrder } ?: -1) + 1
                note.items + NoteChecklistItem(
                    id = UUID.randomUUID().toString(),
                    noteId = note.id,
                    text = itemText,
                    isChecked = false,
                    sortOrder = nextOrder,
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                note.items.map { item ->
                    if (item.id == editingItemId) {
                        item.copy(text = itemText, updatedAt = now)
                    } else {
                        item
                    }
                }
            }
            note.copy(items = nextItems)
        }
    }

    fun deleteChecklistItem(itemId: String) {
        applyNoteMutation(
            recordUndo = true,
            persistImmediately = true
        ) { note ->
            val remaining = note.items
                .filterNot { it.id == itemId }
                .mapIndexed { index, item -> item.copy(sortOrder = index) }
            note.copy(items = remaining)
        }
    }

    fun buildShareIntentForOpenedNote(): Intent? {
        val editor = _uiState.value.editor ?: return null
        return NotesShareHelper.buildShareIntent(editor.note)
    }

    // ---- Serialized autosave & flush ----

    private fun scheduleDebouncedAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(autosaveDebounceMs)
            flushPendingEditorSave()
        }
    }

    /**
     * Serializes database writes through [saveMutex] reading the latest [NoteEditorUiState] inside the lock
     * so earlier queued writes can never overwrite newer user edits.
     */
    suspend fun flushPendingEditorSave(): NoteRecord? {
        return saveMutex.withLock {
            val currentEditor = _uiState.value.editor ?: return@withLock null
            val revisionAtStart = currentEditor.editRevision
            val noteSnapshot = currentEditor.note

            val shouldPersist = currentEditor.isPersistedInDb || noteSnapshot.hasAnyUserContent
            if (!shouldPersist) return@withLock null
            if (currentEditor.isPersistedInDb && revisionAtStart <= lastPersistedRevision) {
                return@withLock noteSnapshot
            }

            val saved = withContext(NonCancellable) {
                repository.saveNote(noteSnapshot)
            }
            lastPersistedRevision = maxOf(lastPersistedRevision, revisionAtStart)

            val afterSaveEditor = _uiState.value.editor
            if (afterSaveEditor != null && afterSaveEditor.note.id == saved.id) {
                val updatedNote = if (afterSaveEditor.editRevision == revisionAtStart) {
                    saved
                } else {
                    afterSaveEditor.note
                }
                _uiState.value = _uiState.value.copy(
                    editor = afterSaveEditor.copy(
                        note = updatedNote,
                        isPersistedInDb = true
                    )
                )
            }
            saved
        }
    }

    override fun onCleared() {
        autosaveJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val MAX_UNDO_HISTORY = 100
    }
}
