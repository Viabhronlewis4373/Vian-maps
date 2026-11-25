package com.example.data.notes

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.NoteChecklistItemEntity
import com.example.data.db.NoteEntity
import com.example.data.db.NotesDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Local-first repository for the standalone Notes workspace.
 * Backed by normalized Room tables (`notes` and `note_checklist_items`) inside `map_app_master.db`.
 */
class NotesRepository(
    private val notesDao: NotesDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(AppDatabase.getInstance(context).notesDao())

    fun observeActiveNotes(): Flow<List<NoteRecord>> =
        notesDao.observeActiveNotes().map { relations ->
            relations.map(NoteRecord::fromRelation)
        }

    suspend fun getActiveNotesOnce(): List<NoteRecord> = withContext(Dispatchers.IO) {
        notesDao.getActiveNotesOnce().map(NoteRecord::fromRelation)
    }

    suspend fun getNoteById(noteId: String): NoteRecord? = withContext(Dispatchers.IO) {
        notesDao.getNoteWithItemsById(noteId)?.let(NoteRecord::fromRelation)
    }

    suspend fun createNote(
        type: NoteType,
        color: NoteColor = NoteColor.DEFAULT,
        title: String = "",
        body: String = ""
    ): NoteRecord = withContext(Dispatchers.IO) {
        val now = clock()
        val note = NoteRecord(
            id = UUID.randomUUID().toString(),
            title = title,
            body = body,
            noteType = type,
            color = color,
            isArchived = false,
            isDeleted = false,
            deletedAt = null,
            createdAt = now,
            modifiedAt = now,
            items = emptyList()
        )
        notesDao.upsertNote(note.toEntity())
        note
    }

    suspend fun saveNote(note: NoteRecord): NoteRecord = withContext(Dispatchers.IO) {
        val now = clock().coerceAtLeast(note.modifiedAt)
        val normalizedItems = note.items.mapIndexed { index, item ->
            item.copy(
                noteId = note.id,
                sortOrder = index,
                updatedAt = now
            )
        }
        val updatedNote = note.copy(
            modifiedAt = now,
            items = normalizedItems
        )
        notesDao.saveNoteWithChecklistItems(
            note = updatedNote.toEntity(),
            items = normalizedItems.map(NoteChecklistItem::toEntity)
        )
        updatedNote
    }

    suspend fun addChecklistItem(
        noteId: String,
        text: String
    ): NoteChecklistItem? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext null
        val parent = notesDao.getNoteById(noteId) ?: return@withContext null
        val now = clock()
        val itemEntity = NoteChecklistItemEntity(
            id = UUID.randomUUID().toString(),
            noteId = parent.id,
            text = trimmed,
            isChecked = false,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now
        )
        notesDao.addChecklistItemAtomic(parent.id, itemEntity, now)
        notesDao.getChecklistItemsForNote(parent.id)
            .firstOrNull { it.id == itemEntity.id }
            ?.let(NoteChecklistItem::fromEntity)
    }

    suspend fun toggleChecklistItem(
        noteId: String,
        itemId: String,
        isChecked: Boolean
    ): NoteRecord? = withContext(Dispatchers.IO) {
        val now = clock()
        val updated = notesDao.toggleChecklistItemAtomic(
            noteId = noteId,
            itemId = itemId,
            isChecked = isChecked,
            modifiedAt = now
        )
        if (!updated) return@withContext null
        notesDao.getNoteWithItemsById(noteId)?.let(NoteRecord::fromRelation)
    }

    suspend fun updateChecklistItemText(
        noteId: String,
        itemId: String,
        newText: String
    ): NoteRecord? = withContext(Dispatchers.IO) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return@withContext null
        val now = clock()
        val updated = notesDao.updateChecklistItemTextAtomic(
            noteId = noteId,
            itemId = itemId,
            text = trimmed,
            modifiedAt = now
        )
        if (!updated) return@withContext null
        notesDao.getNoteWithItemsById(noteId)?.let(NoteRecord::fromRelation)
    }

    suspend fun deleteChecklistItem(
        noteId: String,
        itemId: String
    ): NoteRecord? = withContext(Dispatchers.IO) {
        val now = clock()
        val deleted = notesDao.deleteChecklistItemAndReindexAtomic(
            noteId = noteId,
            itemId = itemId,
            modifiedAt = now
        )
        if (!deleted) return@withContext null
        notesDao.getNoteWithItemsById(noteId)?.let(NoteRecord::fromRelation)
    }

    suspend fun reorderChecklistItems(
        noteId: String,
        orderedItemIds: List<String>
    ): NoteRecord? = withContext(Dispatchers.IO) {
        val now = clock()
        notesDao.reorderChecklistItemsAtomic(
            noteId = noteId,
            orderedItemIds = orderedItemIds,
            modifiedAt = now
        )
        notesDao.getNoteWithItemsById(noteId)?.let(NoteRecord::fromRelation)
    }

    suspend fun updateNoteColor(
        noteId: String,
        color: NoteColor
    ): NoteRecord? = withContext(Dispatchers.IO) {
        val now = clock()
        notesDao.updateNotesColor(listOf(noteId), color.key, now)
        notesDao.getNoteWithItemsById(noteId)?.let(NoteRecord::fromRelation)
    }

    suspend fun updateNotesColor(
        noteIds: List<String>,
        color: NoteColor
    ): Int = withContext(Dispatchers.IO) {
        if (noteIds.isEmpty()) return@withContext 0
        notesDao.updateNotesColor(noteIds, color.key, clock())
    }

    suspend fun softDeleteNotes(noteIds: List<String>): Int = withContext(Dispatchers.IO) {
        if (noteIds.isEmpty()) return@withContext 0
        notesDao.softDeleteNotes(noteIds, clock())
    }

    suspend fun restoreNotes(noteIds: List<String>): Int = withContext(Dispatchers.IO) {
        if (noteIds.isEmpty()) return@withContext 0
        notesDao.restoreSoftDeletedNotes(noteIds)
    }

    suspend fun discardNewUnsavedNote(noteId: String): Boolean = withContext(Dispatchers.IO) {
        notesDao.hardDeleteNoteById(noteId) > 0
    }
}
