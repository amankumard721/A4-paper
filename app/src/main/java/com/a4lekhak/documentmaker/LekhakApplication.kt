package com.a4lekhak.documentmaker

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.a4lekhak.documentmaker.data.LekhakDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp class LekhakApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LekhakDatabase =
        Room.databaseBuilder(context, LekhakDatabase::class.java, "lekhak.db").build()
}
