package com.tuwien.e_health


import android.content.ContentValues.TAG
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.TimeUnit
import android.icu.util.ULocale
import android.os.Bundle
import android.util.Log
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


class MainActivity : AppCompatActivity() {

    private val fitnessOptions: GoogleSignInOptionsExtension = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .build()

    private val RC_SIGNIN = 0
    private val RC_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        btnGoogleSignIn.setOnClickListener {
            if(GoogleSignIn.getLastSignedInAccount(this) == null) {
                signIn()
            }
        }

        btnGoogleSignOut.setOnClickListener {
            if(GoogleSignIn.getLastSignedInAccount(this) != null) {
                logOut()
                Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
            }
        }

        // TODO: Future Work, remove commends for testing purposes
        /*
        btnGoogleSteps.setOnClickListener {
            readData()
        }
        */

        // checks for logged account on startup, if not account, login
        if(GoogleSignIn.getLastSignedInAccount(this) == null) {
            signIn()
        }else{
            //already logged in
        }


        val statisticsLineChartButton = findViewById<Button>(R.id.buttonLineChart)
        statisticsLineChartButton.setOnClickListener {
            val Intent = Intent(this,StatisticsActivity::class.java)
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

    private fun logOut() {
        // log out of Google Account

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        mGoogleSignInClient.signOut()
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

    // TODO: Future Work, remove commend for testing purposes
    /*
    private fun readData() {
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusDays(2)
        Log.i(TAG, "Range Start: $startTime")
        Log.i(TAG, "Range End: $endTime")

        val readRequest =
            DataReadRequest.Builder()
                //.aggregate(DataType.TYPE_HEART_RATE_BPM)
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, java.util.concurrent.TimeUnit.DAYS)
                .setTimeRange(startTime.toEpochSecond(), endTime.toEpochSecond(),
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()


        val account = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            Fitness.getHistoryClient(this, account)
                .readData(readRequest)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        showDataSet(dataSet)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "There was a problem getting the step count.", e)
                }

        }
    }

    private fun showDataSet(dataSet: DataSet) {
        Log.i(TAG, "anfang")
        val dateFormat: DateFormat = DateFormat.getDateInstance()
        val timeFormat: DateFormat = DateFormat.getTimeInstance()
        for (dp in dataSet.dataPoints) {
            Log.i("History", "Data point:")
            Log.i("History", "\tType: " + dp.dataType.name)
            Log.i("History",
                "\tStart: " + dateFormat.format(dp.getStartTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .toString() + " " + timeFormat.format(dp.getStartTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            )
            Log.i("History",
                "\tEnd: " + dateFormat.format(dp.getEndTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .toString() + " " + timeFormat.format(dp.getStartTime(java.util.concurrent.TimeUnit.MILLISECONDS))
            )
            for (field in dp.dataType.fields) {
                Log.i(
                    "History", "\tField: " + field.name +
                            " Value: " + dp.getValue(field)
                )
                tvSteps.setText(dp.getValue(field).toString() + " steps")
            }
        }
    }
    */

    private fun oAuthPermissionsApproved() =
    GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)

    private fun getGoogleAccount(): GoogleSignInAccount =
    GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    // gets automatically called sending login-request
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