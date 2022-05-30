package com.tuwien.e_health

import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.btnSportGame
import kotlinx.android.synthetic.main.activity_sport_game_timer.*


class SportGameTimer : AppCompatActivity() {

    private var hourVar : Int = 0
    private var minuteVar : Int = 30
    private val tag = "[SportGameTimer]"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_sport_game_timer)
        timePicker.setIs24HourView(true)
        timePicker.hour = 0
        timePicker.minute = 30

        onClickTime()

        // Navigation to Settings
        btnSportGame.setOnClickListener {
            val intent = Intent(this, SportsGameActivity::class.java)
            val time = (hourVar *3600000 + minuteVar *60 * 1000).toLong()
            intent.putExtra("data", time)
            Log.i(tag, "Starting Game-Activity with time $time ..")
            startActivity(intent)
        }
    }


    private fun onClickTime() {
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        timePicker.setOnTimeChangedListener { _, hour, minute -> val hour = hour
                hourVar = hour
                minuteVar = minute
            }
    }

}