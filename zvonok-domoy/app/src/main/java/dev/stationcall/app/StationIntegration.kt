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

const val SmartHomeDownloadUrl = "https://ya.cc/iot_app"
const val YandexWidgetHelpUrl = "https://alice.yandex.ru/support/ru/smart-home/widgets"
const val CallStationShortcutAction = "dev.stationcall.app.CALL_STATION"
const val ShortcutFailureExtra = "dev.stationcall.app.SHORTCUT_FAILURE"

private val deviceIdPattern = Regex("[A-Za-z0-9._~-]+")

fun isValidStationDeviceId(value: String): Boolean = deviceIdPattern.matches(value.trim())

data class Station(val name: String, val deviceId: String?)

class StationRepository(context: Context) {
    private val preferences = context.getSharedPreferences("station", Context.MODE_PRIVATE)

    fun load(): Station? {
        val name = preferences.getString("name", null) ?: return null
        val deviceId = preferences.getString("device_id", null)?.takeIf(::isValidStationDeviceId)
        if (name.isBlank()) return null
        return Station(name, deviceId)
    }

    fun save(station: Station) {
        require(station.name.isNotBlank())
        require(station.deviceId == null || isValidStationDeviceId(station.deviceId))
        preferences.edit()
            .putString("name", station.name.trim())
            .putString("device_id", station.deviceId?.trim())
            .apply()
    }
}

enum class CallRoute { DIRECT, SMART_HOME, UNAVAILABLE }

enum class CallHandoffResult {
    OPENED,
    SMART_HOME_UNAVAILABLE,
    INVALID_DEVICE_ID,
    STATION_NOT_CONFIGURED,
    FAILED,
}

object StationCall {
    private const val smartHomePackage = "com.yandex.iot"
    private const val sampleDeviceId = "station"
    private val directCallPackages = listOf(
        "com.yandex.searchapp",
        "com.yandex.searchapp.beta",
        "ru.yandex.searchplugin",
    )

    fun route(context: Context, deviceId: String?): CallRoute = when {
        directCallIntents(deviceId ?: sampleDeviceId).any {
            it.resolveActivity(context.packageManager) != null
        } -> CallRoute.DIRECT
        smartHomeIntent(context) != null -> CallRoute.SMART_HOME
        else -> CallRoute.UNAVAILABLE
    }

    fun launch(context: Context, deviceId: String?): CallHandoffResult {
        if (deviceId != null && !isValidStationDeviceId(deviceId)) {
            return CallHandoffResult.INVALID_DEVICE_ID
        }

        if (deviceId != null && directCallIntents(deviceId).any { tryOpen(context, it) }) {
            return CallHandoffResult.OPENED
        }

        val home = smartHomeIntent(context)
        if (home != null) {
            return if (tryOpen(context, home)) CallHandoffResult.OPENED else CallHandoffResult.FAILED
        }
        return when (route(context, deviceId)) {
            CallRoute.DIRECT -> if (deviceId == null) {
                CallHandoffResult.STATION_NOT_CONFIGURED
            } else {
                CallHandoffResult.FAILED
            }
            CallRoute.SMART_HOME -> CallHandoffResult.FAILED
            CallRoute.UNAVAILABLE -> CallHandoffResult.SMART_HOME_UNAVAILABLE
        }
    }

    private fun smartHomeIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(smartHomePackage)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun directCallIntents(deviceId: String): List<Intent> {
        // Keep the legacy link limited to the Yandex search apps that may handle it.
        val link = Uri.parse(
            "ya-search-app-open://path?uri=messenger://call/create/private?device_id=${deviceId.trim()}",
        )
        return directCallPackages.map { packageName ->
            Intent(Intent.ACTION_VIEW, link)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(packageName)
        }
    }

    private fun tryOpen(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
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
            val result = StationCall.launch(this, station?.deviceId)

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
