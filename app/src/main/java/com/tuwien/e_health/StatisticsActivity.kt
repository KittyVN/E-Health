package com.tuwien.e_health

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class StatisticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        val statisticsBarChartButton = findViewById<Button>(R.id.buttonBarChart)
        statisticsBarChartButton.setOnClickListener {
            val Intent = Intent(this,StatisticsLineChartActivity::class.java)
            startActivity(Intent)
        }
    }
}