package com.pookie.studysprint.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        TaskEntity::class,
        SessionEntity::class,
        SegmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudyDatabase : RoomDatabase() {

    abstract fun subjectDao(): SubjectDao
    abstract fun taskDao(): TaskDao
    abstract fun sessionDao(): SessionDao
    abstract fun segmentDao(): SegmentDao

    companion object {
        /**
         * No destructive fallback: the user's study history must never be wiped silently.
         * If the schema changes in a future version, bump `version` above and add a
         * proper Migration here with .addMigrations(...).
         */
        fun create(context: Context): StudyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                StudyDatabase::class.java,
                "studysprint.db"
            ).build()
    }
}