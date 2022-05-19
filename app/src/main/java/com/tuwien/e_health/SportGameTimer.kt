package com.tuwien.e_health

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.btnSportGame
import kotlinx.android.synthetic.main.activity_sport_game_timer.*
import java.util.*


class SportGameTimer : AppCompatActivity() {

    private var hourVar : Int = 0
    private var minuteVar : Int = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_sport_game_timer)
        timePicker.setIs24HourView(true);
        timePicker.hour = 0
        timePicker.minute = 30

        val msg = "$hourVar : $minuteVar"
        tvEnteredTime.text = msg

        OnClickTime()

        // Navigation to Settings
        btnSportGame.setOnClickListener {
            val Intent = Intent(this, SportsGameActivity::class.java)
            startActivity(Intent)
        }
    }


    private fun OnClickTime() {
        val textView = findViewById<TextView>(R.id.tvEnteredTime)
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        timePicker.setOnTimeChangedListener { _, hour, minute -> var hour = hour

            if (textView != null) {
                hourVar = hour
                minuteVar = minute
                val hour = if (hour < 10) "0" + hour else hour
                val min = if (minute < 10) "0" + minute else minute
                // display format of time
                val msg = "$hour : $min"
                Log.i("TimeInStorage", "$hourVar : $minuteVar")
                textView.text = msg
                textView.visibility = ViewGroup.VISIBLE
            }
        }
    }

}