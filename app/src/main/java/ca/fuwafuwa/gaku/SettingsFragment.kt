package ca.fuwafuwa.gaku

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ca.fuwafuwa.gaku.legacy.user.UserDatabaseHelper

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // New Preference to open Dictionary Manager
        // Fix: Use requireContext() instead of context
        val dictPref = Preference(requireContext())
        dictPref.key = "manage_dictionaries"
        dictPref.title = "Manage Dictionaries (Yomitan)"
        dictPref.summary = "Import and remove offline dictionaries"
        dictPref.order = 0
        dictPref.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), DictionaryActivity::class.java))
            true
        }
        preferenceScreen.addPreference(dictPref)

        findPreference<Preference>("db_reinit")?.setOnPreferenceClickListener {
            resetGakuDatabases(requireContext())
            Toast.makeText(context, "Databases re-initialized", Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("db_clear_user")?.setOnPreferenceClickListener {
            try {
                val dbHelper = UserDatabaseHelper.instance(requireContext())
                dbHelper.userWordDao.queryForAll().forEach { dbHelper.userWordDao.delete(it) }
                Toast.makeText(context, "User progress cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Clear failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
}