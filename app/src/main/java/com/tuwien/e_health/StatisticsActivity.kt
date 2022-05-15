package com.tuwien.e_health

import android.content.ContentValues
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataReadRequest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit


data class Score(
    var name:String,
    var scoreAVG: Float,
    var scoreMIN: Float,
    var scoreMAX: Float,
)

class StatisticsActivity : AppCompatActivity() {

    private lateinit var lineChart: LineChart
    private var scoreList = ArrayList<Score>()
    //private val scoreListMIN = ArrayList<Score>()
    //private val scoreListMAX = ArrayList<Score>()
    private  var testCounter = 0
    private var howFarBackForwardDay = 0L
    private var howFarBackForwardWeek = 0L
    private var isWeekActive = false
    private var isDayActive = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_statistics)


        lineChart = findViewById(R.id.statisticsLineChart)
        initLineChart()
       /* findViewById<TextView>(R.id.tvTitle).apply {
            text = "Date:"
        }*/
        setDataToLineChart(true,howFarBackForwardDay,howFarBackForwardWeek)


        val btnLastDay = findViewById<Button>(R.id.btnLastDay)
        val btnLast7Day = findViewById<Button>(R.id.btnLast7Days)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(-android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_pressed)
            )
        val colorsActive = intArrayOf(
            Color.BLACK,
            Color.BLACK,
            Color.BLACK,
            Color.BLACK
            )
        val colorsNotActive = intArrayOf(
            R.color.purple_500,
            R.color.purple_500,
            R.color.purple_500,
            R.color.purple_500
            )

        val activeBTN = ColorStateList(states, colorsActive)
        val notActiveBTN = ColorStateList(states, colorsNotActive)


        btnLastDay.setOnClickListener{
            isDayActive = true
            isWeekActive = false
           /* findViewById<TextView>(R.id.tvTitle).apply {
                text = "Date:"
            }*/
            findViewById<TextView>(R.id.tvHours).apply {
                text = "TIME"
            }

            setDataToLineChart(true,howFarBackForwardDay,howFarBackForwardWeek)
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            AnimateLineChart()
        }


        btnLast7Day.setOnClickListener{
            isDayActive = false
            isWeekActive = true
           /* findViewById<TextView>(R.id.tvTitle).apply {
                text = "Week:"
            }*/
            findViewById<TextView>(R.id.tvHours).apply {
                text = ""
            }

            setDataToLineChart(false,howFarBackForwardDay,howFarBackForwardWeek)
            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            AnimateLineChart()
        }

        val btnGoBackInTime = findViewById(R.id.btnGoBackInTime) as Button
        btnGoBackInTime.setOnClickListener {
            if (isDayActive){
                howFarBackForwardDay += 1
                setDataToLineChart(true,howFarBackForwardDay,howFarBackForwardWeek)
                lineChart.notifyDataSetChanged()
                lineChart.invalidate()
                AnimateLineChart()
            }else if (isWeekActive){
                howFarBackForwardWeek += 1
                setDataToLineChart(false,howFarBackForwardDay,howFarBackForwardWeek)
                lineChart.notifyDataSetChanged()
                lineChart.invalidate()
                AnimateLineChart()
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
    }




        private fun AnimateLineChart() {
        lineChart.animateX(500, Easing.EaseInSine)
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
        lineChart.legend.isEnabled = true
        lineChart.setGridBackgroundColor(Color.BLACK)

        //remove description label
        lineChart.description.isEnabled = false

        lineChart.extraBottomOffset =5f
        lineChart.extraRightOffset=20f

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
            if (booleanDayWeek){
                getScoreList(true, howFarBackForwardDay, howFarBackForwardWeek)
                //actualSetDataToLineChart(true, howFarBackForwardDay, howFarBackForwardWeek)
            }else {
                getScoreList(false, howFarBackForwardDay, howFarBackForwardWeek)
                //actualSetDataToLineChart(false, howFarBackForwardDay, howFarBackForwardWeek)
            }
        }

    private fun actualSetDataToLineChart(booleanDayWeek: Boolean, howFarBackForwardDay: Long, howFarBackForwardWeek: Long){
        val entriesAVG: ArrayList<Entry> = ArrayList()
        val entriesMIN: ArrayList<Entry> = ArrayList()
        val entriesMAX: ArrayList<Entry> = ArrayList()


        if (booleanDayWeek){

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
            lineDataSetAVG.setDrawValues(false)
            lineDataSetAVG.setDrawCircles(false)
            lineDataSetAVG.setDrawCircleHole(false)
            lineDataSetAVG.lineWidth = 2f
            lineDataSetAVG.mode = LineDataSet.Mode.CUBIC_BEZIER
            lineDataSetAVG.color = ContextCompat.getColor(this, R.color.purple_700)
            lineDataSetAVG.valueTextColor = ContextCompat.getColor(this, R.color.purple_700)

            lineDataSetAVG.notifyDataSetChanged()
            Log.i("DatasetAVG:", lineDataSetAVG.toString())
            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(lineDataSetAVG)

            //dataSets.add(lineDataSetMAX)
            //dataSets.add(lineDataSetMIN)

            val data = LineData(dataSets)
            lineChart.data = data

            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            scoreList.clear()

        }else{

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

            lineDataSetAVG.setDrawValues(false)
            lineDataSetAVG.setDrawCircles(false)

            lineDataSetMIN.setDrawValues(false)
            lineDataSetMIN.setDrawCircles(false)

            lineDataSetMAX.setDrawValues(false)
            lineDataSetMAX.setDrawCircles(false)

            lineDataSetAVG.lineWidth = 2f
            lineDataSetAVG.valueTextSize = 15f
            lineDataSetAVG.mode = LineDataSet.Mode.CUBIC_BEZIER
            lineDataSetAVG.color = ContextCompat.getColor(this, R.color.purple_700)
            lineDataSetAVG.valueTextColor = ContextCompat.getColor(this, R.color.purple_700)

            lineDataSetMIN.lineWidth = 2f
            lineDataSetMIN.valueTextSize = 15f
            lineDataSetMIN.mode = LineDataSet.Mode.CUBIC_BEZIER
            lineDataSetMIN.color = ContextCompat.getColor(this, R.color.purple_700)
            lineDataSetMIN.valueTextColor = ContextCompat.getColor(this, R.color.purple_700)

            lineDataSetMAX.lineWidth = 2f
            lineDataSetMAX.valueTextSize = 15f
            lineDataSetMAX.mode = LineDataSet.Mode.CUBIC_BEZIER
            lineDataSetMAX.color = ContextCompat.getColor(this, R.color.purple_700)
            lineDataSetMAX.valueTextColor = ContextCompat.getColor(this, R.color.purple_700)

            lineDataSetAVG.notifyDataSetChanged()
            lineDataSetMAX.notifyDataSetChanged()
            lineDataSetMIN.notifyDataSetChanged()

            Log.i("DatasetAVG:", lineDataSetAVG.toString())

            val dataSets: ArrayList<ILineDataSet> = ArrayList()
            dataSets.add(lineDataSetAVG)
            dataSets.add(lineDataSetMAX)
            dataSets.add(lineDataSetMIN)

            val data = LineData(dataSets)

            lineChart.data = data
            lineChart.xAxis.valueFormatter = MyAxisFormatter()

            lineChart.notifyDataSetChanged()
            lineChart.invalidate()
            scoreList.clear()
        }
    }


     private fun getScoreList(booleanDayWeek: Boolean, howFarBackForwardDay : Long, howFarBackForwardWeek : Long){
         scoreList.clear()
         // data for 1 day
            if (booleanDayWeek) {
                var today = ZonedDateTime.now().with(LocalTime.of(6,0,0))
                today = today.minusDays(howFarBackForwardDay)
                val yesterday = today.minusDays(1)
                //Log.i("Today:", today.toString())
                //Log.i("Yesterday:", yesterday.toString())

                var formatter = DateTimeFormatter.ofPattern("dd.MMMM")
                var formattedDate = yesterday.format(formatter)
                findViewById<TextView>(R.id.tvDaysDate).apply {
                    text = formattedDate
                }
                readHeartRateData(TimeUnit.HOURS,today,yesterday,true)
            } else {
                //data for 1 week
                var today = ZonedDateTime.now().with(DayOfWeek.MONDAY).with(LocalTime.MIN)
                today = today.minusWeeks(howFarBackForwardWeek)
                val lastWeek = today.minusWeeks(1)
                //Log.i("Today:", today.toString())
                //Log.i("LastWeek:", lastWeek.toString())

                var formatter = DateTimeFormatter.ofPattern("dd.MMMM")
                var formattedToday = today.minusDays(1)
                var formattedTodayString = formattedToday.format(formatter)
                var formattedlastWeek = lastWeek.format(formatter)

                findViewById<TextView>(R.id.tvDaysDate).apply {
                    text = "$formattedlastWeek - $formattedTodayString"
                }
                readHeartRateData(TimeUnit.DAYS, today, lastWeek, false)
            }
    }

    private fun readHeartRateData(timeInterval: TimeUnit, endTime: ZonedDateTime, startTime: ZonedDateTime, booleanDayWeek: Boolean){
        //Log.i(ContentValues.TAG, "Range Start: $startTime")
        //Log.i(ContentValues.TAG, "Range End: $endTime")

        // create read request
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

        // do read request
        if (account != null) {
            testCounter = 0

            var bpmValues: MutableList<DataSet> = mutableListOf()
            val entriesAVG: ArrayList<Entry> = ArrayList()
            val entriesMIN: ArrayList<Entry> = ArrayList()
            val entriesMAX: ArrayList<Entry> = ArrayList()
            Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        // not every dataSet has dataPoint
                        for (dp in dataSet.dataPoints) {
                            //bpmValues.add(dataSet)
                            if (booleanDayWeek){
                                saveDatasetInArray(dataSet,true)
                            }else{
                                saveDatasetInArray(dataSet,false)
                            }
                        }
                    }
                    Log.i(ContentValues.TAG, "Scorelist: ${scoreList}")
                    if (booleanDayWeek){
                        for (i in scoreList.indices) {
                            val score = scoreList[i]
                            entriesAVG.add(Entry(i.toFloat(), score.scoreAVG))
                            entriesMIN.add(Entry(i.toFloat(), score.scoreMIN))
                            entriesMAX.add(Entry(i.toFloat(), score.scoreMAX))
                        }
                        lineChart.xAxis.valueFormatter = MyAxisFormatter()

                        val lineDataSetAVG = LineDataSet(entriesAVG,"Average")
                        val lineDataSetMIN = LineDataSet(entriesMIN,"Minimum")
                        val lineDataSetMAX = LineDataSet(entriesMAX,"Maximum")

                        lineDataSetAVG.valueTextSize = 12f
                        lineDataSetAVG.setDrawValues(false)
                        lineDataSetAVG.setDrawCircles(false)
                        lineDataSetAVG.setDrawCircleHole(false)
                        lineDataSetAVG.lineWidth = 2f
                        lineDataSetAVG.mode = LineDataSet.Mode.CUBIC_BEZIER
                        lineDataSetAVG.color = ContextCompat.getColor(this, R.color.purple_700)
                        lineDataSetAVG.valueTextColor = ContextCompat.getColor(this, R.color.purple_700)

                        lineDataSetAVG.notifyDataSetChanged()
                        Log.i("DatasetAVG:", lineDataSetAVG.toString())
                        val dataSets: ArrayList<ILineDataSet> = ArrayList()
                        dataSets.add(lineDataSetAVG)

                        //dataSets.add(lineDataSetMAX)
                        //dataSets.add(lineDataSetMIN)

                        val data = LineData(dataSets)
                        lineChart.data = data

                        lineChart.notifyDataSetChanged()
                        lineChart.invalidate()
                    }else{

                        for (i in scoreList.indices) {
                            val score = scoreList[i]
                            entriesAVG.add(Entry(i.toFloat(), score.scoreAVG))
                            entriesMIN.add(Entry(i.toFloat(), score.scoreMIN))
                            entriesMAX.add(Entry(i.toFloat(), score.scoreMAX))
                        }

                        val lineDataSetAVG = LineDataSet(entriesAVG,"Average")
                        val lineDataSetMIN = LineDataSet(entriesMIN,"Minimum")
                        val lineDataSetMAX = LineDataSet(entriesMAX,"Maximum")

                        lineDataSetAVG.setDrawValues(false)
                        lineDataSetAVG.setDrawCircles(false)

                        lineDataSetMIN.setDrawValues(false)
                        lineDataSetMIN.setDrawCircles(false)

                        lineDataSetMAX.setDrawValues(false)
                        lineDataSetMAX.setDrawCircles(false)

                        lineChart.xAxis.valueFormatter = MyAxisFormatter()

                        lineDataSetAVG.lineWidth = 2f
                        lineDataSetAVG.valueTextSize = 15f
                        lineDataSetAVG.mode = LineDataSet.Mode.CUBIC_BEZIER
                        lineDataSetAVG.color = ContextCompat.getColor(this, R.color.purple_700)
                        lineDataSetAVG.valueTextColor = ContextCompat.getColor(this, R.color.purple_700)

                        lineDataSetMIN.lineWidth = 2f
                        lineDataSetMIN.valueTextSize = 15f
                        lineDataSetMIN.mode = LineDataSet.Mode.CUBIC_BEZIER
                        lineDataSetMIN.color = ContextCompat.getColor(this, R.color.purple_200)
                        lineDataSetMIN.valueTextColor = ContextCompat.getColor(this, R.color.purple_200)

                        lineDataSetMAX.lineWidth = 2f
                        lineDataSetMAX.valueTextSize = 15f
                        lineDataSetMAX.mode = LineDataSet.Mode.CUBIC_BEZIER
                        lineDataSetMAX.color = ContextCompat.getColor(this, R.color.purple_200)
                        lineDataSetMAX.valueTextColor = ContextCompat.getColor(this, R.color.purple_200)

                        lineDataSetAVG.notifyDataSetChanged()
                        lineDataSetMAX.notifyDataSetChanged()
                        lineDataSetMIN.notifyDataSetChanged()

                        Log.i("DatasetAVG:", lineDataSetAVG.toString())

                        val dataSets: ArrayList<ILineDataSet> = ArrayList()
                        dataSets.add(lineDataSetAVG)
                        dataSets.add(lineDataSetMAX)
                        dataSets.add(lineDataSetMIN)

                        val data = LineData(dataSets)

                        lineChart.data = data
                        lineChart.xAxis.valueFormatter = MyAxisFormatter()

                        lineChart.notifyDataSetChanged()
                        lineChart.invalidate()
                        }
                    }
                .addOnFailureListener { e ->
                    Log.w(ContentValues.TAG, "There was a problem getting the heart rate.", e)
                }

        }

    }

    private fun saveDatasetInArray(dataSet: DataSet,booleanDayWeek: Boolean){
        // show important info of heart rate datapoint
        val entriesAVG: ArrayList<Entry> = ArrayList()
        val entriesMIN: ArrayList<Entry> = ArrayList()
        val entriesMAX: ArrayList<Entry> = ArrayList()

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
                var formatter = DateTimeFormatter.ofPattern("dd.MMMM")
                var time = Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault()).toLocalDateTime()
                var formattedTime = time.format(formatter)

                var tempScore = Score(formattedTime,0f,0f,0f)
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


