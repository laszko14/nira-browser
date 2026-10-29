package com.prirai.android.nira.videosearch

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Independent database created for the first time at schema version 1.
 * No migration of existing Zavro databases is needed or attempted.
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
    version = 1,
    exportSchema = false,
)
internal abstract class VideoSearchDatabase : RoomDatabase() {
    abstract fun dao(): VideoSearchDao

    companion object {
        @Volatile private var instance: VideoSearchDatabase? = null

        fun getInstance(context: Context): VideoSearchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    VideoSearchDatabase::class.java,
                    "zavro_video_search.db",
                ).build().also { instance = it }
            }
    }
}
