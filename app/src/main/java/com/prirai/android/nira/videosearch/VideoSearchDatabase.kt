package com.prirai.android.nira.videosearch

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Independent search database, with a non-destructive v1 -> v2 index migration.
 * This is separate from every other Zavro database.
 * Never use fallbackToDestructiveMigration: search history and saved folders
 * must survive upgrades. A future schema bump requires an explicit migration.
 *
 * All private-mode records must remain RAM-only, never enter this database.
 */
@Database(
    entities = [
        VideoSearchSiteRow::class,
        VideoSearchSettingsRow::class,
        VideoSearchRunRow::class,
        VideoSearchTaskRow::class,
        VideoSearchFilmRow::class,
        VideoSavedFolderRow::class,
        VideoSavedLinkRow::class,
    ],
    version = 2,
    exportSchema = false,
)
internal abstract class VideoSearchDatabase : RoomDatabase() {
    abstract fun dao(): VideoSearchDao

    companion object {
        /** Only adds an index; original history, saved folders and links stay. */
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                    "`index_video_search_films_profileId_runId_siteId_filmPageUrl` " +
                    "ON `video_search_films` " +
                    "(`profileId`, `runId`, `siteId`, `filmPageUrl`)"
                )
            }
        }

        @Volatile private var instance: VideoSearchDatabase? = null

        fun getInstance(context: Context): VideoSearchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoSearchDatabase::class.java,
                    "zavro_video_search.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
