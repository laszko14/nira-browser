package com.prirai.android.nira.videosearch

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Exercise v1 SQL data and the additive v2 migration without destructive
 * recreation. No private rows are ever written to the persistent database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchDatabaseMigrationV2Test {
    @Test fun v1FilmsAndSavedFoldersSurviveUrlIndexMigration() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "video-search-v1-index-migration-test.db"
        context.deleteDatabase(databaseName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE video_search_films (" +
                            "profileId TEXT NOT NULL, id TEXT NOT NULL, " +
                            "runId TEXT NOT NULL, siteId TEXT NOT NULL, " +
                            "siteName TEXT NOT NULL, filmPageUrl TEXT NOT NULL, " +
                            "originalTitle TEXT, thumbnailUrl TEXT, durationSeconds INTEGER, " +
                            "titleSource TEXT NOT NULL, imageSource TEXT NOT NULL, " +
                            "originalIndex INTEGER NOT NULL, foundAtMs INTEGER NOT NULL, " +
                            "PRIMARY KEY(profileId,id))")
                        db.execSQL("CREATE TABLE video_saved_folders (" +
                            "profileId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, " +
                            "createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, " +
                            "PRIMARY KEY(profileId,id))")
                        db.execSQL("INSERT INTO video_search_films VALUES (" +
                            "'default','film-1','run-1','site-1','Films'," +
                            "'https://films.example/watch?id=1','Saved title',NULL,NULL," +
                            "'CARD','UNKNOWN',0,1)")
                        db.execSQL("INSERT INTO video_saved_folders VALUES (" +
                            "'default','folder-1','Saved',1,1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, next: Int) {}
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            VideoSearchDatabase.MIGRATION_1_2.migrate(db)
            db.query("PRAGMA index_list('video_search_films')").use { cursor ->
                var indexed = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) ==
                        "index_video_search_films_profileId_runId_siteId_filmPageUrl")
                        indexed = true
                }
                assertTrue("Expected non-destructive URL lookup index", indexed)
            }
            db.query("SELECT originalTitle FROM video_search_films WHERE id='film-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Saved title", it.getString(0))
            }
            db.query("SELECT name FROM video_saved_folders WHERE id='folder-1'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Saved", it.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(databaseName)
        }
    }
}
