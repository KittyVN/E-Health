package com.tuwien.e_health


import android.Manifest
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import java.time.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val fitnessOptions: GoogleSignInOptionsExtension = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_WORKOUT_EXERCISE, FitnessOptions.ACCESS_READ)
        .build()

    private val RC_SIGNIN = 0
    private val RC_PERMISSION = 1
    private var testCounter = 0
    private var average6hHeartRate = 0.0;

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       supportActionBar?.hide()
       this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
           WindowManager.LayoutParams.FLAG_FULLSCREEN);
       setContentView(R.layout.activity_main)


        // TODO: Just for testing, remove later
        btnGoogleSteps.setOnClickListener {
            // data for 1 day
            //var endTime = LocalDate.of(2022, 5, 7).atTime(23, 59, 59).atZone(ZoneId.systemDefault())
            //val startTime = endTime.minusHours(6)
            //readHeartRateData(TimeUnit.HOURS, endTime, startTime)
            read6hActivities()
        }

        // checks for logged account on startup, if not account, login
        if(GoogleSignIn.getLastSignedInAccount(this) == null) {
            signIn()
        }else{
            //already logged in
        }

        // check for android permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                RC_PERMISSION)
        }

        // Navigation to Settings
        btnSettings.setOnClickListener {
            val Intent = Intent(this, SettingsActivity::class.java)
            startActivity(Intent)
        }

        // Navigation to Statistics
        btnStatistics.setOnClickListener {
            val Intent = Intent(this, StatisticsActivity::class.java)
            startActivity(Intent)
        }

        // Navigation to Sports Game
        btnSportGame.setOnClickListener {
            val Intent = Intent(this, SportsGameActivity::class.java)
            startActivity(Intent)
        }
    }

    private fun signIn() {
        // log in with Google Account

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGNIN)
    }

    private fun reqPermissions() {
        // request Permissions specified in fitnessOptions

        GoogleSignIn.requestPermissions(
            this,
            RC_PERMISSION,
            getGoogleAccount(),
            fitnessOptions)
    }

    private fun accountInfo() {
        // show logged in email and id

        val acct = GoogleSignIn.getLastSignedInAccount(this)
        if (acct != null) {
            Log.i(TAG, "account signed in")
            Log.i(TAG, "personEmail: " + acct.email)
            Log.i(TAG, "personId: " + acct.id)
        }else{
            Log.i(TAG, "no account")
        }
    }

    private fun readHeartRateData(timeInterval: TimeUnit, endTime: ZonedDateTime, startTime: ZonedDateTime) {
        // extract heart rate for given time period

        Log.i(TAG, "Range Start: $startTime")
        Log.i(TAG, "Range End: $endTime")

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
            Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        // not every dataSet has dataPoint
                        for (dp in dataSet.dataPoints) {
                            bpmValues.add(dataSet)
                        }
                        showDataSet(dataSet)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "There was a problem getting the heart rate.", e)
                }

        }
    }

    private fun read6hActivities() {
        // extract activities for given time period

        accountInfo()
        val endTime: ZonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusHours(6)

        Log.i(TAG, "Reading activities of last 6h")
        Log.i(TAG, "Range Start: $startTime")
        Log.i(TAG, "Range End: $endTime")

        // create activity read request
        val readRequestActivity =
            DataReadRequest.Builder()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT)
                    //get all activities over 20min duration (real workouts)
                .bucketByActivitySegment(4, TimeUnit.MINUTES)
                .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(),
                    TimeUnit.SECONDS
                )
                .build()

        val account = GoogleSignIn.getLastSignedInAccount(this)

        var activityValues: MutableList<Pair<LocalDateTime,LocalDateTime>> = mutableListOf()

        // do activity read request
        if (account != null) {
            testCounter = 0
            Fitness.getHistoryClient(this, account)
                .readData(readRequestActivity)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        // not every dataSet has dataPoint
                        for (dp in dataSet.dataPoints) {
                            if(dp.getValue(dp.dataType.fields[0]).toString() != "0" && dp.getValue(dp.dataType.fields[0]).toString() != "4"){
                                val activityTimes = Pair(
                                    Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                                    .toLocalDateTime(),
                                    Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                                    .toLocalDateTime())

                                activityValues.add(activityTimes)
                            }
                        }
                    }
                    read6hHeartRate(activityValues)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "There was a problem getting the heart rate.", e)
                }

        }

    }

    private fun read6hHeartRate(activityValues: MutableList<Pair<LocalDateTime,LocalDateTime>>) {
        // extract heart rate for given time period

        Log.i(TAG, "Reading heart rate of last 6h")

        val endTime: ZonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusHours(6)

        // create heart rate read request
        val readRequestHeartRate =
            DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM)
                .bucketByTime(1, TimeUnit.MINUTES)
                .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(),
                    TimeUnit.SECONDS
                )
                .build()

        val account = GoogleSignIn.getLastSignedInAccount(this)

        // do heart rate read request
        if (account != null) {
            testCounter = 0
            var bpmValues: MutableList<Pair<LocalDateTime,Double>> = mutableListOf()
            Fitness.getHistoryClient(this, account)
                .readData(readRequestHeartRate)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        // not every dataSet has dataPoint
                        for (dp in dataSet.dataPoints) {
                            val bpmValue = Pair(
                                Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS)).atZone(ZoneId.systemDefault())
                                    .toLocalDateTime(),
                                dp.getValue(dp.dataType.fields[0]).toString().toDouble()
                            )
                            bpmValues.add(bpmValue)
                            //showDataSet(dataSet)
                        }
                    }
                    compute6hHeartRate(activityValues,bpmValues)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "There was a problem getting the heart rate.", e)
                }
        }
    }

    private fun compute6hHeartRate(activityValues: MutableList<Pair<LocalDateTime,LocalDateTime>>, bpmValues: MutableList<Pair<LocalDateTime,Double>>){
        // calculate resting heart rate with activities- and bpm-list

        Log.i(TAG, "Calculate resting heart rate of last 6h")

        Log.i(TAG, activityValues.size.toString())
        Log.i(TAG, bpmValues.size.toString())
        activityValues.forEach { (start,end) ->
            //Log.i(TAG, "start: " + start)
            //Log.i(TAG, "end: " + end)
            val bpmIterator = bpmValues.iterator()
            while(bpmIterator.hasNext()){
                val bpmPair = bpmIterator.next()
                if(bpmPair.first > start && bpmPair.first <= end){
                    //Log.i(TAG, "in Activity: " + bpmPair.first)
                    bpmIterator.remove()
                }
            }
        }

        val onlyBpmValues: MutableList<Double> = mutableListOf()

        bpmValues.forEach { pair ->
            onlyBpmValues.add(pair.second)
        }

        average6hHeartRate = onlyBpmValues.toDoubleArray().average()
        Log.i(TAG, "Avg bpm over last 6 hours: $average6hHeartRate")

    }

    private fun showDataSet(dataSet: DataSet) {
        // show important info of heart rate datapoint

        for (dp in dataSet.dataPoints) {
            Log.i("History", "Data point:" + testCounter++)
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
                // loop over all data fields
                Log.i(
                    "History", "\tField: " + field.name +
                            " Value: " + dp.getValue(field)
                )
            }
        }
    }

    private fun oAuthPermissionsApproved() =
    GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)

    private fun getGoogleAccount(): GoogleSignInAccount =
    GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    // gets automatically called after sending login-request
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode === RESULT_OK) {
            if(!oAuthPermissionsApproved()){
                // request missing Permissions
                reqPermissions()
            }
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
            Toast.makeText(this, "Signed In", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            accountInfo()
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
        }
    }

}