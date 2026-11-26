package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.AppDatabaseContract
import com.example.data.db.NoteChecklistItemEntity
import com.example.data.db.NoteEntity
import com.example.data.notes.NoteColor
import com.example.data.notes.NoteFormatting
import com.example.data.notes.NoteSortOrder
import com.example.data.notes.NoteType
import com.example.data.notes.NotesRepository
import com.example.host.AppHostDestination
import com.example.host.AppHostShell
import com.example.map.activation.MapActivationCoordinator
import com.example.map.activation.MapActivationState
import com.example.map.activation.MapRuntimeDependencies
import com.example.map.activation.MapRuntimeInitializer
import com.example.ui.notes.NotesBottomTab
import com.example.ui.notes.NotesFiveSlotBottomBar
import com.example.ui.notes.NotesHostScreen
import com.example.ui.notes.NotesShareHelper
import com.example.ui.notes.NotesViewModel
import com.example.ui.notes.computeConcaveCradleOffsetY
import com.example.ui.notes.computeRuledLinePositions
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
class NotesStage1Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: NotesRepository
    private var fakeNowMs: Long = 1_700_000_000_000L

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeNowMs = 1_700_000_000_000L
        repository = NotesRepository(db.notesDao()) {
            fakeNowMs += 100L
            fakeNowMs
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createViewModel(debounceMs: Long = 50L): NotesViewModel = NotesViewModel(
        application = app,
        repository = repository,
        clock = {
            fakeNowMs += 100L
            fakeNowMs
        },
        autosaveDebounceMs = debounceMs
    )

    private fun awaitCondition(timeoutMs: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        val mainLooperShadow = Shadows.shadowOf(Looper.getMainLooper())
        while (System.currentTimeMillis() < deadline) {
            mainLooperShadow.idle()
            if (condition()) return
            mainLooperShadow.idleFor(25L, TimeUnit.MILLISECONDS)
            Thread.sleep(15L)
        }
        mainLooperShadow.idle()
        assertTrue("Condition was not satisfied within ${timeoutMs}ms", condition())
    }

    @Test
    fun notes_01_migrationV3ToV4PreservesAllTenV3TablesAndValidatesNotesSchema() : Unit = runBlocking {
        assertEquals(4, AppDatabaseContract.CURRENT_VERSION)
        val dbName = "test_migration_v3_v4_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()

        val sqliteV3 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqliteV3.version = 3
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `retained_tiles` (`providerId` TEXT NOT NULL, `category` TEXT NOT NULL, `z` INTEGER NOT NULL, `x` INTEGER NOT NULL, `y` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `byteSize` INTEGER NOT NULL, `checksum` TEXT NOT NULL, `fetchTimestamp` INTEGER NOT NULL, `etag` TEXT, `sourceUrl` TEXT NOT NULL, `attribution` TEXT NOT NULL, `retentionPermission` TEXT NOT NULL, `integrityStatus` TEXT NOT NULL, PRIMARY KEY(`providerId`, `category`, `z`, `x`, `y`))")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `osm_pois` (`osmId` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `subCategory` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `sourceTileZ` INTEGER NOT NULL, `sourceTileX` INTEGER NOT NULL, `sourceTileY` INTEGER NOT NULL, `tagsJson` TEXT NOT NULL, `layerName` TEXT NOT NULL, `fetchTimestamp` INTEGER NOT NULL, PRIMARY KEY(`osmId`))")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `osm_buildings` (`osmId` INTEGER NOT NULL, `name` TEXT, `buildingType` TEXT NOT NULL, `heightMeters` REAL NOT NULL, `minHeightMeters` REAL NOT NULL, `sourceTileZ` INTEGER NOT NULL, `sourceTileX` INTEGER NOT NULL, `sourceTileY` INTEGER NOT NULL, `centroidLat` REAL NOT NULL, `centroidLng` REAL NOT NULL, `geometryJson` TEXT NOT NULL, PRIMARY KEY(`osmId`))")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `osm_regions` (`osmId` INTEGER NOT NULL, `name` TEXT NOT NULL, `adminLevel` INTEGER NOT NULL, `boundaryType` TEXT NOT NULL, `sourceTileZ` INTEGER NOT NULL, `sourceTileX` INTEGER NOT NULL, `sourceTileY` INTEGER NOT NULL, PRIMARY KEY(`osmId`))")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `osm_roads` (`osmId` INTEGER NOT NULL, `name` TEXT, `highwayClass` TEXT NOT NULL, `isOneway` INTEGER NOT NULL, `sourceTileZ` INTEGER NOT NULL, `sourceTileX` INTEGER NOT NULL, `sourceTileY` INTEGER NOT NULL, `geometryJson` TEXT NOT NULL, PRIMARY KEY(`osmId`))")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `routing_graph_tiles` (`level` INTEGER NOT NULL, `tileIndex` INTEGER NOT NULL, `status` TEXT NOT NULL, `filePath` TEXT, `byteSize` INTEGER NOT NULL, `lastChecked` INTEGER NOT NULL, PRIMARY KEY(`level`, `tileIndex`))")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `user_places` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `description` TEXT NOT NULL, `addressLabel` TEXT, `category` TEXT NOT NULL, `tagsCsv` TEXT NOT NULL, `isPlacemark` INTEGER NOT NULL, `linkedModuleRef` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_user_places_isPlacemark` ON `user_places` (`isPlacemark`)")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_user_places_updatedAt` ON `user_places` (`updatedAt`)")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `saved_routes` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `geometryJson` TEXT NOT NULL, `pointCount` INTEGER NOT NULL, `totalDistanceMeters` REAL NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_routes_updatedAt` ON `saved_routes` (`updatedAt`)")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `route_waypoints` (`id` TEXT NOT NULL, `routeId` TEXT NOT NULL, `placeId` TEXT, `orderIndex` INTEGER NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`routeId`) REFERENCES `saved_routes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`placeId`) REFERENCES `user_places`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_route_waypoints_routeId` ON `route_waypoints` (`routeId`)")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_route_waypoints_placeId` ON `route_waypoints` (`placeId`)")
        sqliteV3.execSQL("CREATE TABLE IF NOT EXISTS `saved_areas` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `addressLabel` TEXT NOT NULL, `locality` TEXT NOT NULL, `region` TEXT NOT NULL, `country` TEXT NOT NULL, `featureName` TEXT NOT NULL, `category` TEXT NOT NULL, `geographicDetails` TEXT NOT NULL, `nearbySummaryJson` TEXT NOT NULL, `retainedNearbyCount` INTEGER NOT NULL, `wikiTitle` TEXT, `wikiSummary` TEXT, `wikiUrl` TEXT, `wikidataId` TEXT, `sourceAttribution` TEXT NOT NULL, `dataProvenance` TEXT NOT NULL, `userNotes` TEXT NOT NULL, `tagsCsv` TEXT NOT NULL, `retrievedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_areas_updatedAt` ON `saved_areas` (`updatedAt`)")
        sqliteV3.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_areas_latitude_longitude` ON `saved_areas` (`latitude`, `longitude`)")

        // Insert representative data into ALL 10 v3 tables
        sqliteV3.execSQL("INSERT INTO `retained_tiles` VALUES ('openfreemap', 'OSM_VECTOR', 14, 1, 2, '/tmp/tile.pbf', 256, 'abc', 100, NULL, 'https://example.org', 'OSM', 'PERMITTED', 'VALID')")
        sqliteV3.execSQL("INSERT INTO `osm_pois` VALUES (101, 'Town Bakery', 'shop', 'bakery', 39.9, 32.8, 14, 1, 2, '{}', 'poi', 100)")
        sqliteV3.execSQL("INSERT INTO `osm_buildings` VALUES (201, 'Clock Tower', 'civic', 18.0, 0.0, 14, 1, 2, 39.9, 32.8, '[]')")
        sqliteV3.execSQL("INSERT INTO `osm_regions` VALUES (301, 'Central District', 8, 'administrative', 14, 1, 2)")
        sqliteV3.execSQL("INSERT INTO `osm_roads` VALUES (401, 'Market Street', 'residential', 0, 14, 1, 2, '[]')")
        sqliteV3.execSQL("INSERT INTO `routing_graph_tiles` VALUES (2, 888, 'NOT_CONFIGURED', NULL, 0, 100)")
        sqliteV3.execSQL("INSERT INTO `user_places` VALUES ('p1', 'Camp', 39.9, 32.8, 'Base camp', 'Hill', 'placemark', 'v3', 1, NULL, 100, 100)")
        sqliteV3.execSQL("INSERT INTO `saved_routes` VALUES ('r1', 'Ridge Walk', 'Loop', '[[39.9,32.8],[39.91,32.81]]', 2, 1200.0, 100, 100)")
        sqliteV3.execSQL("INSERT INTO `route_waypoints` VALUES ('wp1', 'r1', 'p1', 0, 'Start', 39.9, 32.8, 'Gate', 100)")
        sqliteV3.execSQL("INSERT INTO `saved_areas` VALUES ('a1', 'Harbor', 39.9, 32.8, 'Pier 1', 'Town', 'Region', 'Country', 'Harbor', 'bay', 'Coast', '[]', 0, NULL, NULL, NULL, NULL, 'Local OSM', 'LOCAL_ONLY', 'Saved area note', 'coast', 100, 100, 100)")
        sqliteV3.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        // Verify Room schema validation passed and ALL 10 v3 tables preserved data
        assertEquals(1, migrated.retainedTileDao().getTileCount())
        assertEquals("Town Bakery", migrated.osmPoiDao().getPoiById(101)?.name)
        assertEquals(1, migrated.osmBuildingDao().getBuildingCount())
        assertEquals(1, migrated.osmRegionDao().getRegionCount())
        assertEquals(1, migrated.osmRoadDao().getRoadCount())
        assertNotNull(migrated.routingGraphTileDao().getGraphTile(2, 888))
        assertEquals("Camp", migrated.userPlaceDao().getPlaceById("p1")?.name)
        assertEquals(1, migrated.savedRouteDao().getRouteCount())
        assertEquals("Start", migrated.savedRouteDao().getRouteWithWaypoints("r1")?.waypoints?.firstOrNull()?.name)
        assertEquals("Harbor", migrated.savedAreaDao().getSavedAreaById("a1")?.name)

        // Verify indices and FK cascade on migrated v4 tables
        val writable = migrated.openHelper.writableDatabase
        val noteIndices = mutableSetOf<String>()
        writable.query("PRAGMA index_list(`notes`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                noteIndices.add(cursor.getString(nameIdx))
            }
        }
        assertTrue(noteIndices.contains("index_notes_isDeleted_isArchived_modifiedAt"))
        assertTrue(noteIndices.contains("index_notes_createdAt"))
        assertTrue(noteIndices.contains("index_notes_colorKey"))

        val itemIndices = mutableSetOf<String>()
        writable.query("PRAGMA index_list(`note_checklist_items`)").use { cursor ->
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                itemIndices.add(cursor.getString(nameIdx))
            }
        }
        assertTrue(itemIndices.contains("index_note_checklist_items_noteId"))
        assertTrue(itemIndices.contains("index_note_checklist_items_noteId_sortOrder"))

        // Verify FK cascade deletes checklist items when parent note is hard deleted
        migrated.notesDao().upsertNote(NoteEntity(id = "n_fk", title = "FK Test", noteType = NoteEntity.TYPE_CHECKLIST))
        migrated.notesDao().upsertChecklistItem(
            NoteChecklistItemEntity(id = "i_fk_1", noteId = "n_fk", text = "Item 1", sortOrder = 0)
        )
        assertEquals(1, migrated.notesDao().getChecklistItemsForNote("n_fk").size)
        migrated.notesDao().hardDeleteNoteById("n_fk")
        assertEquals(0, migrated.notesDao().getChecklistItemsForNote("n_fk").size)

        migrated.close()
        dbFile.delete()
    }

    @Test
    fun notes_02_repositoryNotesAndChecklistCrudOrderingCheckedPersistence() : Unit = runBlocking {
        val created = repository.createNote(type = NoteType.CHECKLIST, color = NoteColor.BLUE, title = "Groceries")
        val initialMod = created.modifiedAt

        val item1 = repository.addChecklistItem(created.id, "Apples")!!
        val item2 = repository.addChecklistItem(created.id, "Bananas")!!
        val item3 = repository.addChecklistItem(created.id, "Carrots")!!
        assertEquals(0, item1.sortOrder)
        assertEquals(1, item2.sortOrder)
        assertEquals(2, item3.sortOrder)

        val toggled = repository.toggleChecklistItem(created.id, item2.id, true)!!
        assertTrue(toggled.modifiedAt > initialMod)
        assertTrue(toggled.items.first { it.id == item2.id }.isChecked)

        val edited = repository.updateChecklistItemText(created.id, item1.id, "Green Apples")!!
        assertEquals("Green Apples", edited.items.first { it.id == item1.id }.text)

        val reordered = repository.reorderChecklistItems(created.id, listOf(item3.id, item1.id, item2.id))!!
        assertEquals(listOf("Carrots", "Green Apples", "Bananas"), reordered.items.map { it.text })

        val afterDelete = repository.deleteChecklistItem(created.id, item1.id)!!
        assertEquals(listOf("Carrots", "Bananas"), afterDelete.items.map { it.text })
        assertEquals(listOf(0, 1), afterDelete.items.map { it.sortOrder })
    }

    @Test
    fun notes_03_softDeleteUndoRestoreAndArchivedDeletedExclusion() : Unit = runBlocking {
        val active = repository.createNote(type = NoteType.TEXT, title = "Active", body = "Visible")
        val toDelete = repository.createNote(type = NoteType.TEXT, title = "To Delete", body = "Temporary")
        db.notesDao().upsertNote(
            NoteEntity(
                id = "archived_1",
                title = "Archived Note",
                body = "Hidden",
                isArchived = true,
                isDeleted = false
            )
        )

        repository.softDeleteNotes(listOf(toDelete.id))
        val rawDeleted = db.notesDao().getNoteById(toDelete.id)!!
        assertTrue(rawDeleted.isDeleted)
        assertNotNull(rawDeleted.deletedAt)

        val activeList = repository.observeActiveNotes().first()
        assertEquals(listOf(active.id), activeList.map { it.id })

        // Undo restore brings deleted note cleanly back into active list
        repository.restoreNotes(listOf(toDelete.id))
        val restoredRaw = db.notesDao().getNoteById(toDelete.id)!!
        assertFalse(restoredRaw.isDeleted)
        assertNull(restoredRaw.deletedAt)
        val afterRestore = repository.observeActiveNotes().first().map { it.id }.toSet()
        assertTrue(afterRestore.contains(active.id))
        assertTrue(afterRestore.contains(toDelete.id))
        assertFalse(afterRestore.contains("archived_1"))
    }

    @Test
    fun notes_04_emptyStateAndNewTextAndChecklistOpenInEditMode() {
        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("notes_empty_state").assertIsDisplayed()

        // Tap + -> Text Note opens directly in Edit mode
        composeTestRule.onNodeWithTag("notes_bottom_nav_add").performClick()
        composeTestRule.onNodeWithTag("notes_add_chooser_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notes_add_text_note").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("note_editor_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_editor_mode_badge").assertTextContains("Editing")
        composeTestRule.onNodeWithTag("note_editor_title_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_editor_body_input").assertIsDisplayed()

        // Back out of untouched new note -> leaves zero blank rows
        composeTestRule.onNodeWithTag("note_editor_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notes_empty_state").assertIsDisplayed()

        // Tap + -> Checklist opens directly in Edit mode
        composeTestRule.onNodeWithTag("notes_bottom_nav_add").performClick()
        composeTestRule.onNodeWithTag("notes_add_checklist").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("note_editor_mode_badge").assertTextContains("Editing")
        composeTestRule.onNodeWithTag("checklist_add_item_button").assertIsDisplayed()
    }

    @Test
    fun notes_05_existingNoteOpensInViewModeAndTogglesChecklistWithoutSwitchingToEdit() {
        val (textNote, checklistNote, tentItem) = runBlocking {
            val t = repository.createNote(type = NoteType.TEXT, title = "Field Log", body = "Observed ridge trail.")
            val c = repository.createNote(type = NoteType.CHECKLIST, title = "Packing List")
            val item = repository.addChecklistItem(c.id, "Tent")!!
            Triple(t, c, item)
        }

        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("note_row_${textNote.id}").fetchSemanticsNodes().isNotEmpty()
        }

        // Existing text note opens in View mode
        composeTestRule.onNodeWithTag("note_row_${textNote.id}").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("note_viewer_body").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("note_editor_mode_badge").assertTextContains("Viewing")
        composeTestRule.onNodeWithTag("note_viewer_body").assertTextContains("Observed ridge trail.")
        composeTestRule.onNodeWithTag("note_editor_edit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("note_editor_mode_badge").assertTextContains("Editing")
        composeTestRule.onNodeWithTag("note_editor_back_button").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("note_row_${checklistNote.id}").fetchSemanticsNodes().isNotEmpty()
        }

        // Existing checklist opens in View mode; toggling checkbox persists and stays in Viewing mode
        composeTestRule.onNodeWithTag("note_row_${checklistNote.id}").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("checklist_checkbox_${tentItem.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("note_editor_mode_badge").assertTextContains("Viewing")
        composeTestRule.onNodeWithTag("checklist_checkbox_${tentItem.id}").performClick()
        composeTestRule.waitUntil(3_000L) {
            runBlocking { repository.getNoteById(checklistNote.id)?.items?.firstOrNull { it.id == tentItem.id }?.isChecked == true }
        }

        composeTestRule.onNodeWithTag("note_editor_mode_badge").assertTextContains("Viewing")
        val updated = runBlocking { repository.getNoteById(checklistNote.id)!! }
        assertTrue(updated.items.first { it.id == tentItem.id }.isChecked)
    }

    @Test
    fun notes_06_untitledFallbackTitlesDisplayOnlyAndNeverOverwriteEditableTitle() : Unit = runBlocking {
        val bodyFallback = repository.createNote(type = NoteType.TEXT, title = "", body = "  \n   First non-empty line   \nSecond")
        val emptyTextFallback = repository.createNote(type = NoteType.TEXT, title = "", body = "   ")
        val checklistFallback = repository.createNote(type = NoteType.CHECKLIST, title = "")
        repository.addChecklistItem(checklistFallback.id, "Buy Coffee")

        val expectedDate = NoteFormatting.formatDateYyyyMmDd(emptyTextFallback.createdAt)
        val reloadedBody = repository.getNoteById(bodyFallback.id)!!
        val reloadedEmpty = repository.getNoteById(emptyTextFallback.id)!!
        val reloadedChecklist = repository.getNoteById(checklistFallback.id)!!

        assertEquals("", reloadedBody.title)
        assertEquals("First non-empty line", reloadedBody.displayTitle)
        assertEquals("", reloadedEmpty.title)
        assertEquals(expectedDate, reloadedEmpty.displayTitle)
        assertEquals("", reloadedChecklist.title)
        assertEquals("Buy Coffee", reloadedChecklist.displayTitle)
    }

    @Test
    fun notes_07_allNineColorsRenderAndPersistWithoutBlackOrGrayFallingBackToWhite() : Unit = runBlocking {
        // Default new note color is Yellow
        val defaultNote = repository.createNote(type = NoteType.TEXT, title = "Default")
        assertEquals(NoteColor.YELLOW, defaultNote.color)

        // Verify all 9 keys round-trip cleanly and Black/Gray never fall back to White
        assertEquals(9, NoteColor.orderedColors.size)
        NoteColor.orderedColors.forEach { color ->
            assertEquals(color, NoteColor.fromKey(color.key))
            val saved = repository.createNote(type = NoteType.TEXT, color = color, title = "Color ${color.displayName}")
            val loaded = repository.getNoteById(saved.id)!!
            assertEquals(color, loaded.color)
        }
        assertFalse(NoteColor.BLACK == NoteColor.WHITE)
        assertFalse(NoteColor.GRAY == NoteColor.WHITE)
        assertFalse(NoteColor.BLACK.saturatedStrip == NoteColor.WHITE.saturatedStrip)
        assertFalse(NoteColor.GRAY.saturatedStrip == NoteColor.WHITE.saturatedStrip)
        assertFalse(NoteColor.BLACK.editorHeaderBackground == NoteColor.WHITE.editorHeaderBackground)
        assertFalse(NoteColor.GRAY.editorHeaderBackground == NoteColor.WHITE.editorHeaderBackground)

        // Contrast sanity on Black, Gray, White, Yellow, Orange headers
        listOf(NoteColor.BLACK, NoteColor.GRAY, NoteColor.WHITE, NoteColor.YELLOW, NoteColor.ORANGE).forEach { c ->
            assertTrue(c.editorHeaderForeground != c.editorHeaderBackground)
            assertTrue(c.editorPaperText != c.editorPaperBackground)
        }
    }

    @Test
    fun notes_08_fourSortingModesDeterministicAndCaseInsensitive() : Unit = runBlocking {
        fakeNowMs = 1_700_000_000_000L
        // Create notes in specific order
        val nGrayAlpha = repository.createNote(type = NoteType.TEXT, color = NoteColor.GRAY, title = "beta")
        val nBlackAlpha = repository.createNote(type = NoteType.TEXT, color = NoteColor.BLACK, title = "Alpha")
        val nWhiteFallback = repository.createNote(type = NoteType.TEXT, color = NoteColor.WHITE, title = "", body = "charlie line")

        // Modify nWhiteFallback last? No, let's set explicit modified order:
        repository.updateNoteColor(nGrayAlpha.id, NoteColor.GRAY) // updates modifiedAt latest among these 3

        val notes = repository.getActiveNotesOnce()

        val byModified = NoteFormatting.sortNotes(notes, NoteSortOrder.MODIFIED).map { it.id }
        assertEquals(listOf(nGrayAlpha.id, nWhiteFallback.id, nBlackAlpha.id), byModified)

        val byCreated = NoteFormatting.sortNotes(notes, NoteSortOrder.CREATED).map { it.id }
        assertEquals(listOf(nWhiteFallback.id, nBlackAlpha.id, nGrayAlpha.id), byCreated)

        val byAlpha = NoteFormatting.sortNotes(notes, NoteSortOrder.ALPHABETICAL).map { it.displayTitle }
        assertEquals(listOf("Alpha", "beta", "charlie line"), byAlpha)

        val byColor = NoteFormatting.sortNotes(notes, NoteSortOrder.COLOR).map { it.color }
        assertEquals(listOf(NoteColor.WHITE, NoteColor.BLACK, NoteColor.GRAY), byColor)
    }

    @Test
    fun notes_09_searchAcrossTitleBodyAndChecklistExcludesDeletedAndArchived() : Unit = runBlocking {
        val titleMatch = repository.createNote(type = NoteType.TEXT, title = "Passport Renewal", body = "Embassy")
        val bodyMatch = repository.createNote(type = NoteType.TEXT, title = "Trip Prep", body = "Pack passport adapter")
        val checklistMatch = repository.createNote(type = NoteType.CHECKLIST, title = "Bag")
        repository.addChecklistItem(checklistMatch.id, "Verify passport validity")
        val deletedMatch = repository.createNote(type = NoteType.TEXT, title = "Old Passport")
        repository.softDeleteNotes(listOf(deletedMatch.id))
        db.notesDao().upsertNote(
            NoteEntity(id = "arch_p", title = "Archived Passport", body = "passport", isArchived = true)
        )

        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        vm.toggleSearch(true)
        vm.updateSearchQuery("passport")
        composeTestRule.waitForIdle()

        val matchedIds = vm.uiState.value.visibleNotes.map { it.id }.toSet()
        assertEquals(setOf(titleMatch.id, bodyMatch.id, checklistMatch.id), matchedIds)

        vm.updateSearchQuery("   ")
        composeTestRule.waitForIdle()
        assertEquals(3, vm.uiState.value.visibleNotes.size)
    }

    @Test
    fun notes_10_checklistAddItemDialogNextCancelOkBehavior() {
        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        // Create new checklist (opens directly in Edit mode)
        composeTestRule.onNodeWithTag("notes_bottom_nav_add").performClick()
        composeTestRule.onNodeWithTag("notes_add_checklist").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("checklist_add_item_button").performClick()
        composeTestRule.onNodeWithTag("checklist_item_dialog").assertIsDisplayed()

        // NEXT saves Milk, clears dialog, keeps dialog open
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Milk")
        composeTestRule.onNodeWithTag("checklist_item_dialog_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checklist_item_dialog").assertIsDisplayed()

        // NEXT saves Eggs, keeps dialog open
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Eggs")
        composeTestRule.onNodeWithTag("checklist_item_dialog_next").performClick()
        composeTestRule.waitForIdle()

        // Typing Temp and pressing CANCEL discards Temp but preserves Milk and Eggs
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Temp Unsaved")
        composeTestRule.onNodeWithTag("checklist_item_dialog_cancel").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("checklist_item_dialog").fetchSemanticsNodes().size)

        // Reopen, add Bread, tap OK -> saves Bread and closes
        composeTestRule.onNodeWithTag("checklist_add_item_button").performClick()
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Bread")
        composeTestRule.onNodeWithTag("checklist_item_dialog_ok").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("checklist_item_dialog").fetchSemanticsNodes().size)

        val openedNoteId = vm.uiState.value.editor!!.note.id
        awaitCondition {
            runBlocking { repository.getNoteById(openedNoteId)?.items?.size == 3 }
        }
        val saved = runBlocking { repository.getNoteById(openedNoteId)!! }
        assertEquals(listOf("Milk", "Eggs", "Bread"), saved.items.map { it.text })
    }

    @Test
    fun notes_11_undoRedoCoversTitleBodyColorAndChecklistChanges() {
        val (note, item) = runBlocking {
            val n = repository.createNote(type = NoteType.CHECKLIST, color = NoteColor.YELLOW, title = "Start Title")
            val i = repository.addChecklistItem(n.id, "First Step")!!
            n to i
        }

        val vm = createViewModel()
        vm.openExistingNote(note.id)
        awaitCondition {
            vm.uiState.value.editor != null
        }
        vm.enterEditMode()

        assertFalse(vm.uiState.value.editor!!.canUndo)
        assertFalse(vm.uiState.value.editor!!.canRedo)

        // No-op title update must NOT create undo history
        vm.updateEditorTitle("Start Title")
        assertFalse(vm.uiState.value.editor!!.canUndo)

        vm.updateEditorTitle("Edited Title")
        vm.updateEditorColor(NoteColor.PURPLE)
        vm.toggleChecklistItemInViewOrEdit(item.id)
        vm.openAddChecklistItemDialog()
        vm.updateChecklistDialogText("Second Step")
        vm.confirmChecklistDialogOk()

        assertEquals("Edited Title", vm.uiState.value.editor!!.note.title)
        assertEquals(NoteColor.PURPLE, vm.uiState.value.editor!!.note.color)
        assertEquals(2, vm.uiState.value.editor!!.note.items.size)
        assertTrue(vm.uiState.value.editor!!.canUndo)

        // Undo step by step
        vm.undoEditorChange() // undo Second Step add
        assertEquals(1, vm.uiState.value.editor!!.note.items.size)
        vm.undoEditorChange() // undo check toggle
        assertFalse(vm.uiState.value.editor!!.note.items.first().isChecked)
        vm.undoEditorChange() // undo color Purple -> Yellow
        assertEquals(NoteColor.YELLOW, vm.uiState.value.editor!!.note.color)
        vm.undoEditorChange() // undo title -> Start Title
        assertEquals("Start Title", vm.uiState.value.editor!!.note.title)
        assertFalse(vm.uiState.value.editor!!.canUndo)
        assertTrue(vm.uiState.value.editor!!.canRedo)

        // Redo restores step cleanly
        vm.redoEditorChange()
        assertEquals("Edited Title", vm.uiState.value.editor!!.note.title)
        awaitCondition {
            runBlocking { repository.getNoteById(note.id)?.title } == "Edited Title"
        }
        val persisted = runBlocking { repository.getNoteById(note.id)!! }
        assertEquals("Edited Title", persisted.title)
    }

    @Test
    fun notes_12_multiSelectEntrySelectionBulkColorAndSoftDeleteWithUndo() {
        val (n1, n2) = runBlocking {
            repository.createNote(type = NoteType.TEXT, title = "Note One", body = "One") to
                repository.createNote(type = NoteType.TEXT, title = "Note Two", body = "Two")
        }

        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("note_row_${n1.id}").fetchSemanticsNodes().isNotEmpty()
        }

        // Long-press enters multi-select
        composeTestRule.onNodeWithTag("note_row_${n1.id}").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notes_bulk_action_bar").assertIsDisplayed()

        // Select second row and change color to Black
        composeTestRule.onNodeWithTag("note_row_${n2.id}").performClick()
        composeTestRule.onNodeWithTag("notes_bulk_color_button").performClick()
        composeTestRule.onNodeWithTag("notes_bulk_color_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_color_swatch_black").performClick()
        awaitCondition { !vm.uiState.value.isSelectionMode }
        composeTestRule.waitForIdle()

        assertEquals(NoteColor.BLACK, runBlocking { repository.getNoteById(n1.id)!!.color })
        assertEquals(NoteColor.BLACK, runBlocking { repository.getNoteById(n2.id)!!.color })
        assertFalse(vm.uiState.value.isSelectionMode)

        // Re-select and delete -> Undo snackbar restores both notes
        composeTestRule.onNodeWithTag("note_row_${n1.id}").performTouchInput { longClick() }
        composeTestRule.onNodeWithTag("note_row_${n2.id}").performClick()
        composeTestRule.onNodeWithTag("notes_bulk_delete_button").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("notes_empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("notes_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").performClick()
        awaitCondition {
            runBlocking {
                repository.getNoteById(n1.id)?.isDeleted == false &&
                    repository.getNoteById(n2.id)?.isDeleted == false
            }
        }

        assertFalse(runBlocking { repository.getNoteById(n1.id)!!.isDeleted })
        assertFalse(runBlocking { repository.getNoteById(n2.id)!!.isDeleted })
    }

    @Test
    fun notes_13_shareIntentFormatsTitleBodyAndChecklistContent() : Unit = runBlocking {
        val textNote = repository.createNote(
            type = NoteType.TEXT,
            title = "Packing",
            body = "Passport\nCharger"
        )
        val textIntent = NotesShareHelper.buildShareIntent(textNote)
        assertEquals(Intent.ACTION_SEND, textIntent.action)
        assertEquals("text/plain", textIntent.type)
        assertEquals("Packing", textIntent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Packing\n\nPassport\nCharger", textIntent.getStringExtra(Intent.EXTRA_TEXT))

        val checklist = repository.createNote(type = NoteType.CHECKLIST, title = "Errands")
        val item1 = repository.addChecklistItem(checklist.id, "Post Office")!!
        repository.addChecklistItem(checklist.id, "Pharmacy")
        val updatedChecklist = repository.toggleChecklistItem(checklist.id, item1.id, true)!!

        val checklistIntent = NotesShareHelper.buildShareIntent(updatedChecklist)
        assertEquals("Errands", checklistIntent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(
            "Errands\n\n[x] Post Office\n[ ] Pharmacy",
            checklistIntent.getStringExtra(Intent.EXTRA_TEXT)
        )
    }

    @Test
    fun notes_14_autosaveNavigationWriteOrderingAndUnsavedDiscard() {
        val vm = createViewModel(debounceMs = 150L)
        vm.createNewNote(NoteType.TEXT)
        vm.updateEditorTitle("First")
        vm.updateEditorBody("Body v1")
        vm.updateEditorTitle("Final Title")
        vm.updateEditorBody("Final Body")
        val draftId = vm.uiState.value.editor!!.note.id

        // Immediate close flushes the latest state without stale earlier values overwriting it
        runBlocking { vm.closeEditorSafely() }
        val persisted = runBlocking { repository.getNoteById(draftId)!! }
        assertEquals("Final Title", persisted.title)
        assertEquals("Final Body", persisted.body)

        // Discarding an autosaved new draft removes it completely
        vm.createNewNote(NoteType.TEXT)
        vm.updateEditorBody("Temporary draft")
        val tempId = vm.uiState.value.editor!!.note.id
        awaitCondition {
            runBlocking { repository.getNoteById(tempId) != null }
        }
        assertNotNull(runBlocking { repository.getNoteById(tempId) })
        vm.discardEditorChangesOrNewNote()
        awaitCondition {
            runBlocking { repository.getNoteById(tempId) == null }
        }
        assertNull(runBlocking { repository.getNoteById(tempId) })
    }

    @Test
    fun notes_15_ab1IndependenceAndStage2TabsRemainClean() {
        val sdkInitCalls = AtomicInteger(0)
        val coordinator = MapActivationCoordinator(
            runtimeInitializer = MapRuntimeInitializer { sdkInitCalls.incrementAndGet() },
            dependenciesFactory = { ctx -> MapRuntimeDependencies(ctx) },
            activeSessionContent = { _, _, _, _, _ ->
                androidx.compose.material3.Text("Map Active UI")
            }
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AppHostShell(
                    initialDestination = AppHostDestination.NOTES,
                    mapFeature = coordinator
                )
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(MapActivationState.Inactive, coordinator.state.value)
        assertEquals(0, sdkInitCalls.get())
        composeTestRule.onNodeWithTag("notes_workspace_screen").assertIsDisplayed()

        // Switch through honest Stage-2 placeholder tabs
        composeTestRule.onNodeWithTag("notes_bottom_nav_habits").performClick()
        composeTestRule.onNodeWithTag("notes_stage2_placeholder_habits").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notes_bottom_nav_expense").performClick()
        composeTestRule.onNodeWithTag("notes_stage2_placeholder_expense").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notes_bottom_nav_calendar").performClick()
        composeTestRule.onNodeWithTag("notes_stage2_placeholder_calendar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notes_bottom_nav_notes").performClick()
        composeTestRule.onNodeWithTag("notes_workspace_screen").assertIsDisplayed()

        // Still never touched Map runtime
        assertEquals(0, sdkInitCalls.get())

        // Return to Map activates Map cleanly
        composeTestRule.onNodeWithTag("notes_return_to_map_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(MapActivationState.Active, coordinator.state.value)
        assertEquals(1, sdkInitCalls.get())
        composeTestRule.onNodeWithText("Map Active UI").assertIsDisplayed()
    }

    @Test
    fun notes_16_checklistDualAddItemControlsEmptyScrollableAndHiddenInViewMode() {
        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitForIdle()

        // Create empty checklist -> opens directly in Edit mode with BOTH Add Item rows and zero empty-state message
        composeTestRule.onNodeWithTag("notes_bottom_nav_add").performClick()
        composeTestRule.onNodeWithTag("notes_add_checklist").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("checklist_add_item_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checklist_add_item_bottom_button").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithText("No items yet").fetchSemanticsNodes().size)

        // Verify modest ColorNote-style vertical gap (~56dp) instead of pinning bottom Add Item to screen bottom
        val topAddBounds = composeTestRule.onNodeWithTag("checklist_add_item_button").getUnclippedBoundsInRoot()
        val bottomAddBounds = composeTestRule.onNodeWithTag("checklist_add_item_bottom_button").getUnclippedBoundsInRoot()
        val verticalGap = bottomAddBounds.top - topAddBounds.bottom
        assertTrue("Expected modest gap (~56dp), got $verticalGap", verticalGap >= 40.dp && verticalGap <= 96.dp)

        // Top Add Item opens dialog with NEXT + CANCEL + OK
        composeTestRule.onNodeWithTag("checklist_add_item_button").performClick()
        composeTestRule.onNodeWithTag("checklist_item_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Top Item")
        composeTestRule.onNodeWithTag("checklist_item_dialog_next").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Discarded Text")
        composeTestRule.onNodeWithTag("checklist_item_dialog_cancel").performClick()
        composeTestRule.waitForIdle()

        // Bottom Add Item opens the exact same dialog and saves via OK
        composeTestRule.onNodeWithTag("checklist_add_item_bottom_button").performClick()
        composeTestRule.onNodeWithTag("checklist_item_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checklist_item_dialog_input").performTextInput("Bottom Item")
        composeTestRule.onNodeWithTag("checklist_item_dialog_ok").performClick()
        composeTestRule.waitForIdle()

        val checklistId = vm.uiState.value.editor!!.note.id
        awaitCondition {
            runBlocking { repository.getNoteById(checklistId)?.items?.size == 2 }
        }
        assertEquals(
            listOf("Top Item", "Bottom Item"),
            runBlocking { repository.getNoteById(checklistId)!!.items.map { it.text } }
        )

        // Switching to View mode hides both Add Item controls
        composeTestRule.onNodeWithTag("note_editor_done_button").performClick()
        composeTestRule.waitUntil(3_000L) {
            vm.uiState.value.editor?.isEditing == false
        }
        assertEquals(0, composeTestRule.onAllNodesWithTag("checklist_add_item_button").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("checklist_add_item_bottom_button").fetchSemanticsNodes().size)

        // Add many items so bottom Add Item sits at the end of the scrollable list (not a fixed overlay)
        runBlocking {
            for (i in 3..25) {
                repository.addChecklistItem(checklistId, "Scrollable Item $i")
            }
        }
        awaitCondition {
            vm.uiState.value.allActiveNotes.firstOrNull { it.id == checklistId }?.items?.size == 25
        }
        vm.openExistingNote(checklistId)
        awaitCondition { vm.uiState.value.editor?.note?.items?.size == 25 }
        vm.enterEditMode()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("checklist_add_item_button").assertIsDisplayed()
        // Bottom Add Item is below the 25 items in the scrollable list (not pinned as an overlay)
        assertTrue(
            composeTestRule.onAllNodesWithTag("checklist_add_item_bottom_button").fetchSemanticsNodes().isEmpty()
        )
        composeTestRule.onNodeWithTag("checklist_edit_list").performScrollToIndex(26)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checklist_add_item_bottom_button").assertIsDisplayed()
    }

    @Test
    fun notes_17_ruledPaperPositioningWrappedParagraphsViewEditAndLongScroll() {
        // 1. Verify exact snug baseline-to-rule positioning and empty-space continuation
        val lineStepPx = 90f
        val topOffsetPx = 30f
        val snugDescentPx = 10.5f
        // Simulated baselines for wrapped 2-line paragraph + blank newline + line 4
        val baselines = listOf(78f, 168f, 258f, 348f)
        val positions = computeRuledLinePositions(
            lineBaselinesPx = baselines,
            topOffsetPx = topOffsetPx,
            fallbackStepPx = lineStepPx,
            snugDescentPx = snugDescentPx,
            totalHeightPx = 900f
        )
        assertTrue(positions.size >= 8)
        baselines.forEachIndexed { idx, baseline ->
            val expectedRuleY = topOffsetPx + baseline + snugDescentPx
            assertEquals(expectedRuleY, positions[idx], 0.01f)
            // Snug against baseline (~10.5px below baseline), NOT halfway (~45px below)
            assertTrue((positions[idx] - (topOffsetPx + baseline)) < lineStepPx * 0.25f)
        }
        for (i in 1 until positions.size) {
            assertEquals(lineStepPx, positions[i] - positions[i - 1], 0.01f)
        }

        // 2. Verify View and Edit modes render ruled paper surface with wrapped, multi-paragraph, scrollable note
        val longBody = buildString {
            append("Wrapped paragraph line that should wrap naturally across multiple visual lines on mobile widths without drifting away from its horizontal rules.\n\n\n")
            for (i in 1..45) {
                append("Notebook entry line $i\n")
            }
        }
        val note = runBlocking {
            repository.createNote(type = NoteType.TEXT, title = "Ruled Test", body = longBody)
        }

        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("note_row_${note.id}").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("note_row_${note.id}").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("note_viewer_body").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("note_paper_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_paper_scroll_container").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("note_paper_surface").assertIsDisplayed()

        composeTestRule.onNodeWithTag("note_editor_edit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("note_paper_surface").assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_editor_body_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("note_paper_scroll_container").performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("note_paper_surface").assertIsDisplayed()
    }

    @Test
    fun notes_18_allNineNoteColorsHaveDistinctNonHardcodedRuleColors() {
        val colors = NoteColor.orderedColors
        assertEquals(9, colors.size)

        // Each color has its own distinct rule line color (not one hardcoded yellow for all)
        val distinctRuleColors = colors.map { it.editorRuleLineColor }.toSet()
        assertEquals(9, distinctRuleColors.size)

        colors.forEach { color ->
            assertTrue(
                "${color.displayName} rule line must have visible opacity",
                color.editorRuleLineColor.alpha >= 0.20f
            )
            assertTrue(
                "${color.displayName} rule line must remain distinct from paper background",
                color.editorRuleLineColor != color.editorPaperBackground
            )
            assertTrue(
                "${color.displayName} rule line must remain softer than primary text",
                color.editorRuleLineColor.alpha < 0.60f && color.editorRuleLineColor != color.editorPaperText
            )
        }
    }

    @Test
    fun notes_19_concaveBottomBarGeometryNavigationSmallScreenAndMultiSelect() {
        // 1. Verify concave cradle curve math: flat shoulders, symmetric smooth valley, deep center nadir
        val centerX = 500f
        val halfSpan = 120f
        val depth = 96f
        assertEquals(0f, computeConcaveCradleOffsetY(0f, centerX, halfSpan, depth), 0.01f)
        assertEquals(0f, computeConcaveCradleOffsetY(centerX - halfSpan, centerX, halfSpan, depth), 0.01f)
        assertEquals(0f, computeConcaveCradleOffsetY(centerX + halfSpan, centerX, halfSpan, depth), 0.01f)
        assertEquals(depth, computeConcaveCradleOffsetY(centerX, centerX, halfSpan, depth), 0.01f)
        val leftQuarter = computeConcaveCradleOffsetY(centerX - 60f, centerX, halfSpan, depth)
        val rightQuarter = computeConcaveCradleOffsetY(centerX + 60f, centerX, halfSpan, depth)
        assertTrue(leftQuarter > 0f && leftQuarter < depth)
        assertEquals(leftQuarter, rightQuarter, 0.05f)

        // 2. Verify host screen navigation, raised +, selection semantics, and multi-select switch
        runBlocking { repository.createNote(type = NoteType.TEXT, title = "Concave Test Note", body = "Body") }
        val vm = createViewModel()
        composeTestRule.setContent {
            MyApplicationTheme {
                NotesHostScreen(onReturnToMap = {}, notesViewModel = vm)
            }
        }
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("notes_bottom_bar_surface").fetchSemanticsNodes().isNotEmpty()
        }

        val barBounds = composeTestRule.onNodeWithTag("notes_bottom_bar_surface").getUnclippedBoundsInRoot()
        val addBounds = composeTestRule.onNodeWithTag("notes_bottom_nav_add").getUnclippedBoundsInRoot()
        assertTrue("Center + button must rise above bottom bar shoulder", addBounds.top < barBounds.top)
        assertTrue("Center + touch target must be >= 48dp", (addBounds.right - addBounds.left) >= 48.dp)
        assertTrue("Center + touch target height must be >= 48dp", (addBounds.bottom - addBounds.top) >= 48.dp)
        val barCenterX = (barBounds.left + barBounds.right) / 2f
        val addCenterX = (addBounds.left + addBounds.right) / 2f
        assertTrue("Center + must sit centered in cradle", abs((addCenterX - barCenterX).value) <= 2f)

        composeTestRule.onNodeWithTag("notes_bottom_nav_notes").assertIsSelected()
        composeTestRule.onNodeWithTag("notes_bottom_nav_habits").assertIsNotSelected()
        composeTestRule.onNodeWithTag("notes_bottom_nav_expense").assertIsNotSelected()
        composeTestRule.onNodeWithTag("notes_bottom_nav_calendar").assertIsNotSelected()

        composeTestRule.onNodeWithTag("notes_bottom_nav_habits").performClick()
        composeTestRule.onNodeWithTag("notes_bottom_nav_habits").assertIsSelected()
        composeTestRule.onNodeWithTag("notes_bottom_nav_notes").assertIsNotSelected()
        composeTestRule.onNodeWithTag("notes_stage2_placeholder_habits").assertIsDisplayed()

        composeTestRule.onNodeWithTag("notes_bottom_nav_expense").performClick()
        composeTestRule.onNodeWithTag("notes_bottom_nav_expense").assertIsSelected()
        composeTestRule.onNodeWithTag("notes_stage2_placeholder_expense").assertIsDisplayed()

        composeTestRule.onNodeWithTag("notes_bottom_nav_calendar").performClick()
        composeTestRule.onNodeWithTag("notes_bottom_nav_calendar").assertIsSelected()
        composeTestRule.onNodeWithTag("notes_stage2_placeholder_calendar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("notes_bottom_nav_notes").performClick()
        composeTestRule.onNodeWithTag("notes_bottom_nav_notes").assertIsSelected()
        composeTestRule.onNodeWithTag("notes_list").assertIsDisplayed()

        composeTestRule.onNodeWithTag("notes_bottom_nav_add").performClick()
        composeTestRule.onNodeWithTag("notes_add_chooser_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // Multi-select switches cleanly to bulk action bar
        val firstId = vm.uiState.value.visibleNotes.first().id
        composeTestRule.onNodeWithTag("note_row_$firstId").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("notes_bulk_action_bar").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("notes_bottom_bar_surface").fetchSemanticsNodes().size)
    }

    @Test
    fun notes_20_concaveBottomBarNarrowWidthAndLargeFontNoOverlap() {
        composeTestRule.setContent {
            MyApplicationTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 1.4f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(320.dp)
                            .height(140.dp)
                    ) {
                        NotesFiveSlotBottomBar(
                            activeTab = NotesBottomTab.NOTES,
                            onSelectTab = {},
                            onAddClick = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val notesBounds = composeTestRule.onNodeWithTag("notes_bottom_nav_notes").getUnclippedBoundsInRoot()
        val habitsBounds = composeTestRule.onNodeWithTag("notes_bottom_nav_habits").getUnclippedBoundsInRoot()
        val addBounds = composeTestRule.onNodeWithTag("notes_bottom_nav_add").getUnclippedBoundsInRoot()
        val expenseBounds = composeTestRule.onNodeWithTag("notes_bottom_nav_expense").getUnclippedBoundsInRoot()
        val calendarBounds = composeTestRule.onNodeWithTag("notes_bottom_nav_calendar").getUnclippedBoundsInRoot()
        val surfaceBounds = composeTestRule.onNodeWithTag("notes_bottom_bar_surface").getUnclippedBoundsInRoot()

        assertTrue(notesBounds.right <= habitsBounds.left)
        assertTrue(habitsBounds.right <= addBounds.left)
        assertTrue(addBounds.right <= expenseBounds.left)
        assertTrue(expenseBounds.right <= calendarBounds.left)

        listOf(notesBounds, habitsBounds, expenseBounds, calendarBounds).forEach { tab ->
            assertTrue("Tab height must remain >= 48dp", (tab.bottom - tab.top) >= 48.dp)
            assertTrue("Tab must remain vertically inside white bar", tab.top >= surfaceBounds.top && tab.bottom <= surfaceBounds.bottom)
        }
    }
}
