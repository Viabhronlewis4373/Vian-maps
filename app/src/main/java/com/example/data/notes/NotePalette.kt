package com.example.data.notes

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Unified nine-color palette aligned with the Legacy ColorNote reference.
 * All UI surfaces (list rows, saturated edge strips, editor headers, editor soft paper,
 * 3x3 swatch pickers, sorting, and bulk color updates) resolve through this single table.
 */
enum class NoteColor(
    val key: String,
    val displayName: String,
    val paletteOrder: Int,
    val listBackground: Color,
    val saturatedStrip: Color,
    val editorHeaderBackground: Color,
    val editorHeaderForeground: Color,
    val editorPaperBackground: Color,
    val editorPaperText: Color,
    val editorPaperMutedText: Color,
    val editorRuleLineColor: Color,
    val editorHeaderDividerColor: Color
) {
    WHITE(
        key = "white",
        displayName = "White",
        paletteOrder = 0,
        listBackground = Color(0xFFF5F5F0),
        saturatedStrip = Color(0xFFAAAAAA),
        editorHeaderBackground = Color(0xFFEAEAE3),
        editorHeaderForeground = Color(0xFF1A1A1A),
        editorPaperBackground = Color(0xFFFCFCF8),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF525252),
        editorRuleLineColor = Color(0x1C0F172A),
        editorHeaderDividerColor = Color(0x330F172A)
    ),
    RED(
        key = "red",
        displayName = "Red",
        paletteOrder = 1,
        listBackground = Color(0xFFFCE8E8),
        saturatedStrip = Color(0xFFE53935),
        editorHeaderBackground = Color(0xFFE53935),
        editorHeaderForeground = Color(0xFFFFFFFF),
        editorPaperBackground = Color(0xFFFFF5F5),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF5C3434),
        editorRuleLineColor = Color(0x1EE53935),
        editorHeaderDividerColor = Color(0x26FFFFFF)
    ),
    ORANGE(
        key = "orange",
        displayName = "Orange",
        paletteOrder = 2,
        listBackground = Color(0xFFFEF3E2),
        saturatedStrip = Color(0xFFEF6C00),
        editorHeaderBackground = Color(0xFFEF6C00),
        editorHeaderForeground = Color(0xFF1A1A1A),
        editorPaperBackground = Color(0xFFFFF8EE),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF5C3E1E),
        editorRuleLineColor = Color(0x20EF6C00),
        editorHeaderDividerColor = Color(0x28000000)
    ),
    YELLOW(
        key = "yellow",
        displayName = "Yellow",
        paletteOrder = 3,
        listBackground = Color(0xFFFFFDE7),
        saturatedStrip = Color(0xFFF9A825),
        editorHeaderBackground = Color(0xFFF9A825),
        editorHeaderForeground = Color(0xFF1A1A1A),
        editorPaperBackground = Color(0xFFFFFEF3),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF5B4E1D),
        editorRuleLineColor = Color(0x24C49000),
        editorHeaderDividerColor = Color(0x24000000)
    ),
    GREEN(
        key = "green",
        displayName = "Green",
        paletteOrder = 4,
        listBackground = Color(0xFFE8F5E9),
        saturatedStrip = Color(0xFF2E7D32),
        editorHeaderBackground = Color(0xFF2E7D32),
        editorHeaderForeground = Color(0xFFFFFFFF),
        editorPaperBackground = Color(0xFFF3FBF4),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF2D4F31),
        editorRuleLineColor = Color(0x1E2E7D32),
        editorHeaderDividerColor = Color(0x26FFFFFF)
    ),
    BLUE(
        key = "blue",
        displayName = "Blue",
        paletteOrder = 5,
        listBackground = Color(0xFFE3F2FD),
        saturatedStrip = Color(0xFF1565C0),
        editorHeaderBackground = Color(0xFF1565C0),
        editorHeaderForeground = Color(0xFFFFFFFF),
        editorPaperBackground = Color(0xFFF2F8FF),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF2A4566),
        editorRuleLineColor = Color(0x1E1565C0),
        editorHeaderDividerColor = Color(0x26FFFFFF)
    ),
    PURPLE(
        key = "purple",
        displayName = "Purple",
        paletteOrder = 6,
        listBackground = Color(0xFFF3E5F5),
        saturatedStrip = Color(0xFF6A1B9A),
        editorHeaderBackground = Color(0xFF6A1B9A),
        editorHeaderForeground = Color(0xFFFFFFFF),
        editorPaperBackground = Color(0xFFFAF2FC),
        editorPaperText = Color(0xFF1A1A1A),
        editorPaperMutedText = Color(0xFF4E2D5E),
        editorRuleLineColor = Color(0x1E6A1B9A),
        editorHeaderDividerColor = Color(0x26FFFFFF)
    ),
    BLACK(
        key = "black",
        displayName = "Black",
        paletteOrder = 7,
        listBackground = Color(0xFFECEFF1),
        saturatedStrip = Color(0xFF37474F),
        editorHeaderBackground = Color(0xFF37474F),
        editorHeaderForeground = Color(0xFFFFFFFF),
        editorPaperBackground = Color(0xFFECEFF1),
        editorPaperText = Color(0xFF111827),
        editorPaperMutedText = Color(0xFF455A64),
        editorRuleLineColor = Color(0x2237474F),
        editorHeaderDividerColor = Color(0x2EFFFFFF)
    ),
    GRAY(
        key = "gray",
        displayName = "Gray",
        paletteOrder = 8,
        listBackground = Color(0xFFF5F5F5),
        saturatedStrip = Color(0xFF78909C),
        editorHeaderBackground = Color(0xFF78909C),
        editorHeaderForeground = Color(0xFF111827),
        editorPaperBackground = Color(0xFFF6F7F8),
        editorPaperText = Color(0xFF111827),
        editorPaperMutedText = Color(0xFF4B5563),
        editorRuleLineColor = Color(0x1F455A64),
        editorHeaderDividerColor = Color(0x28000000)
    );

    companion object {
        val DEFAULT: NoteColor = YELLOW
        val orderedColors: List<NoteColor> = entries.sortedBy { it.paletteOrder }

        fun fromKey(rawKey: String?): NoteColor {
            val normalized = rawKey?.trim()?.lowercase(Locale.ROOT) ?: return DEFAULT
            return when (normalized) {
                "white" -> WHITE
                "red" -> RED
                "orange" -> ORANGE
                "yellow" -> YELLOW
                "green" -> GREEN
                "blue" -> BLUE
                "purple" -> PURPLE
                "black" -> BLACK
                "gray", "grey" -> GRAY
                else -> DEFAULT
            }
        }
    }
}
