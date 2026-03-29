package com.example.peyarealnumbers.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [JornadaEntity::class, SesionEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jornadaDao(): JornadaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "peya_real_numbers_db"
                )
                .fallbackToDestructiveMigration() // Borra la DB vieja si cambia el esquema
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}