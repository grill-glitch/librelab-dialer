package org.librelab.dialer.data.antispam

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdentityPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val p = context.getSharedPreferences("mi_device_identity", Context.MODE_PRIVATE)

    fun load(): DeviceIdentityData? {
        val uuid = p.getString("uuid", null) ?: return null
        val oaId = p.getString("oaId", null) ?: return null
        val imeimd5 = p.getString("imeimd5", null) ?: return null
        return DeviceIdentityData(uuid, oaId, imeimd5)
    }

    fun save(data: DeviceIdentityData) {
        p.edit()
            .putString("uuid", data.uuid)
            .putString("oaId", data.oaId)
            .putString("imeimd5", data.imeimd5)
            .apply()
    }
}
