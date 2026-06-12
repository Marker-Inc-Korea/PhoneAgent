package ai.markr.phoneagent.di

import android.content.Context
import androidx.room.Room
import ai.markr.phoneagent.agent.DeviceController
import ai.markr.phoneagent.data.AppDatabase
import ai.markr.phoneagent.data.RunHistoryDao
import ai.markr.phoneagent.data.SettingsRepository
import ai.markr.phoneagent.platform.AccessibilityController
import ai.markr.phoneagent.runtime.AgentRunner
import ai.markr.phoneagent.voice.AndroidStt
import ai.markr.phoneagent.voice.AndroidTts
import ai.markr.phoneagent.voice.SpeechSynthesizer
import ai.markr.phoneagent.voice.SpeechTranscriber
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
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideDeviceController(@ApplicationContext context: Context): DeviceController =
        AccessibilityController(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "phoneagent.db").build()

    @Provides
    fun provideRunHistoryDao(db: AppDatabase): RunHistoryDao = db.runHistoryDao()

    @Provides
    @Singleton
    fun provideSpeechSynthesizer(@ApplicationContext context: Context): SpeechSynthesizer =
        AndroidTts(context)

    @Provides
    @Singleton
    fun provideSpeechTranscriber(@ApplicationContext context: Context): SpeechTranscriber =
        AndroidStt(context)

    @Provides
    @Singleton
    fun provideAgentRunner(
        controller: DeviceController,
        settingsRepository: SettingsRepository,
        dao: RunHistoryDao,
    ): AgentRunner = AgentRunner(
        controller = controller,
        settingsProvider = { settingsRepository.current() },
        saveRecord = { dao.insert(it) },
    )
}
