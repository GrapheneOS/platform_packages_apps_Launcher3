package com.android.launcher3.provider

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class GosLauncherDbMigrationsTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrateGalleryFavorite_updatesOnlyOldGalleryApplications() {
        val db = TestDatabaseHelper(context).writableDatabase
        val oldGalleryComponent = ComponentName(OLD_GALLERY_PACKAGE, OLD_GALLERY_ACTIVITY)
        val oldGalleryComponentId = insertFavorite(
            db,
            Favorites.ITEM_TYPE_APPLICATION,
            "Gallery",
            makeLaunchIntent(oldGalleryComponent),
        )
        val oldGalleryPackageId = insertFavorite(
            db,
            Favorites.ITEM_TYPE_APPLICATION,
            "Gallery",
            makePackageIntent(OLD_GALLERY_PACKAGE),
        )
        val settingsComponent = ComponentName(
            context.packageName,
            SettingsActivity::class.java.name,
        )
        val settingsId = insertFavorite(
            db,
            Favorites.ITEM_TYPE_APPLICATION,
            "Settings",
            makeLaunchIntent(settingsComponent),
        )
        val oldGalleryDeepShortcutId = insertFavorite(
            db,
            Favorites.ITEM_TYPE_DEEP_SHORTCUT,
            "Gallery deep shortcut",
            makeLaunchIntent(oldGalleryComponent),
        )
        val newGalleryComponent = ComponentName(NEW_GALLERY_PACKAGE, NEW_GALLERY_ACTIVITY)
        val newGalleryIntent = makeLaunchIntent(newGalleryComponent)

        assertEquals(
            2,
            GosLauncherDbMigrations.migrateGalleryFavorite(db, newGalleryIntent),
        )
        assertEquals(4, getFavoriteDataCount(db))
        assertFavorite(
            db,
            oldGalleryComponentId,
            Favorites.ITEM_TYPE_APPLICATION,
            newGalleryComponent,
        )
        assertFavorite(
            db,
            oldGalleryPackageId,
            Favorites.ITEM_TYPE_APPLICATION,
            newGalleryComponent,
        )
        assertFavorite(db, settingsId, Favorites.ITEM_TYPE_APPLICATION, settingsComponent)
        assertFavorite(
            db,
            oldGalleryDeepShortcutId,
            Favorites.ITEM_TYPE_DEEP_SHORTCUT,
            oldGalleryComponent,
        )
    }

    @Test
    fun runIdempotentMigrations_recordsMigrationVersion() {
        val db = TestDatabaseHelper(context).writableDatabase
        GosLauncherDbMigrations.clearIdempotentMigrationVersionForTest(context, db)

        try {
            val oldGalleryComponent = ComponentName(OLD_GALLERY_PACKAGE, OLD_GALLERY_ACTIVITY)
            val firstOldGalleryId = insertFavorite(
                db,
                Favorites.ITEM_TYPE_APPLICATION,
                "Gallery",
                makeLaunchIntent(oldGalleryComponent),
            )
            val newGalleryComponent = ComponentName(NEW_GALLERY_PACKAGE, NEW_GALLERY_ACTIVITY)
            val newGalleryIntent = makeLaunchIntent(newGalleryComponent)

            assertEquals(
                1,
                GosLauncherDbMigrations.runIdempotentMigrationsForTest(
                    context,
                    db,
                    newGalleryIntent,
                ),
            )
            assertFavorite(
                db,
                firstOldGalleryId,
                Favorites.ITEM_TYPE_APPLICATION,
                newGalleryComponent,
            )

            val secondOldGalleryId = insertFavorite(
                db,
                Favorites.ITEM_TYPE_APPLICATION,
                "Gallery",
                makeLaunchIntent(oldGalleryComponent),
            )

            assertEquals(
                0,
                GosLauncherDbMigrations.runIdempotentMigrationsForTest(
                    context,
                    db,
                    newGalleryIntent,
                ),
            )
            assertFavorite(
                db,
                secondOldGalleryId,
                Favorites.ITEM_TYPE_APPLICATION,
                oldGalleryComponent,
            )
        } finally {
            GosLauncherDbMigrations.clearIdempotentMigrationVersionForTest(context, db)
        }
    }

    private fun insertFavorite(
        db: SQLiteDatabase,
        itemType: Int,
        title: String,
        intent: Intent,
    ): Long {
        val values = ContentValues().apply {
            put(Favorites.ITEM_TYPE, itemType)
            put(Favorites.TITLE, title)
            put(Favorites.INTENT, intent.toUri(0))
            put(Favorites.CONTAINER, Favorites.CONTAINER_HOTSEAT)
            put(Favorites.SCREEN, SCREEN)
            put(Favorites.CELLX, CELL_X)
            put(Favorites.CELLY, CELL_Y)
            put(Favorites.SPANX, SPAN_X)
            put(Favorites.SPANY, SPAN_Y)
        }
        return db.insert(Favorites.TABLE_NAME, null, values)
    }

    private fun makeLaunchIntent(component: ComponentName): Intent {
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private fun makePackageIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_GALLERY)
            .setPackage(packageName)
    }

    private fun getFavoriteDataCount(db: SQLiteDatabase): Int {
        val count = db.query(
            Favorites.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            cursor.count
        }
        return count
    }

    private fun assertFavorite(
        db: SQLiteDatabase,
        id: Long,
        expectedItemType: Int,
        expectedComponent: ComponentName,
    ) {
        db.query(
            Favorites.TABLE_NAME,
            arrayOf(
                Favorites.ITEM_TYPE,
                Favorites.INTENT,
                Favorites.CONTAINER,
                Favorites.SCREEN,
                Favorites.CELLX,
                Favorites.CELLY,
                Favorites.SPANX,
                Favorites.SPANY,
            ),
            "${Favorites._ID} = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToNext()

            val intent = Intent.parseUri(
                cursor.getString(cursor.getColumnIndexOrThrow(Favorites.INTENT)),
                0,
            )
            assertEquals(expectedItemType, cursor.getInt(
                cursor.getColumnIndexOrThrow(Favorites.ITEM_TYPE),
            ))
            assertEquals(expectedComponent, intent.component)
            assertEquals(Favorites.CONTAINER_HOTSEAT, cursor.getInt(
                cursor.getColumnIndexOrThrow(Favorites.CONTAINER),
            ))
            assertEquals(SCREEN, cursor.getInt(cursor.getColumnIndexOrThrow(Favorites.SCREEN)))
            assertEquals(CELL_X, cursor.getInt(cursor.getColumnIndexOrThrow(Favorites.CELLX)))
            assertEquals(CELL_Y, cursor.getInt(cursor.getColumnIndexOrThrow(Favorites.CELLY)))
            assertEquals(SPAN_X, cursor.getInt(cursor.getColumnIndexOrThrow(Favorites.SPANX)))
            assertEquals(SPAN_Y, cursor.getInt(cursor.getColumnIndexOrThrow(Favorites.SPANY)))
        }
    }

    private class TestDatabaseHelper(context: Context) :
        DatabaseHelper(context, null, Runnable {}) {

        override fun handleOneTimeDataUpgrade(db: SQLiteDatabase) {
        }
    }

    private companion object {
        const val OLD_GALLERY_PACKAGE = "com.android.gallery3d"
        const val OLD_GALLERY_ACTIVITY = "com.android.gallery3d.app.GalleryActivity"
        const val NEW_GALLERY_PACKAGE = "app.grapheneos.gallery"
        const val NEW_GALLERY_ACTIVITY = "com.dot.gallery.Launcher_ReFra"
        const val SCREEN = 2
        const val CELL_X = 2
        const val CELL_Y = 0
        const val SPAN_X = 1
        const val SPAN_Y = 1
    }
}
