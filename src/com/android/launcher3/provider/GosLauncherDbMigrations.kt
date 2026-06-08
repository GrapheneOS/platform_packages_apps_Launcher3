package com.android.launcher3.provider

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.VisibleForTesting
import com.android.launcher3.ConstantItem
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.logging.FileLog
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.provider.LauncherDbUtils.SQLiteTransaction
import java.io.File
import java.net.URISyntaxException

object GosLauncherDbMigrations {
    private const val TAG = "GrapheneOsLauncherDbMigrations"

    private const val OLD_GALLERY_PACKAGE = "com.android.gallery3d"
    private const val NEW_GALLERY_PACKAGE = "app.grapheneos.gallery"

    private const val IDEMPOTENT_DB_MIGRATIONS_PREF_PREFIX =
        "gos_launcher_idempotent_db_migrations_version"
    private const val IN_MEMORY_DB_NAME = "memory"

    private const val GALLERY_FAVORITE_MIGRATION_VERSION = 1

    @JvmStatic
    fun runIdempotentMigrations(context: Context, db: SQLiteDatabase): Int {
        return runIdempotentMigrations(context, db) { c ->
            getNewGalleryIntent(c)
        }
    }

    @VisibleForTesting
    @JvmStatic
    fun runIdempotentMigrationsForTest(
        context: Context,
        db: SQLiteDatabase,
        newGalleryIntent: Intent,
    ): Int {
        return runIdempotentMigrations(context, db) {
            newGalleryIntent
        }
    }

    @VisibleForTesting
    @JvmStatic
    fun clearIdempotentMigrationVersionForTest(context: Context, db: SQLiteDatabase) {
        LauncherPrefs.get(context).removeSync(getIdempotentMigrationVersionPref(db))
    }

    private fun runIdempotentMigrations(
        context: Context,
        db: SQLiteDatabase,
        newGalleryIntentProvider: (Context) -> Intent?,
    ): Int {
        val migrationVersionPref = getIdempotentMigrationVersionPref(db)
        val prefs = LauncherPrefs.get(context)
        var migrationVersion = prefs.get(migrationVersionPref)
        var migrated = 0

        if (migrationVersion < GALLERY_FAVORITE_MIGRATION_VERSION) {
            val newGalleryIntent = newGalleryIntentProvider(context) ?: return 0

            migrated += migrateGalleryFavorite(db, newGalleryIntent)
            migrationVersion = GALLERY_FAVORITE_MIGRATION_VERSION
            prefs.putSync(migrationVersionPref.to(migrationVersion))
        }

        return migrated
    }

    private fun getIdempotentMigrationVersionPref(db: SQLiteDatabase): ConstantItem<Int> {
        val dbPath = db.path
        val dbName = if (dbPath.isNullOrEmpty()) {
            IN_MEMORY_DB_NAME
        } else {
            File(dbPath).name
        }
        return LauncherPrefs.nonRestorableItem(
            "$IDEMPOTENT_DB_MIGRATIONS_PREF_PREFIX@$dbName",
            0,
        )
    }

    private fun getNewGalleryIntent(context: Context): Intent? {
        val component = context
            .packageManager
            .getLaunchIntentForPackage(NEW_GALLERY_PACKAGE)
            ?.component

        if (component == null) {
            FileLog.d(TAG, "No launch intent found for $NEW_GALLERY_PACKAGE")
            return null
        }

        return AppInfo.makeLaunchIntent(component)
    }

    @JvmStatic
    fun migrateGalleryFavorite(db: SQLiteDatabase, newGalleryIntent: Intent): Int {
        val newGalleryIntentUri = newGalleryIntent.toUri(0)
        var migrated = 0

        SQLiteTransaction(db).use { transaction ->
            db.query(
                Favorites.TABLE_NAME,
                arrayOf(Favorites._ID, Favorites.INTENT),
                "${Favorites.ITEM_TYPE} = ?",
                arrayOf(Favorites.ITEM_TYPE_APPLICATION.toString()),
                null,
                null,
                null,
            )
                .use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(Favorites._ID)
                    val intentIndex = cursor.getColumnIndexOrThrow(Favorites.INTENT)
                    while (cursor.moveToNext()) {
                        if (!isOldGalleryIntent(cursor.getString(intentIndex))) {
                            continue
                        }

                        val id = cursor.getLong(idIndex)
                        val values = ContentValues().apply {
                            put(Favorites.INTENT, newGalleryIntentUri)
                        }
                        migrated += db.update(
                            Favorites.TABLE_NAME,
                            values,
                            "${Favorites._ID} = ?",
                            arrayOf(id.toString()),
                        )
                    }
                }
            transaction.commit()
        }

        FileLog.d(TAG, "Migrated $migrated Gallery favorites")

        return migrated
    }

    private fun isOldGalleryIntent(intentUri: String?): Boolean {
        if (intentUri == null) {
            return false
        }

        val intent = try {
            Intent.parseUri(intentUri, 0)
        } catch (e: URISyntaxException) {
            FileLog.d(TAG, "Unable to parse favorite intent while migrating Gallery", e)
            return false
        }

        if (intent.component?.packageName == OLD_GALLERY_PACKAGE) {
            return true
        }

        return intent.`package` == OLD_GALLERY_PACKAGE
    }
}
