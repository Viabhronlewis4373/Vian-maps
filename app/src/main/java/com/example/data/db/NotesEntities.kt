package com.example.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["isDeleted", "isArchived", "modifiedAt"]),
        Index(value = ["createdAt"]),
        Index(value = ["colorKey"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val body: String = "",
    val noteType: String = TYPE_TEXT,
    val colorKey: String = DEFAULT_COLOR_KEY,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_CHECKLIST = "CHECKLIST"
        const val DEFAULT_COLOR_KEY = "yellow"
    }
}

@Entity(
    tableName = "note_checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["noteId", "sortOrder"])
    ]
)
data class NoteChecklistItemEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String,
    val isChecked: Boolean = false,
    val sortOrder: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class NoteWithChecklistItems(
    @Embedded val note: NoteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId"
    )
    val items: List<NoteChecklistItemEntity>
) {
    fun sortedItems(): List<NoteChecklistItemEntity> =
        items.sortedWith(
            compareBy<NoteChecklistItemEntity> { it.sortOrder }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
}

@Dao
interface NotesDao {
    @Transaction
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY modifiedAt DESC, createdAt DESC, id ASC")
    fun observeActiveNotes(): Flow<List<NoteWithChecklistItems>>

    @Transaction
    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY modifiedAt DESC, createdAt DESC, id ASC")
    suspend fun getActiveNotesOnce(): List<NoteWithChecklistItems>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteWithItemsById(noteId: String): NoteWithChecklistItems?

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    fun observeNoteWithItemsById(noteId: String): Flow<NoteWithChecklistItems?>

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Query("SELECT * FROM note_checklist_items WHERE noteId = :noteId ORDER BY sortOrder ASC, createdAt ASC, id ASC")
    suspend fun getChecklistItemsForNote(noteId: String): List<NoteChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItem(item: NoteChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItems(items: List<NoteChecklistItemEntity>)

    @Query("UPDATE note_checklist_items SET isChecked = :isChecked, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun updateChecklistItemChecked(itemId: String, isChecked: Boolean, updatedAt: Long): Int

    @Query("UPDATE note_checklist_items SET text = :text, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun updateChecklistItemText(itemId: String, text: String, updatedAt: Long): Int

    @Query("UPDATE note_checklist_items SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :itemId")
    suspend fun updateChecklistItemSortOrder(itemId: String, sortOrder: Int, updatedAt: Long): Int

    @Query("DELETE FROM note_checklist_items WHERE id = :itemId")
    suspend fun deleteChecklistItemById(itemId: String): Int

    @Query("DELETE FROM note_checklist_items WHERE noteId = :noteId")
    suspend fun deleteChecklistItemsForNote(noteId: String): Int

    @Query("UPDATE notes SET modifiedAt = :modifiedAt WHERE id = :noteId")
    suspend fun touchNoteModifiedAt(noteId: String, modifiedAt: Long): Int

    @Query("UPDATE notes SET title = :title, body = :body, colorKey = :colorKey, modifiedAt = :modifiedAt WHERE id = :noteId")
    suspend fun updateNoteFields(
        noteId: String,
        title: String,
        body: String,
        colorKey: String,
        modifiedAt: Long
    ): Int

    @Query("UPDATE notes SET colorKey = :colorKey, modifiedAt = :modifiedAt WHERE id IN (:noteIds) AND isDeleted = 0")
    suspend fun updateNotesColor(noteIds: List<String>, colorKey: String, modifiedAt: Long): Int

    @Query("UPDATE notes SET isDeleted = 1, deletedAt = :deletedAt WHERE id IN (:noteIds)")
    suspend fun softDeleteNotes(noteIds: List<String>, deletedAt: Long): Int

    @Query("UPDATE notes SET isDeleted = 0, deletedAt = NULL WHERE id IN (:noteIds)")
    suspend fun restoreSoftDeletedNotes(noteIds: List<String>): Int

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun hardDeleteNoteById(noteId: String): Int

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND isArchived = 0")
    suspend fun getActiveNoteCount(): Int

    @Transaction
    suspend fun saveNoteWithChecklistItems(
        note: NoteEntity,
        items: List<NoteChecklistItemEntity>
    ) {
        upsertNote(note)
        deleteChecklistItemsForNote(note.id)
        if (items.isNotEmpty()) {
            val normalized = items.mapIndexed { index, item ->
                item.copy(
                    noteId = note.id,
                    sortOrder = index
                )
            }
            upsertChecklistItems(normalized)
        }
    }

    @Transaction
    suspend fun addChecklistItemAtomic(
        noteId: String,
        item: NoteChecklistItemEntity,
        modifiedAt: Long
    ) {
        val existing = getChecklistItemsForNote(noteId)
        val nextSortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        upsertChecklistItem(
            item.copy(
                noteId = noteId,
                sortOrder = nextSortOrder,
                updatedAt = modifiedAt
            )
        )
        touchNoteModifiedAt(noteId, modifiedAt)
    }

    @Transaction
    suspend fun toggleChecklistItemAtomic(
        noteId: String,
        itemId: String,
        isChecked: Boolean,
        modifiedAt: Long
    ): Boolean {
        val updated = updateChecklistItemChecked(itemId, isChecked, modifiedAt)
        if (updated > 0) {
            touchNoteModifiedAt(noteId, modifiedAt)
            return true
        }
        return false
    }

    @Transaction
    suspend fun updateChecklistItemTextAtomic(
        noteId: String,
        itemId: String,
        text: String,
        modifiedAt: Long
    ): Boolean {
        val updated = updateChecklistItemText(itemId, text, modifiedAt)
        if (updated > 0) {
            touchNoteModifiedAt(noteId, modifiedAt)
            return true
        }
        return false
    }

    @Transaction
    suspend fun deleteChecklistItemAndReindexAtomic(
        noteId: String,
        itemId: String,
        modifiedAt: Long
    ): Boolean {
        val deleted = deleteChecklistItemById(itemId)
        if (deleted <= 0) return false
        val remaining = getChecklistItemsForNote(noteId)
        remaining.forEachIndexed { index, item ->
            if (item.sortOrder != index) {
                updateChecklistItemSortOrder(item.id, index, modifiedAt)
            }
        }
        touchNoteModifiedAt(noteId, modifiedAt)
        return true
    }

    @Transaction
    suspend fun reorderChecklistItemsAtomic(
        noteId: String,
        orderedItemIds: List<String>,
        modifiedAt: Long
    ) {
        val existingById = getChecklistItemsForNote(noteId).associateBy { it.id }
        var order = 0
        val seen = HashSet<String>()
        for (id in orderedItemIds) {
            if (existingById.containsKey(id) && seen.add(id)) {
                updateChecklistItemSortOrder(id, order++, modifiedAt)
            }
        }
        for (item in existingById.values.sortedWith(compareBy<NoteChecklistItemEntity> { it.sortOrder }.thenBy { it.createdAt })) {
            if (seen.add(item.id)) {
                updateChecklistItemSortOrder(item.id, order++, modifiedAt)
            }
        }
        touchNoteModifiedAt(noteId, modifiedAt)
    }
}
