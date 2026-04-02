package com.rajvir.FuturesTracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREF_ALARM_ENABLED = "alarm_enabled"
        const val PREF_ALARM_TRIGGERED = "alarm_triggered"
        const val PREF_ALARM_MODE = "alarm_mode"
        const val PREF_ALARM_TARGET_PRICE = "alarm_target_price"
        const val PREF_ALARM_PERCENT = "alarm_percent"
        const val PREF_ALARM_BASE_PRICE = "alarm_base_price"
        const val PREF_ALARM_SYMBOL = "alarm_symbol"
        const val PREF_ALARM_DIRECTION = "alarm_direction"
        const val PREF_ALARM_REPEAT = "alarm_repeat"
        const val PREF_ALARM_COOLDOWN_MIN = "alarm_cooldown_min"
        const val PREF_ALARM_LAST_TRIGGER_AT = "alarm_last_trigger_at"
        const val PREF_HOME_AUTO_REFRESH = "home_auto_refresh"
        const val PREF_HOME_REFRESH_INTERVAL = "home_refresh_interval"
        const val PREF_HOME_SORT_MODE = "home_sort_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dashboardIntent = Intent(this, DashboardActivity::class.java).apply {
            intent.extras?.let { putExtras(it) }
        }
        startActivity(dashboardIntent)
        finish()
    }
}
