package com.tuwien.e_health

import android.content.ContentValues
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import java.time.*
import java.util.concurrent.TimeUnit


data class Score(
    var name:String,
    var scoreAVG: Float,
    var scoreMIN: Float,
    var scoreMAX: Float,
)

class StatisticsActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private val scoreList = ArrayList<Score>()
    //private val scoreListMIN = ArrayList<Score>()
    //private val scoreListMAX = ArrayList<Score>()
    private  var testCounter = 0
    private var howFarBackForwardDay = 0L
    private var howFarBackForwardWeek = 0L
    private var isWeekActive = false
    private var isDayActive = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_statistics)


        lineChart = findViewById(R.id.statisticsLineChart)
        initLineChart()

        /*val switchDayWeek: SwitchCompat = findViewById(R.id.switchDayWeek) as SwitchCompat


        switchDayWeek.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                setDataToLineChart(true)
            } else {
                setDataToLineChart(false)
            }
        }*/
        val btnLastDay = findViewById(R.id.btnLastDay) as Button
        btnLastDay.setOnClickListener{
            isDayActive = true
            isWeekActive = false
            setDataToLineChart(true,howFarBackForwardDay,howFarBackForwardWeek)
            scoreList.clear()
        }

        val btnLast7Day = findViewById(R.id.btnLast7Days) as Button
        btnLast7Day.setOnClickListener{
            isDayActive = false
            isWeekActive = true
            setDataToLineChart(false,howFarBackForwardDay,howFarBackForwardWeek)
            scoreList.clear()
        }

        val btnGoBackInTime = findViewById(R.id.btnGoBackInTime) as Button
        btnGoBackInTime.setOnClickListener {
            if (isDayActive){
                howFarBackForwardDay += 1
                setDataToLineChart(true,howFarBackForwardDay,howFarBackForwardWeek)
            }else if (isWeekActive){
                howFarBackForwardWeek += 1
                setDataToLineChart(false,howFarBackForwardDay,howFarBackForwardWeek)
            }
        }

        val btnGoForwardInTime = findViewById(R.id.btnGoForwardInTime) as Button
        btnGoForwardInTime.setOnClickListener {
            if (isDayActive){
                howFarBackForwardDay -= 1
                setDataToLineChart(true,howFarBackForwardDay,howFarBackForwardWeek)
            }else if (isWeekActive){
                howFarBackForwardWeek -= 1
                setDataToLineChart(false,howFarBackForwardDay,howFarBackForwardWeek)
            }
        }
