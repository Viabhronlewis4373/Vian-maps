package com.example.data.notes

import com.example.data.db.NoteChecklistItemEntity
import com.example.data.db.NoteEntity
import com.example.data.db.NoteWithChecklistItems
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NoteType(val dbValue: String) {
    TEXT(NoteEntity.TYPE_TEXT),
    CHECKLIST(NoteEntity.TYPE_CHECKLIST);

    companion object {
        fun fromDb(raw: String?): NoteType =
            if (raw.equals(CHECKLIST.dbValue, ignoreCase = true)) CHECKLIST else TEXT
    }
}

enum class NoteSortOrder(val label: String) {
    MODIFIED("Modified"),
    CREATED("Created"),
    ALPHABETICAL("Alphabetical"),
    COLOR("Color")
}

data class NoteChecklistItem(
    val id: String,
    val noteId: String,
    val text: String,
    val isChecked: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toEntity(): NoteChecklistItemEntity = NoteChecklistItemEntity(
        id = id,
        noteId = noteId,
        text = text,
        isChecked = isChecked,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: NoteChecklistItemEntity): NoteChecklistItem = NoteChecklistItem(
            id = entity.id,
            noteId = entity.noteId,
            text = entity.text,
            isChecked = entity.isChecked,
            sortOrder = entity.sortOrder,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
}

data class NoteRecord(
    val id: String,
    val title: String = "",
    val body: String = "",
    val noteType: NoteType = NoteType.TEXT,
    val color: NoteColor = NoteColor.DEFAULT,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val items: List<NoteChecklistItem> = emptyList()
) {
    /**
     * Display-only fallback title. Never written back to [title].
     * - Text note: explicit title; otherwise first non-empty body line trimmed; otherwise yyyy-MM-dd creation date.
     * - Checklist: explicit title; otherwise first checklist item text trimmed; otherwise yyyy-MM-dd creation date.
     */
    val displayTitle: String
        get() {
            val trimmedTitle = title.trim()
            if (trimmedTitle.isNotEmpty()) return trimmedTitle
            return when (noteType) {
                NoteType.TEXT -> {
                    val firstBodyLine = body.lineSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() }
                    firstBodyLine ?: NoteFormatting.formatDateYyyyMmDd(createdAt)
                }
                NoteType.CHECKLIST -> {
                    val firstItemText = items
                        .sortedBy { it.sortOrder }
                        .map { it.text.trim() }
                        .firstOrNull { it.isNotEmpty() }
                    firstItemText ?: NoteFormatting.formatDateYyyyMmDd(createdAt)
                }
            }
        }

    val hasAnyUserContent: Boolean
        get() = title.isNotBlank() || when (noteType) {
            NoteType.TEXT -> body.isNotBlank()
            NoteType.CHECKLIST -> items.any { it.text.isNotBlank() }
        }

    fun toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        body = body,
        noteType = noteType.dbValue,
        colorKey = color.key,
        isArchived = isArchived,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
        createdAt = createdAt,
        modifiedAt = modifiedAt
    )

    companion object {
        fun fromRelation(relation: NoteWithChecklistItems): NoteRecord = NoteRecord(
            id = relation.note.id,
            title = relation.note.title,
            body = relation.note.body,
            noteType = NoteType.fromDb(relation.note.noteType),
            color = NoteColor.fromKey(relation.note.colorKey),
            isArchived = relation.note.isArchived,
            isDeleted = relation.note.isDeleted,
            deletedAt = relation.note.deletedAt,
            createdAt = relation.note.createdAt,
            modifiedAt = relation.note.modifiedAt,
            items = relation.sortedItems().map(NoteChecklistItem::fromEntity)
        )
    }
}

object NoteFormatting {
    fun formatDateYyyyMmDd(timestampMs: Long, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", locale)
        return formatter.format(Date(timestampMs))
    }

    fun formatDateTimeMedium(timestampMs: Long, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat("MMM d, yyyy · HH:mm", locale)
        return formatter.format(Date(timestampMs))
    }

    fun formatListRowDate(
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        locale: Locale = Locale.getDefault()
    ): String {
        val sameDayFmt = SimpleDateFormat("yyyy-MM-dd", locale)
        val isToday = sameDayFmt.format(Date(timestampMs)) == sameDayFmt.format(Date(nowMs))
        return if (isToday) {
            SimpleDateFormat("HH:mm", locale).format(Date(timestampMs))
        } else {
            SimpleDateFormat("MMM d", locale).format(Date(timestampMs))
        }
    }

    fun sortNotes(notes: List<NoteRecord>, sortOrder: NoteSortOrder): List<NoteRecord> {
        val comparator = when (sortOrder) {
            NoteSortOrder.MODIFIED -> compareByDescending<NoteRecord> { it.modifiedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
            NoteSortOrder.CREATED -> compareByDescending<NoteRecord> { it.createdAt }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.id }
            NoteSortOrder.ALPHABETICAL -> compareBy<NoteRecord> { it.displayTitle.lowercase(Locale.getDefault()) }
                .thenBy { it.displayTitle }
                .thenByDescending { it.modifiedAt }
                .thenBy { it.id }
            NoteSortOrder.COLOR -> compareBy<NoteRecord> { it.color.paletteOrder }
                .thenByDescending { it.modifiedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        }
        return notes.sortedWith(comparator)
    }

    fun filterActiveNotes(notes: List<NoteRecord>, rawQuery: String): List<NoteRecord> {
        val active = notes.filter { !it.isDeleted && !it.isArchived }
        val query = rawQuery.trim()
        if (query.isEmpty()) return active
        return active.filter { note ->
            note.title.contains(query, ignoreCase = true) ||
                note.body.contains(query, ignoreCase = true) ||
                note.items.any { it.text.contains(query, ignoreCase = true) }
        }
    }

    fun formatShareSubject(note: NoteRecord): String = note.displayTitle

    fun formatShareBody(note: NoteRecord): String {
        val trimmedTitle = note.title.trim()
        return when (note.noteType) {
            NoteType.TEXT -> {
                val trimmedBody = note.body.trimEnd()
                when {
                    trimmedTitle.isNotEmpty() && trimmedBody.isNotEmpty() -> "$trimmedTitle\n\n$trimmedBody"
                    trimmedTitle.isNotEmpty() -> trimmedTitle
                    trimmedBody.isNotEmpty() -> trimmedBody
                    else -> note.displayTitle
                }
            }
            NoteType.CHECKLIST -> {
                val checklistLines = note.items
                    .sortedBy { it.sortOrder }
                    .joinToString("\n") { item ->
                        val mark = if (item.isChecked) "[x]" else "[ ]"
                        "$mark ${item.text}"
                    }
                when {
                    trimmedTitle.isNotEmpty() && checklistLines.isNotEmpty() -> "$trimmedTitle\n\n$checklistLines"
                    trimmedTitle.isNotEmpty() -> trimmedTitle
                    checklistLines.isNotEmpty() -> checklistLines
                    else -> note.displayTitle
                }
            }
        }
    }
}
