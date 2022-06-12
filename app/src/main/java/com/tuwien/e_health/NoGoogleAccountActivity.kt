package com.tuwien.e_health

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_no_google_account.*

class NoGoogleAccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_no_google_account)

        // opens popup overlay for button
        btnUIdisabled.setOnClickListener{
            showPopup()
        }
    }

    override fun onBackPressed() {
        return
    }

    /*

        This activity only shows, when there is no google account on the smartphone. Due to some
        bugs with firebase the user should create a google account before starting the app for the
        first time. Will also pop up, if google account was only created in last ~5-10 min due to
        synchronization delays.

        Will pretty much never be the case anyway (safety first)

     */

    private fun showPopup() {

        val builder = AlertDialog.Builder(this)
        builder.setTitle(" ")
        builder.setPositiveButton("Ok", null)
        var message = "If you have disabled the sign in prompt too many times, it might be on a cooldown, not letting you sign in at the moment. \n" +
                "You can fix this by clearing your Google Play services' app storage in your device settings. " +
                "After you have done that, restart this app. " +
                "You might have to wait a few minutes again for it to work."

        builder.setMessage(message)

        val alertDialog = builder.create()
        alertDialog.show()

        val okButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        with(okButton) {
            setPadding(0, 0, 20, 0)
            setTextColor(Color.BLACK)
        }
    }

}