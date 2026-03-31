package com.example.peyarealnumbers.di

import android.content.Context
import com.example.peyarealnumbers.database.AppDatabase
import com.example.peyarealnumbers.database.JornadaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideJornadaDao(database: AppDatabase): JornadaDao {
        return database.jornadaDao()
    }
}
