package com.torbox // Changed package name

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.torbox.settings.SettingsFragment // Import the SettingsFragment

@CloudstreamPlugin
class TorboxPlugin: Plugin() { // Changed class name
    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity

        // All providers should be added in this manner
        registerMainAPI(TorboxProvider(this)) // Changed provider and passing plugin instance

        // Register settings
        openSettings = {
            activity?.let { act ->
                SettingsFragment(this).show(act.supportFragmentManager, "TorboxSettingsFragment")
            }
        }
    }
}
