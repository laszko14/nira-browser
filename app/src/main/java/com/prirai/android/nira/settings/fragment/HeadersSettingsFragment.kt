package com.prirai.android.nira.settings.fragment

import android.content.Intent
import android.os.Bundle
import com.prirai.android.nira.R
import com.prirai.android.nira.addons.AddonsActivity

class SettingsFragment : BaseSettingsFragment() {
    private val testLabLauncher=registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {result->
        if(result.resultCode==android.app.Activity.RESULT_OK)activity?.finish()
    }
    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_headers)
        
        findPreference<androidx.preference.Preference>("zavro_testlab")?.setOnPreferenceClickListener {
            testLabLauncher.launch(Intent(requireContext(),com.prirai.android.nira.diagnostics.testlab.TestLabActivity::class.java))
            true
        }
        // Wire Add-on settings to open AddonsActivity
        findPreference<androidx.preference.Preference>("addons_settings")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), AddonsActivity::class.java)
            startActivity(intent)
            true
        }
    }
}