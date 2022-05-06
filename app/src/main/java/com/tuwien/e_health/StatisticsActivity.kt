package com.tuwien.e_health

import android.content.ContentValues
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

data class Score(
    val name:String,
    val score: Int,
)

class StatisticsActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private var scoreList = ArrayList<Score>()
    private var testCounter = 0;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_statistics)

        lineChart = findViewById(R.id.statisticsLineChart)
        initLineChart()
        setDataToLineChart(false)

        val switchDayWeek: SwitchCompat = findViewById(R.id.switchDayWeek) as SwitchCompat

        switchDayWeek.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                initLineChart()
                setDataToLineChart(true)
            } else {
                setDataToLineChart(false)
            }
          }
        }



    private fun initLineChart() {

//        hide grid lines
        lineChart.axisLeft.setDrawGridLines(false)
        val xAxis: XAxis = lineChart.xAxis
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)

        //remove right y-axis
        lineChart.axisRight.isEnabled = false

        //remove legend
        lineChart.legend.isEnabled = false


        //remove description label
        lineChart.description.isEnabled = false


        //add animation
        lineChart.animateX(1000, Easing.EaseInSine)

        // to draw label on xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM_INSIDE
        xAxis.valueFormatter = MyAxisFormatter()
        xAxis.setDrawLabels(true)
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = +0f

    }


    inner class MyAxisFormatter : IndexAxisValueFormatter() {

        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            val index = value.toInt()
            return if (index < scoreList.size) {
                scoreList[index].name
            } else {
                ""
            }
        }
    }

    private fun setDataToLineChart(booleanDayWeek: Boolean) {
        //now draw bar chart with dynamic data
        val entries: ArrayList<Entry> = ArrayList()

        scoreList.clear()
        if (booleanDayWeek){
            scoreList = getScoreList(true)
        }else{
            scoreList = getScoreList(false)
        }

        //you can replace this data object with  your custom object
        for (i in scoreList.indices) {
            val score = scoreList[i]
            entries.add(Entry(i.toFloat(), score.score.toFloat()))
        }

        val lineDataSet = LineDataSet(entries, "")

        val data = LineData(lineDataSet)
        lineChart.data = data

        lineChart.invalidate()
    }

    // simulate api call
    // we are initialising it directly
    private fun getScoreList(booleanDayWeek: Boolean): ArrayList<Score> {

        //val endTime =  LocalDateTime.now().atZone(ZoneId.systemDefault())

        // data for 1 day
        var endTime =
            LocalDate.of(2022, 4, 30).atTime(23, 59, 59).atZone(ZoneId.systemDefault())
        val startTime = endTime.minusDays(1)

        /*
        // data for 1 week
        var endTime = LocalDate.of(2022, 4, 29).atTime(23, 59, 59).atZone(ZoneId.systemDefault())
        var startTime = endTime.minusWeeks(1)
        */
        readHeartRateData(TimeUnit.DAYS, endTime, startTime)

        if (booleanDayWeek){
            scoreList.add(Score("Test", 56))
            scoreList.add(Score("Rey", 75))
            scoreList.add(Score("Steve", 85))
            scoreList.add(Score("Kevin", 45))
            scoreList.add(Score("Jeff", 63))
        }else{
            scoreList.add(Score("John", 20))
            scoreList.add(Score("Rey", 30))
            scoreList.add(Score("Steve", 40))
            scoreList.add(Score("Kevin", 50))
            scoreList.add(Score("Jeff", 63))
        }


        return scoreList
    }

    private fun readHeartRateData(timeInterval: TimeUnit, endTime: ZonedDateTime, startTime: ZonedDateTime) {
        // extract heart rate for given time period

        Log.i(ContentValues.TAG, "Range Start: $startTime")
        Log.i(ContentValues.TAG, "Range End: $endTime")

        val readRequest =
            DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM)
                //.aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, timeInterval)
                .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(),
                    TimeUnit.SECONDS
                )
                .build()


        val account = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            testCounter = 0
            Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        showDataSet(dataSet)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(ContentValues.TAG, "There was a problem getting the heart rate.", e)
                }

        }
    }

    private fun showDataSet(dataSet: DataSet) {
        // show important info of heart rate datapoint

        testCounter++
        for (dp in dataSet.dataPoints) {
            Log.i("History", "Data point:" + testCounter)
            Log.i("History", "\tType: " + dp.dataType.name)
            Log.i("History",
                "\tStart: " + Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                    .toLocalDateTime().toString()
            )
            Log.i("History",
                "\tEnd: " + Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                    .toLocalDateTime().toString()
            )
            for (field in dp.dataType.fields) {
                // bpm values saved in "fields" of datapoint
                // loop over avg-bpm, max-bpm, min-bpm
                Log.i(
                    "History", "\tField: " + field.name +
                            " Value: " + dp.getValue(field)
                )
            }
        }
    }
}