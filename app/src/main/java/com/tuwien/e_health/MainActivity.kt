package com.tuwien.e_health


import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.*
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import kotlinx.android.synthetic.main.activity_main.*
import java.time.*
import java.util.*
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


    private lateinit var auth: FirebaseAuth
    private val REQ_ONE_TAP = 2 // Can be any integer unique to the Activity
    private var showOneTapUI = true
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var database: DatabaseReference
    private val RC_SIGNIN = 0
    private val RC_PERMISSION = 1
    private var testCounter = 0
    var toggle = false
    private var restingHeartRate = -1.0
    private val tag = "[MainActivity]"
    private var knownUsers : MutableSet<String> = mutableSetOf()

    // variables that have up do date user data stored. if yearOfBirth and age have their standard
    // values of -1 there is no user signed in.
    private var yearOfBirth = -1
    private var age = -1
    private var sportMode = false

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
            Log.i(tag,"SIGN IN CALLED")
        }else{
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

        // gets instance of DatabaseReference object
        database = Firebase.database("https://e-health-347815-default-rtdb.europe-west1.firebasedatabase.app").reference

        // adds listener that reads all userID's stored in database and adds them to knownUsers
        addUserIDEventListener(database.child("users"))

        // gets instance of FirebaseAuth object
        auth = Firebase.auth

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
        Log.i(tag,"main started")
        // reload pond data on every start
        super.onStart()

        // Check if user is signed in (non-null) and update UI accordingly
        val currentUser = auth.currentUser

        // adds listener that reads current user's year of birth
        auth.currentUser?.let { database.child("users").child(it.uid) }
            ?.let { addYearOfBirthEventListener(it) }

        // adds listener that reads current user's sport mode setting
        auth.currentUser?.let { database.child("users").child(it.uid) }
            ?.let { addSportModeEventListener(it) }

        // Check if user is signed in (non-null) and update UI accordingly
        updateUI(currentUser)

        //
        read6hActivities()
    }

    private fun signIn() {
        // log in with Google Account

        oneTapClient = Identity.getSignInClient(this)
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    // Your server's client ID, not your Android client ID.
                    .setServerClientId("138791617002-334a5tj9850kf722ngormuq9f8rqq6ah.apps.googleusercontent.com")
                    // Only show accounts previously used to sign in.
                    .setFilterByAuthorizedAccounts(true)
                    .build())
            .build()

        // displays one tap sign-in UI
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    startIntentSenderForResult(
                        result.pendingIntent.intentSender, REQ_ONE_TAP,
                        null, 0, 0, 0, null)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e(tag, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
                Log.d(tag, e.localizedMessage)
                Log.d(tag,"log in failure listener called since there is no account")
                val Intent = Intent(this, NoGoogleAccountActivity::class.java)
                startActivity(Intent)


            }
    }

    private fun oldGoogleAccSignIn() {
        // Old sign in function, still needed to trigger 6h heart rate data.
        // If this were left out GoogleSignIn.getLastSignedInAccount(this)
        // would return null.
        // TODO: Find out why this is and change code to make this unnecessary.
        // Important! : Doing so will also require a number of changes in SettingsActivity class.

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGNIN)
    }

    private fun writeNewUserToDatabase(user: FirebaseUser) {
        val newUser = UserData(user.email,user.displayName)

        database.child("users").child(user.uid).setValue(newUser)
            .addOnSuccessListener {
                Log.i(tag,"new user ${newUser.email} created in database")
            }
            .addOnFailureListener { e ->
                Log.i(tag, "there was a problem creating the new user ${newUser.email}", e)
            }
    }

    private fun addUserIDEventListener(databaseReference: DatabaseReference) {
        val databaseListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val database = dataSnapshot.children
                for(uid in database) {
                    uid.key?.let {
                        knownUsers.add(it)
                        Log.w(tag, "Database listener retrieved data: " + it)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(tag, "loadPost:onCancelled", databaseError.toException())
            }
        }
        databaseReference.addValueEventListener(databaseListener)
    }

    private fun addUserDataEventListener(userDataReference: DatabaseReference) {
        val userDataListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val userData = dataSnapshot.getValue<UserData>()
                Log.w(tag, "User data listener retrieved data: " + userData.toString())
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(tag, "loadPost:onCancelled", databaseError.toException())
            }
        }
        userDataReference.addValueEventListener(userDataListener)
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

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            Log.i(tag, "account signed in")
            Log.i(tag, "personEmail: " + user.email)
            Log.i(tag, "personName: " + user.displayName)
            Log.i(tag, "personId: " + user.uid)
        }else{
            Log.i(tag, "no account")
            yearOfBirth = -1
            age = -1
            sportMode = false
            Log.i(tag, "year of birth is $yearOfBirth")
            Log.i(tag, "age is $age")
            Log.i(tag, "sport mode $sportMode")
        }
    }

    private fun read6hActivities() {
        // extract activities for given time period

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
            //Log.i(tag, "start: " + start)
            //Log.i(tag, "end: " + end)
            val bpmIterator = bpmValues.iterator()
            while (bpmIterator.hasNext()) {
                val bpmPair = bpmIterator.next()
                if (bpmPair.first > start && bpmPair.first <= end) {
                    //Log.i(tag, "in Activity: " + bpmPair.first)
                    bpmIterator.remove()
                }
            }
        }

        val onlyBpmValues: MutableList<Double> = mutableListOf()

        bpmValues.forEach { pair ->
            onlyBpmValues.add(pair.second)
            //Log.i(tag, pair.first.toString())
        }

        val average6hHeartRate = onlyBpmValues.toDoubleArray().average()
        Log.i(tag, "Avg bpm over last 6 hours: $average6hHeartRate")
        restingHeartRate = average6hHeartRate
        updateMainScreen(average6hHeartRate)

    }

    private fun updateMainScreen(rhr: Double) {
        // update background and gifs of main-screen due to resting-heart-rate rhr
        //val rhr = 54.0

        // set default value of 180 maxHr (~correct if 40 years old)
        var maxHeartRate = 180
        if(age != -1) {
            maxHeartRate = 220 - age
        }

        if (rhr <= 0 || rhr.isNaN()) {
            setBackground(0)
        } else if (rhr >= 1 && rhr < 60) {
            if(sportMode){
                // best state
                setBackground(11)
                setDog(1)
                setCat(1)
                setRabbit(1)
            } else {
                // bad state
                // -> low hr and no sport can be sign for heart problem
                setBackground(41)
                setDog(4)
                setCat(4)
                setRabbit(4)
            }

        } else if (rhr >= 60 && rhr < 80) {
            // good state
            setBackground(21)
            setDog(2)
            setCat(2)
            setRabbit(2)
        } else if (rhr >= 80 && rhr < 100) {
            // semi-good state
            setBackground(31)
            setDog(3)
            setCat(3)
            setRabbit(3)
        } else if (rhr >= 100 && rhr < maxHeartRate) {
            // bad state
            setBackground(41)
            setDog(4)
            setCat(4)
            setRabbit(4)
        } else if (rhr >= maxHeartRate) {
            // worst state
            setBackground(51)
            setDog(5)
            setCat(5)
            setRabbit(5)
        }

    }


    private fun addYearOfBirthEventListener(databaseReference: DatabaseReference) {
        val databaseListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val database = dataSnapshot.child("yearOfBirth")
                yearOfBirth = database.value.toString().toInt()
                Log.i(tag,"year of birth is " + database.value)

                // calculate age, set it to -1 if there is no year of birth set
                if(yearOfBirth != -1) {
                    var currentYear = Calendar.getInstance().get(Calendar.YEAR)
                    age = currentYear - yearOfBirth
                } else {
                    age = -1
                }
                Log.i(tag,"year of birth: $yearOfBirth, age: $age")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(tag, "loadPost:onCancelled", databaseError.toException())
            }
        }
        databaseReference.addValueEventListener(databaseListener)
    }

    private fun addSportModeEventListener(databaseReference: DatabaseReference) {
        val databaseListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val database = dataSnapshot.child("sportMode")
                sportMode = database.value.toString().toBooleanStrict()
                Log.i(tag,"sport mode " + database.value)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(tag, "loadPost:onCancelled", databaseError.toException())
            }
        }
        databaseReference.addValueEventListener(databaseListener)
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

        when (requestCode) {
            REQ_ONE_TAP -> {
                try {
                   val googleCredential = oneTapClient.getSignInCredentialFromIntent(data)
                   val idToken = googleCredential.googleIdToken
                   when {
                       idToken != null -> {
                           // Got an ID token from Google. Use it to authenticate
                           // with Firebase.
                           val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                           auth.signInWithCredential(firebaseCredential)
                               .addOnCompleteListener(this) { task ->
                                   if(task.isSuccessful) {
                                       // Sign in success, update UI with signed-in user's information
                                       Log.d(tag, "signInWithCredential:success")
                                       val user = auth.currentUser
                                       oldGoogleAccSignIn()
                                       updateUI(user)
                                       if (user != null) {
                                           if (knownUsers.contains(user.uid)) {
                                               Log.d(tag,"user already exists in database")
                                           } else {
                                               writeNewUserToDatabase(user)
                                           }
                                       } else {
                                           Log.d(tag,"no user found")
                                       }
                                       Toast.makeText(this, "Signed In", Toast.LENGTH_SHORT).show()

                                       // listeners are called again late after sign in to get accurate account data
                                       // adds listener that reads current user's year of birth
                                       auth.currentUser?.let { database.child("users").child(it.uid) }
                                           ?.let { addYearOfBirthEventListener(it) }
                                       // adds listener that reads current user's sport mode setting
                                       auth.currentUser?.let { database.child("users").child(it.uid) }
                                           ?.let { addSportModeEventListener(it) }

                                   } else {
                                       // If sign in fails, display a message to the user.
                                       Log.w(tag, "signInWithCredential:failure", task.exception)
                                       updateUI(null)
                                   }
                               }
                       }
                       else -> {
                           // Shouldn't happen.
                           Log.d(tag, "No ID token")
                       }
                   }

                } catch (e: ApiException) {
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> {
                            Log.d(tag, "One-tap dialog was closed.")
                            // Don't re-prompt the user.
                            showOneTapUI = false
                            Toast.makeText(this, "Google account is needed for this app to work. Please sign in in settings.", Toast.LENGTH_LONG).show()
                        }
                        CommonStatusCodes.NETWORK_ERROR -> {
                            Log.d(tag, "One-tap encountered a network error.")
                            // Try again or just ignore.
                        }
                        else -> {
                            Log.d(tag, "Couldn't get credential from result." +
                                    " (${e.localizedMessage})")
                        }
                    }
                }
            }
        }

        // Old sign in function code, still needed. See comment in googleAccSignIn function.
        // TODO: Delete this as well as soon as it's figured out how to work without it.
        if (resultCode === RESULT_OK) {
            if (!oAuthPermissionsApproved()) {
                // request missing Permissions
                reqPermissions()
            }
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
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

        var messageBasic = "Your resting heart rate of the past six hours is " + restingHeartRate.toLong() + ". "
        var messageAdvanced = ""
        // set default value of 180 maxHr (~correct if 40 years old)
        var maxHeartRate = 180
        if(age != -1) {
            maxHeartRate = 220 - age
        }

        if (restingHeartRate <= 0 || restingHeartRate.isNaN()) {
            // no rhr detected
            messageBasic = "Your resting heart rate of the past six hours is $restingHeartRate. "
            messageAdvanced =
                "Unfortunately we could not detect a heart rate. Maybe check your Google Fit Account."
            builder.setIcon(R.drawable.ic_baseline_help_outline_24)
        } else if (restingHeartRate >= 1 && restingHeartRate < 60) {
            if(sportMode) {
                // best state

                messageAdvanced =
                    "This is a extremely good value (<60). Either you were sleeping or your heart is very well trained."
                builder.setIcon(R.drawable.ic_baseline_mood_24)
            } else {
                // bad

                messageAdvanced =
                    "A resting heart rate under 60 without doing sport might be an indicator for heart problems (or you were just sleeping). If your heart rate is under 60 for a very long time you might want to get yourself checked by a professional."
                builder.setIcon(R.drawable.ic_baseline_sentiment_dissatisfied_24)
            }

        } else if (restingHeartRate >= 60 && restingHeartRate < 80) {
            // good state

            messageAdvanced =
                "This is a very good heart rate (60 - 80). Seems like you are very relaxed."
            builder.setIcon(R.drawable.ic_baseline_sentiment_satisfied_alt_24)
        } else if (restingHeartRate >= 80 && restingHeartRate < 100) {

            // semi-good state
            messageAdvanced =
                "This is a fairly decent resting heart rate (in range 80 - 100 bpm). The next better area would be 60 - 80 bpm."
            builder.setIcon(R.drawable.ic_baseline_sentiment_satisfied_24)
        } else if (restingHeartRate >= 100 && restingHeartRate < maxHeartRate) {
            // bad

            messageAdvanced =
                "A resting heart rate over 100 might be an indicator for high stress or heart problems. If your heart rate is over 100 for a very long time you might want to get yourself checked by a professional."
            builder.setIcon(R.drawable.ic_baseline_sentiment_dissatisfied_24)
        } else if (restingHeartRate >= maxHeartRate) {
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