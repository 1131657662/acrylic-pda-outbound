package com.acrylic.m71demo.di

import android.content.Context
import androidx.room.Room
import com.acrylic.m71demo.data.*
import com.acrylic.m71demo.device.*
import com.acrylic.m71demo.domain.*
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): DemoDatabase =
        Room.databaseBuilder(context, DemoDatabase::class.java, "m71-demo.db")
            .openHelperFactory { FullSyncOpenHelper(it) }
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(DemoMigrations.from1To2, DemoMigrations.from2To3)
            .addCallback(DurabilityCallback()).build()

    @Provides fun clock(value: AndroidClock): AppClock = value
    @Provides fun signer(value: KeystoreSigner): DemoSigner = value
}
