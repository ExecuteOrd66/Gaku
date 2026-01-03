package ca.fuwafuwa.gaku

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ca.fuwafuwa.gaku.Database.User.UserDatabaseHelper

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

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
