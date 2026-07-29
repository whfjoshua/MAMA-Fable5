package com.mama.scheduler.di

import android.content.Context
import com.mama.scheduler.data.local.ChatDao
import com.mama.scheduler.data.local.KidProfileDao
import com.mama.scheduler.data.local.MamaDatabase
import com.mama.scheduler.data.local.PendingApprovalEventDao
import com.mama.scheduler.data.local.ScheduledEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MamaDatabase =
        MamaDatabase.getInstance(context)

    @Provides
    fun provideKidProfileDao(db: MamaDatabase): KidProfileDao = db.kidProfileDao()

    @Provides
    fun provideScheduledEventDao(db: MamaDatabase): ScheduledEventDao = db.scheduledEventDao()

    @Provides
    fun providePendingApprovalEventDao(db: MamaDatabase): PendingApprovalEventDao =
        db.pendingApprovalEventDao()

    @Provides
    fun provideChatDao(db: MamaDatabase): ChatDao = db.chatDao()
}
