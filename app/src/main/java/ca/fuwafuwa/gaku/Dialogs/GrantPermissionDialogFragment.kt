package ca.fuwafuwa.gaku.Dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import ca.fuwafuwa.gaku.GAKU_PREF_FILE
import ca.fuwafuwa.gaku.GAKU_PREF_FIRST_LAUNCH
import ca.fuwafuwa.gaku.MainActivity

class GrantPermissionDialogFragment : DialogFragment()
{
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog
    {
        return activity?.let {

            val builder = AlertDialog.Builder(it)

            builder.setTitle("Grant Gaku Permissions")
                    .setMessage("Gaku uses optical character recognition (OCR) to detect text from images and works by automatically taking screenshots of your screen when active. After granting permissions, please restart Gaku.\n\nGaku works completely offline and WILL NEVER transmit ANY user data encountered during usage.")
                    .setPositiveButton("GRANT")
                    {
                        _, _ ->
                        run {
                            val prefs = context!!.getSharedPreferences(GAKU_PREF_FILE, Context.MODE_PRIVATE)
                            prefs.edit().putBoolean(GAKU_PREF_FIRST_LAUNCH, false).apply()

                            startActivity(Intent(activity, MainActivity::class.java))
                            (activity as FragmentActivity).finish()
                        }
                    }
                    .setNegativeButton("CANCEL")
                    {
                        _, _ ->
                        run {
                        }
                    }

            builder.create()

        } ?: throw IllegalStateException("Activity cannot be null")
    }
}



