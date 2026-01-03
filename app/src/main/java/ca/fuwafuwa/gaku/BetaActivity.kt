package ca.fuwafuwa.gaku

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class BetaActivity : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_beta)

        findViewById<Button>(R.id.beta_button_start).setOnClickListener {
            finish()
        }

        val emailIntent = Intent(android.content.Intent.ACTION_SENDTO)
        emailIntent.type = "text/plain"
        emailIntent.data = Uri.parse("mailto:")
        emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf("carloosthuizen0@gmail.com"))
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Gaku Beta User - ${android.os.Build.BRAND + " " + android.os.Build.MODEL}")

        findViewById<Button>(R.id.beta_button_email).setOnClickListener {
            startActivity(Intent.createChooser(emailIntent, "Send e-mail using..."))
        }
    }
}




