package dev.stationcall.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle

const val YandexAppDownloadUrl = "https://yandex.ru/yandexapp/"
const val YandexWidgetHelpUrl = "https://alice.yandex.ru/support/ru/smart-home/widgets"
const val CallStationShortcutAction = "dev.stationcall.app.CALL_STATION"
const val ShortcutFailureExtra = "dev.stationcall.app.SHORTCUT_FAILURE"

private val deviceIdPattern = Regex("[A-Za-z0-9._~-]+")

fun isValidStationDeviceId(value: String): Boolean = deviceIdPattern.matches(value.trim())

data class Station(val name: String, val deviceId: String)

class StationRepository(context: Context) {
    private val preferences = context.getSharedPreferences("station", Context.MODE_PRIVATE)

    fun load(): Station? {
        val name = preferences.getString("name", null) ?: return null
        val deviceId = preferences.getString("device_id", null) ?: return null
        if (name.isBlank() || !isValidStationDeviceId(deviceId)) return null
        return Station(name, deviceId)
    }

    fun save(station: Station) {
        require(station.name.isNotBlank() && isValidStationDeviceId(station.deviceId))
        preferences.edit()
            .putString("name", station.name.trim())
            .putString("device_id", station.deviceId.trim())
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}

enum class CallHandoffResult {
    OPENED,
    YANDEX_UNAVAILABLE,
    INVALID_DEVICE_ID,
    STATION_NOT_CONFIGURED,
    FAILED,
}

object StationCall {
    private val yandexPackages = listOf(
        "com.yandex.searchapp",
        "com.yandex.searchapp.beta",
        "ru.yandex.searchplugin",
    )

    fun launch(context: Context, deviceId: String): CallHandoffResult {
        if (!isValidStationDeviceId(deviceId)) return CallHandoffResult.INVALID_DEVICE_ID

        // Preserve the nested URI format used by the community-documented Android handoff.
        val link = Uri.parse(
            "ya-search-app-open://path?uri=messenger://call/create/private?device_id=${deviceId.trim()}",
        )
        val intent = Intent(Intent.ACTION_VIEW, link).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        for (packageName in yandexPackages) {
            try {
                context.startActivity(intent.setPackage(packageName))
                return CallHandoffResult.OPENED
            } catch (_: ActivityNotFoundException) {
                // The installed edition may use another package name.
            } catch (_: SecurityException) {
                return CallHandoffResult.FAILED
            }
        }
        return CallHandoffResult.YANDEX_UNAVAILABLE
    }
}

object StationShortcut {
    fun isSupported(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    fun requestPin(context: Context): Boolean {
        val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcuts.isRequestPinShortcutSupported) return false

        val intent = Intent(context, StationShortcutActivity::class.java).setAction(CallStationShortcutAction)
        val shortcut = ShortcutInfo.Builder(context, "call_station")
            .setShortLabel("Позвонить")
            .setLongLabel("Позвонить на Станцию")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_station_launcher))
            .setIntent(intent)
            .build()

        return try {
            shortcuts.requestPinShortcut(shortcut, null)
        } catch (_: IllegalStateException) {
            false
        }
    }
}

class StationShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null && intent.action == CallStationShortcutAction) {
            val station = StationRepository(this).load()
            val result = station?.let { StationCall.launch(this, it.deviceId) }
                ?: CallHandoffResult.STATION_NOT_CONFIGURED

            if (result != CallHandoffResult.OPENED) {
                val main = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(ShortcutFailureExtra, result.name)
                }
                startActivity(main)
            }
        }
        finish()
    }
}