/*
        // get reference to button
        val btnReload = findViewById(R.id.btnReload) as Button
        // set on-click listener
        btnReload.setOnClickListener {
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            scoreList.clear()
        }
*/
    }




    private fun initLineChart() {

//        hide grid lines
        lineChart.axisLeft.setDrawGridLines(true)
        val xAxis: XAxis = lineChart.xAxis
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textSize = 12f

        //remove right y-axis
        lineChart.axisRight.isEnabled = false

        //val backgroundColor = R.color.purple_200.toInt()
        //lineChart.setBackgroundColor(backgroundColor)
        //remove legend
        lineChart.legend.isEnabled = false
        lineChart.setGridBackgroundColor(Color.BLACK)

        //remove description label
        lineChart.description.isEnabled = false

        //add animation
        lineChart.animateX(1000, Easing.EaseInSine)

        // to draw label on xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setLabelCount(12)

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

    private fun setDataToLineChart(booleanDayWeek: Boolean, howFarBackForwardDay: Long, howFarBackForwardWeek: Long)  {
        //now draw bar chart with dynamic data
        val entriesAVG: ArrayList<Entry> = ArrayList()
        val entriesMIN: ArrayList<Entry> = ArrayList()
        val entriesMAX: ArrayList<Entry> = ArrayList()


        if (booleanDayWeek){
            getScoreList(true, howFarBackForwardDay, howFarBackForwardWeek)

            for (i in scoreList.indices) {
                val score = scoreList[i]
                entriesAVG.add(Entry(i.toFloat(), score.scoreAVG))
                entriesMIN.add(Entry(i.toFloat(), score.scoreMIN))
                entriesMAX.add(Entry(i.toFloat(), score.scoreMAX))
            }

            val lineDataSetAVG = LineDataSet(entriesAVG,"Average")
            val lineDataSetMIN = LineDataSet(entriesMIN,"Minimum")
            val lineDataSetMAX = LineDataSet(entriesMAX,"Maximum")


            lineDataSetAVG.valueTextSize = 12f
            lineDataSetAVG.setDrawFilled(true)
            lineDataSetAVG.setDrawValues(false)
            lineDataSetAVG.setDrawCircles(false)
            lineDataSetAVG.setDrawCircleHole(false)

            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(lineDataSetAVG)

            //dataSets.add(lineDataSetMAX)
            //dataSets.add(lineDataSetMIN)

            val data = LineData(dataSets)
            lineChart.data = data

            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            //scoreList.clear()

        }else{
            getScoreList(false, howFarBackForwardDay,howFarBackForwardWeek)

            for (i in scoreList.indices) {
                val score = scoreList[i]
                entriesAVG.add(Entry(i.toFloat(), score.scoreAVG))
                entriesMIN.add(Entry(i.toFloat(), score.scoreMIN))
                entriesMAX.add(Entry(i.toFloat(), score.scoreMAX))
            }
/*
            for (i in scoreListMIN.indices) {
                val score = scoreListMIN[i]
                entriesMIN.add(Entry(i.toFloat(), score.score))
            }

            for (i in scoreListMAX.indices) {
                val score = scoreListMAX[i]
                entriesMAX.add(Entry(i.toFloat(), score.score))
            }*/


            val lineDataSetAVG = LineDataSet(entriesAVG,"Average")
            val lineDataSetMIN = LineDataSet(entriesMIN,"Minimum")
            val lineDataSetMAX = LineDataSet(entriesMAX,"Maximum")

            lineDataSetAVG.color = R.color.purple_200

            lineDataSetMIN.color = R.color.purple_500

            lineDataSetMAX.color = R.color.purple_700

            lineDataSetAVG.setDrawFilled(true)
            lineDataSetAVG.fillColor = R.color.purple_500

            lineDataSetAVG.fillColor = R.color.purple_200
            lineDataSetMIN.setDrawFilled(true)

            lineDataSetMIN.setCircleColor(R.color.purple_700)

            lineDataSetAVG.setDrawFilled(true)
            lineDataSetAVG.setDrawValues(false)
            lineDataSetAVG.setDrawCircles(false)

            lineDataSetMIN.setDrawFilled(true)
            lineDataSetMIN.setDrawValues(false)
            lineDataSetMIN.setDrawCircles(false)

            lineDataSetMAX.setDrawFilled(true)
            lineDataSetMAX.setDrawValues(false)
            lineDataSetMAX.setDrawCircles(false)



            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(lineDataSetAVG)
            dataSets.add(lineDataSetMAX)
            dataSets.add(lineDataSetMIN)

            val data = LineData(dataSets)

            lineChart.data = data
            lineChart.xAxis.valueFormatter = MyAxisFormatter()

            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            //scoreList.clear()
        }
    }


    private fun getScoreList(booleanDayWeek: Boolean, howFarBackForwardDay : Long, howFarBackForwardWeek : Long) {

            // data for 1 day
            if (booleanDayWeek) {
                var today = ZonedDateTime.now().with(LocalTime.of(6,0,0))
                today = today.minusDays(howFarBackForwardDay)
                val yesterday = today.minusDays(1)
                Log.i("Today:", today.toString())
                Log.i("Yesterday:", yesterday.toString())
                readHeartRateData(TimeUnit.HOURS,today,yesterday,true)
            } else {
                //data for 1 week
                var today = ZonedDateTime.now().with(DayOfWeek.MONDAY).with(LocalTime.MIN)
                today = today.minusWeeks(howFarBackForwardWeek)
                val lastWeek = today.minusWeeks(1)
                Log.i("Today:", today.toString())
                Log.i("LastWeek:", lastWeek.toString())
                readHeartRateData(TimeUnit.DAYS,today,lastWeek,false)
            }

    }

    private fun readHeartRateData(timeInterval: TimeUnit, endTime: ZonedDateTime, startTime: ZonedDateTime, booleanDayWeek: Boolean) {
        // extract heart rate for given time period

            Log.i(ContentValues.TAG, "Range Start: $startTime")
            Log.i(ContentValues.TAG, "Range End: $endTime")

            val readRequest =
                DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_HEART_RATE_BPM)
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
                            if (booleanDayWeek){
                                saveDatasetInArray(dataSet,true)
                            }else{
                                saveDatasetInArray(dataSet,false)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(ContentValues.TAG, "There was a problem getting the heart rate.", e)
                    }
            }
    }

    private fun saveDatasetInArray(dataSet: DataSet,booleanDayWeek: Boolean){
        // show important info of heart rate datapoint

        if (booleanDayWeek){
            testCounter++
            for (dp in dataSet.dataPoints) {
                //Log.i("History", "Data point:" + testCounter)
                //Log.i("History", "\tType: " + dp.dataType.name)
               // Log.i("History",
                 // "\tStart: " + Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                   // .toLocalDateTime().toString()
                //)
                //Log.i("History",
                //  "\tEnd: " + Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                //    .toLocalDateTime().toString()
                //)
                var tempScore = Score(Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().substringAfter("T").substringBefore(":"),0f,0f,0f)
                for (field in dp.dataType.fields) {
                    // bpm values saved in "fields" of datapoint
                    // loop over avg-bpm, max-bpm, min-bpm
                    //  Log.i(
                    //    "History", "\tField: " + field.name +
                    //          " Value: " + dp.getValue(field)
                    //)
                    if(field.name == "average") {
                        tempScore.scoreAVG = dp.getValue(field).asFloat()
                        //scoreList.add(Score(testCounter.toString(), dp.getValue(field).asFloat(),0f,0f))
                    }
                    if(field.name == "min") {
                        //scoreListMIN.add(Score(testCounter.toString(), dp.getValue(field).asFloat()))
                        tempScore.scoreMIN = dp.getValue(field).asFloat()

                    }
                    if(field.name == "max") {
                        tempScore.scoreMAX = dp.getValue(field).asFloat()
                        //scoreListMAX.add(Score(testCounter.toString(), dp.getValue(field).asFloat()))
                    }
                }
                scoreList.add(tempScore)
            }
        }else{
            testCounter++
            for (dp in dataSet.dataPoints) {
                //Log.i("History", "Data point:" + testCounter)
                //Log.i("History", "\tType: " + dp.dataType.name)
                //Log.i("History",
                  //"\tStart: " + Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                    //.toLocalDateTime().toString()
                //)
                //Log.i("History",
                //  "\tEnd: " + Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                //    .toLocalDateTime().toString()
                //)

                var tempScore = Score(Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().substringBefore("T"),0f,0f,0f)
                for (field in dp.dataType.fields) {
                    // bpm values saved in "fields" of datapoint
                    // loop over avg-bpm, max-bpm, min-bpm
                     // Log.i(
                       // "History", "\tField: " + field.name +
                         //   " Value: " + dp.getValue(field)
                    //)
                    if(field.name == "average") {
                        tempScore.scoreAVG = dp.getValue(field).asFloat()
                        //scoreListAVG.add(Score(testCounter.toString(), dp.getValue(field).asFloat()))
                    }
                    if(field.name == "min") {
                        //scoreListMIN.add(Score(testCounter.toString(), dp.getValue(field).asFloat()))
                        tempScore.scoreMIN = dp.getValue(field).asFloat()

                    }
                    if(field.name == "max") {
                        tempScore.scoreMAX = dp.getValue(field).asFloat()
                        //scoreListMAX.add(Score(testCounter.toString(), dp.getValue(field).asFloat()))
                    }

                }
                scoreList.add(tempScore)
            }
        }

        }
    }
