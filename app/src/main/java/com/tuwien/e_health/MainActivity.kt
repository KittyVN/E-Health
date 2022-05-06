package com.tuwien.e_health


import android.content.ContentValues.TAG
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import java.text.DateFormat
import java.time.*
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val fitnessOptions: GoogleSignInOptionsExtension = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .build()

    private val RC_PERMISSION = 1
    private var testCounter = 0
    private val RC_SIGNIN = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       supportActionBar?.hide()
       this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
           WindowManager.LayoutParams.FLAG_FULLSCREEN);
       setContentView(R.layout.activity_main)


        // TODO: Just for testing, remove later
        btnGoogleSteps.setOnClickListener {
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
        }

        // checks for logged account on startup, if not account, login
        if (GoogleSignIn.getLastSignedInAccount(this) == null) {
            signIn()
        } else {
            //already logged in
        }

        val statisticsLineChartButton = findViewById<Button>(R.id.buttonLineChart)
        statisticsLineChartButton.setOnClickListener {
            val Intent = Intent(this, StatisticsActivity::class.java)
            startActivity(Intent)
        }
        // Navigation to Settings
        val settingsButton = findViewById<Button>(R.id.btnSettings)
        settingsButton.setOnClickListener {
            val Intent = Intent(this, SettingsActivity::class.java)
            startActivity(Intent)
        }
        // Navigation to Statistics
        val statisticsButton = findViewById<Button>(R.id.btnStatistics)
        statisticsButton.setOnClickListener {
            val Intent = Intent(this, StatisticsActivity::class.java)
            startActivity(Intent)
        }
        // Navigation to Sports Game
        val sportsGameButton = findViewById<Button>(R.id.btnSportGame)
        sportsGameButton.setOnClickListener {
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
        val signInIntent = mGoogleSignInClient.getSignInIntent()
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
                    Log.w(TAG, "There was a problem getting the heart rate.", e)
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