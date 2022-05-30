package com.tuwien.e_health


import android.Manifest
import android.content.ContentValues.TAG
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
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
import kotlin.math.abs


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
    var toggle = false
    private var restingHeartRate = -1.0
    private val tag = "[MainActivity]"

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)

        // checks for logged account on startup, if not account, login
        if (GoogleSignIn.getLastSignedInAccount(this) == null) {
            signIn()
        } else {
            //already logged in
        }

        // check for android permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                RC_PERMISSION
            )
        }

        // Navigation to Settings
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Navigation to Statistics
        btnStatistics.setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }

        // Navigation to Sports Game
        btnSportGame.setOnClickListener {
            val intent = Intent(this, SportGameTimer::class.java)
            startActivity(intent)
        }

        // opens popup overlay for resting heart rate info message
        btnInfo.setOnClickListener {
            showPopup()
        }

        // toggle buttons and arrows on button bar whenever its clicked
        greenLayout.setOnClickListener {
            toggle = !toggle
            showButtons(toggle)
            if (toggle) {
                arrow1.text = ">"
                arrow2.text = ">"
            } else {
                arrow1.text = "<"
                arrow2.text = "<"
            }
        }

        // toggle buttons and arrows on button bar at swipe
        val parent = findViewById<ViewGroup>(R.id.parent)
        parent.setOnTouchListener(object : OnSwipeTouchListener(this@MainActivity) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                showButtons(true)
                arrow1.text = ">"
                arrow2.text = ">"
            }

            override fun onSwipeRight() {
                super.onSwipeLeft()
                showButtons(false)
                arrow1.text = "<"
                arrow2.text = "<"
            }
        })
    }


    override fun onStart() {
        // reload pond data on every start

        super.onStart()
        read6hActivities()
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
            fitnessOptions
        )
    }

    private fun accountInfo() {
        // show logged in email and id

        val acct = GoogleSignIn.getLastSignedInAccount(this)
        if (acct != null) {
            Log.i(tag, "account signed in")
            Log.i(tag, "personEmail: " + acct.email)
            Log.i(tag, "personName: " + acct.displayName)
            Log.i(tag, "personId: " + acct.id)
        } else {
            Log.i(tag, "no account")
        }
    }

    // TODO: just for testing, remove later
    private fun readHeartRateData(
        timeInterval: TimeUnit,
        endTime: ZonedDateTime,
        startTime: ZonedDateTime
    ) {
        // extract heart rate for given time period

        Log.i(tag, "Range Start: $startTime")
        Log.i(tag, "Range End: $endTime")

        // create read request
        val readRequest =
            DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM)
                //.aggregate(DataType.TYPE_STEP_COUNT_DELTA)
                .bucketByTime(1, timeInterval)
                .setTimeRange(
                    startTime.toEpochSecond(), endTime.toEpochSecond(),
                    TimeUnit.SECONDS
                )
                .build()


        val account = GoogleSignIn.getLastSignedInAccount(this)

        // do read request
        if (account != null) {
            testCounter = 0

            val bpmValues: MutableList<DataSet> = mutableListOf()
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
                    Log.w(tag, "There was a problem getting the heart rate.", e)
                }

        }
    }

    private fun read6hActivities() {
        // extract activities for given time period

        accountInfo()
        val endTime: ZonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusHours(6)

        Log.i(tag, "Reading activities of last 6h")
        Log.i(tag, "Range Start: $startTime")
        Log.i(tag, "Range End: $endTime")

        // create activity read request
        val readRequestActivity =
            DataReadRequest.Builder()
                .aggregate(DataType.TYPE_ACTIVITY_SEGMENT)
                //get all activities over 20min duration (real workouts)
                .bucketByActivitySegment(4, TimeUnit.MINUTES)
                .setTimeRange(
                    startTime.toEpochSecond(), endTime.toEpochSecond(),
                    TimeUnit.SECONDS
                )
                .build()

        val account = GoogleSignIn.getLastSignedInAccount(this)

        val activityValues: MutableList<Pair<LocalDateTime, LocalDateTime>> = mutableListOf()

        // do activity read request
        if (account != null) {
            testCounter = 0
            Fitness.getHistoryClient(this, account)
                .readData(readRequestActivity)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        // not every dataSet has dataPoint
                        for (dp in dataSet.dataPoints) {
                            if (dp.getValue(dp.dataType.fields[0]).toString() != "0" && dp.getValue(
                                    dp.dataType.fields[0]
                                ).toString() != "4"
                            ) {
                                val activityTimes = Pair(
                                    Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS))
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime(),
                                    Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS))
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()
                                )

                                activityValues.add(activityTimes)
                            }
                        }
                    }
                    read6hHeartRate(activityValues)
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "There was a problem getting the heart rate.", e)
                }

        }

    }

    private fun read6hHeartRate(activityValues: MutableList<Pair<LocalDateTime, LocalDateTime>>) {
        // extract heart rate for given time period

        Log.i(tag, "Reading heart rate of last 6h")

        val endTime: ZonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val startTime = endTime.minusHours(6)

        // create heart rate read request
        val readRequestHeartRate =
            DataReadRequest.Builder()
                .aggregate(DataType.TYPE_HEART_RATE_BPM)
                .bucketByTime(1, TimeUnit.MINUTES)
                .setTimeRange(
                    startTime.toEpochSecond(), endTime.toEpochSecond(),
                    TimeUnit.SECONDS
                )
                .enableServerQueries()
                .build()

        val account = GoogleSignIn.getLastSignedInAccount(this)

        // do heart rate read request
        if (account != null) {
            testCounter = 0
            val bpmValues: MutableList<Pair<LocalDateTime, Double>> = mutableListOf()
            Fitness.getHistoryClient(this, account)
                .readData(readRequestHeartRate)
                .addOnSuccessListener { response ->
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        // not every dataSet has dataPoint
                        for (dp in dataSet.dataPoints) {
                            val bpmValue = Pair(
                                Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS))
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime(),
                                dp.getValue(dp.dataType.fields[0]).toString().toDouble()
                            )
                            bpmValues.add(bpmValue)
                            //showDataSet(dataSet)
                        }
                    }
                    compute6hHeartRate(activityValues, bpmValues)
                }
                .addOnFailureListener { e ->
                    Log.w(tag, "There was a problem getting the heart rate.", e)
                }
        }
    }

    private fun compute6hHeartRate(
        activityValues: MutableList<Pair<LocalDateTime, LocalDateTime>>,
        bpmValues: MutableList<Pair<LocalDateTime, Double>>
    ) {
        // calculate resting heart rate with activities- and bpm-list

        Log.i(tag, "Calculate resting heart rate of last 6h")

        Log.i(tag, "activities: " + activityValues.size)
        Log.i(tag, "bpm values: " + bpmValues.size)
        activityValues.forEach { (start, end) ->
            //Log.i(TAG, "start: " + start)
            //Log.i(TAG, "end: " + end)
            val bpmIterator = bpmValues.iterator()
            while (bpmIterator.hasNext()) {
                val bpmPair = bpmIterator.next()
                if (bpmPair.first > start && bpmPair.first <= end) {
                    //Log.i(TAG, "in Activity: " + bpmPair.first)
                    bpmIterator.remove()
                }
            }
        }

        val onlyBpmValues: MutableList<Double> = mutableListOf()

        bpmValues.forEach { pair ->
            onlyBpmValues.add(pair.second)
            Log.i(tag, pair.first.toString())
        }

        val average6hHeartRate = onlyBpmValues.toDoubleArray().average()
        Log.i(tag, "Avg bpm over last 6 hours: $average6hHeartRate")
        restingHeartRate = average6hHeartRate
        updateMainScreen(average6hHeartRate)

    }

    private fun updateMainScreen(rhr: Double) {
        // update background and gifs of main-screen due to resting-heart-rate rhr
        val rhr = 54.0
        if (rhr <= 0 || rhr.isNaN()) {
            setBackground(0)
        } else if (rhr in 1.0..59.9) {
            // best state
            setBackground(11)
            setDog(1)
            setCat(1)
            setRabbit(1)
        } else if (rhr in 60.0..79.9) {
            // good state
            setBackground(21)
            setDog(2)
            setCat(2)
            setRabbit(2)
        } else if (rhr in 80.0..99.9) {
            // semi-good state
            setBackground(31)
            setDog(3)
            setCat(3)
            setRabbit(3)
        } else if (rhr in 100.0..179.9) {
            // bad state
            setBackground(41)
            setDog(4)
            setCat(4)
            setRabbit(4)
        } else if (rhr >= 180) {
            // worst state
            setBackground(51)
            setDog(5)
            setCat(5)
            setRabbit(5)
        }

    }

    private fun setBackground(int: Int) {
        val resId = resources.getIdentifier("bg_$int", "drawable", packageName)
        backgroundImage.setImageResource(resId)
    }

    private fun setDog(int: Int) {
        val resId = resources.getIdentifier("dog_$int", "drawable", packageName)
        dogGIF.setImageResource(resId)
    }

    private fun setCat(int: Int) {
        val resId = resources.getIdentifier("cat_$int", "drawable", packageName)
        catGIF.setImageResource(resId)
    }

    private fun setRabbit(int: Int) {
        val resId = resources.getIdentifier("rabbit_$int", "drawable", packageName)
        rabbitGIF.setImageResource(resId)
    }

    private fun showDataSet(dataSet: DataSet) {
        // show important info of heart rate datapoint

        for (dp in dataSet.dataPoints) {
            Log.i("History", "Data point:" + testCounter++)
            Log.i("History", "\tType: " + dp.dataType.name)
            Log.i(
                "History",
                "\tStart: " + Instant.ofEpochSecond(dp.getStartTime(TimeUnit.SECONDS))
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime().toString()
            )
            Log.i(
                "History",
                "\tEnd: " + Instant.ofEpochSecond(dp.getEndTime(TimeUnit.SECONDS))
                    .atZone(ZoneId.systemDefault())
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
            if (!oAuthPermissionsApproved()) {
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
            accountInfo()
            read6hActivities()
        } catch (e: ApiException) {
            Log.w(tag, "signInResult:failed code=" + e.statusCode)
        }
    }


    private fun showButtons(show: Boolean) {
        // slide  animation for buttons on swipe

        val parent = findViewById<ViewGroup>(R.id.parent)
        val transition: Transition = Slide(Gravity.RIGHT)
        transition.duration = 300
        transition.addTarget(R.id.btnStatistics)
        transition.addTarget(R.id.btnSettings)
        transition.addTarget(R.id.btnSportGame)
        TransitionManager.beginDelayedTransition(parent, transition)
        btnStatistics.visibility = if (show) View.VISIBLE else View.GONE
        btnSettings.visibility = if (show) View.VISIBLE else View.GONE
        btnSportGame.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showPopup() {

        val builder = AlertDialog.Builder(this)
        builder.setTitle(" ")
        builder.setPositiveButton("Ok", null)
        val messageBasic = "Your resting heart rate of the past six hours is $restingHeartRate. "
        var messageAdvanced = ""

        if (restingHeartRate <= 0 || restingHeartRate.isNaN()) {
            // no rhr detected

            messageAdvanced =
                "Unfortunately we could not detect a heart rate. Maybe check your Google Fit Account."
            builder.setIcon(R.drawable.ic_baseline_help_outline_24)
        } else if (restingHeartRate in 1.0..59.9) {
            // best state

            messageAdvanced =
                "This is a extremely good value (<60). Either you were sleeping or your heart is very well trained."
            builder.setIcon(R.drawable.ic_baseline_mood_24)
        } else if (restingHeartRate in 60.0..79.9) {
            // good state

            messageAdvanced =
                "This is a very good heart rate (60 - 80). Seems like you are very relaxed."
            builder.setIcon(R.drawable.ic_baseline_sentiment_satisfied_alt_24)
        } else if (restingHeartRate in 80.0..99.9) {

            // semi-good state
            messageAdvanced =
                "This is a fairly decent resting heart rate (80 - 100). The next better area would be 60 - 80."
            builder.setIcon(R.drawable.ic_baseline_sentiment_satisfied_24)
        } else if (restingHeartRate in 100.0..179.9) {
            // bad

            messageAdvanced =
                "A resting heart rate over 100 might be an indicator for high stress or heart problems. If your heart rate is over 100 for a very long time you might want to get yourself checked by a professional."
            builder.setIcon(R.drawable.ic_baseline_sentiment_dissatisfied_24)
        } else if (restingHeartRate >= 180) {
            // worst state

            messageAdvanced =
                "A resting heart rate over 180 is extremely bad. You should get yourself checked by a professional very soon."
            builder.setIcon(R.drawable.ic_baseline_mood_bad_24)
        }

        builder.setMessage(messageBasic + messageAdvanced)

        val alertDialog = builder.create()
        alertDialog.show()

        val okButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        with(okButton) {
            setPadding(0, 0, 20, 0)
            setTextColor(Color.BLACK)
        }
    }

    internal open class OnSwipeTouchListener(c: Context?) :
        View.OnTouchListener {
        private val gestureDetector: GestureDetector
        override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
            return gestureDetector.onTouchEvent(motionEvent)
        }

        private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD: Int = 100
            private val SWIPE_VELOCITY_THRESHOLD: Int = 100
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onClick()
                return super.onSingleTapUp(e)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                onDoubleClick()
                return super.onDoubleTap(e)
            }

            override fun onLongPress(e: MotionEvent) {
                onLongClick()
                super.onLongPress(e)
            }

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                try {
                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x
                    if (abs(diffX) > abs(diffY)) {
                        if (abs(diffX) > SWIPE_THRESHOLD && abs(
                                velocityX
                            ) > SWIPE_VELOCITY_THRESHOLD
                        ) {
                            if (diffX > 0) {
                                onSwipeRight()
                            } else {
                                onSwipeLeft()
                            }
                        }
                    } else {
                        if (abs(diffY) > SWIPE_THRESHOLD && abs(
                                velocityY
                            ) > SWIPE_VELOCITY_THRESHOLD
                        ) {
                            if (diffY < 0) {
                                onSwipeUp()
                            } else {
                                onSwipeDown()
                            }
                        }
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
                return false
            }
        }

        open fun onSwipeRight() {}
        open fun onSwipeLeft() {}
        open fun onSwipeUp() {}
        open fun onSwipeDown() {}
        private fun onClick() {}
        private fun onDoubleClick() {}
        private fun onLongClick() {}

        init {
            gestureDetector = GestureDetector(c, GestureListener())
        }
    }
}