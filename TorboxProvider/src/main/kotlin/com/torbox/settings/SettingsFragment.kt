package com.torbox.settings // Updated package name

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment // Changed from BottomSheetDialogFragment for wider compatibility if needed, can revert
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.torbox.TorboxPlugin // Updated import
import com.torbox.R // Import R for resources

// Using DialogFragment, but BottomSheetDialogFragment is also fine
class SettingsFragment(
    private val plugin: TorboxPlugin, // Use TorboxPlugin
) : DialogFragment() { // Changed from BottomSheetDialogFragment

    private lateinit var sharedPref: SharedPreferences

    // Key for storing the API token in SharedPreferences
    companion object {
        const val TORBOX_API_KEY = "torbox_api_key"
        const val PREFS_FILE = "TorboxSettings"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // Ensure context is available for SharedPreferences and layout inflation
        val context = plugin.activity ?: return null // Or handle error appropriately
        sharedPref = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

        // Inflate the layout
        return inflater.inflate(R.layout.settings_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tokenInput = view.findViewById<EditText>(R.id.tokenInput)
        val saveButton = view.findViewById<Button>(R.id.addButton) // Renamed from addButton to saveButton for clarity
        val resetButton = view.findViewById<Button>(R.id.resetButton)

        // Load saved token
        val savedToken = sharedPref.getString(TORBOX_API_KEY, null)
        if (!savedToken.isNullOrEmpty()) {
            tokenInput.setText(savedToken)
        }

        saveButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isNotEmpty()) {
                sharedPref.edit().apply {
                    putString(TORBOX_API_KEY, token)
                    apply()
                }
                showToast(activity, "API Key saved successfully.")
                dismiss()
            } else {
                showToast(activity, "Please enter a valid API Key.")
            }
        }

        resetButton.setOnClickListener {
            sharedPref.edit().apply {
                remove(TORBOX_API_KEY)
                apply()
            }
            tokenInput.setText("") // Clear the input field
            showToast(activity, "API Key reset successfully.")
            dismiss()
        }
    }
}
