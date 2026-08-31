package org.librelab.dialer.di

import android.content.Context
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.librelab.dialer.ui.settings.SettingsPrefs

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun telecomManager(@ApplicationContext context: Context): TelecomManager {
        return context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    @Provides
    @Singleton
    fun telephonyManager(@ApplicationContext context: Context): TelephonyManager {
        return context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    @Provides
    @Singleton
    fun settingsPrefs(@ApplicationContext context: Context): SettingsPrefs {
        return SettingsPrefs(context)
    }
}
