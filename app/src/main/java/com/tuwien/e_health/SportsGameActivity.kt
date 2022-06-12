package com.tuwien.e_health

import android.content.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_sports_game.*
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

// TODO: heart rate impact -> animations

class SportsGameActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private var animationHandler = Handler()
    private var messageHandler = Handler()
    private var time = 0L
    private var timeMsgDuration = 5000L
    private var bpmMsgDuration = 5000L
    private var currentTime = 0L
    private val tag = "[SportsGameActivity]"
    private var hr = 144L
    private var status : HeartRateStatus? = null
    private var heartRateMsgTime = 0L
    private var gameStatus = false

    // variables that have up do date user data stored. if yearOfBirth and age have their standard
    // values of -1 there is no user signed in.
    private var yearOfBirth = -1
    private var age = -1
    private var sportMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_sports_game)

        // gets instance of FirebaseAuth object
        auth = Firebase.auth

        // gets instance of DatabaseReference object
        database = Firebase.database("https://e-health-347815-default-rtdb.europe-west1.firebasedatabase.app").reference

        // get time from timer spinner activity
        val passedTime = intent.getLongExtra("data", 60000L)
        time = passedTime
        Log.i(tag, "Passed time: $time")
        tvTime.text = formatTime(time)

        // game countdown timer
        var gameTimer = timer(time, 1000)

        // start button pulse animation
        pulseAnimation.run()

        // start game
        btnStart.setOnClickListener {
            gameStatus = true
            sendStartSignal()
            gameTimer.start()
            //tvBpm.text = "999 "
            runner.visibility = View.VISIBLE
            bahn.visibility = View.INVISIBLE
            btnStart.visibility = View.INVISIBLE
            circleStart.visibility = View.INVISIBLE
            circleStart2.visibility = View.INVISIBLE
            bgWaiting.visibility = View.INVISIBLE
            circlePause.visibility = View.VISIBLE
            btnPause.visibility = View.VISIBLE
            animationHandler.removeCallbacks(pulseAnimation)
        }

        // play again
        btnAgain.setOnClickListener {
            sendStartSignal()
            gameTimer.start()
            runner.visibility = View.VISIBLE
            bahn.visibility = View.INVISIBLE
            circleAgain.visibility = View.INVISIBLE
            circleHome.visibility = View.INVISIBLE
            btnAgain.visibility = View.INVISIBLE
            btnHome.visibility = View.INVISIBLE
            bgWaiting.visibility = View.INVISIBLE
        }

        // pause game
        btnPause.setOnClickListener {
            gameTimer.cancel()
            sendStopSignal()
            circleContinue.visibility = View.VISIBLE
            btnContinue.visibility = View.VISIBLE
            runner.visibility = View.INVISIBLE
            bahn.visibility = View.VISIBLE
            circleHome.visibility = View.VISIBLE
            btnHome.visibility = View.VISIBLE
            btnMessage.visibility = View.INVISIBLE
            bgWaiting.visibility = View.VISIBLE
        }

        // continue game
        btnContinue.setOnClickListener {
            gameTimer = timer(currentTime, 1000)
            gameTimer.start()
            sendStartSignal()
            runner.visibility = View.VISIBLE
            bahn.visibility = View.INVISIBLE
            circleHome.visibility = View.INVISIBLE
            btnHome.visibility = View.INVISIBLE
            bgWaiting.visibility = View.INVISIBLE
            circleContinue.visibility = View.INVISIBLE
            btnContinue.visibility = View.INVISIBLE
        }

        // go back home
        btnHome.setOnClickListener {
            gameTimer.cancel()
            sendStopSignal()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // register to receive local broadcasts
        val messageFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver: SportsGameActivity.Receiver = SportsGameActivity().Receiver()
        messageReceiver.setBpmTextView(tvBpm)
        messageReceiver.setPauseBtn(btnPause)
        messageReceiver.setContinueBtn(btnContinue)
        messageReceiver.setStartBtn(btnStart)
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter)

    }

    override fun onStart() {
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
        accountInfo(currentUser)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        Log.i(tag, "Detected Game exit, stop sampling")
        // tell smartwatch to stop sampling heart rate
        sendStopSignal()

        // goto main screen, not game timer
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    // receive message from wearable
    inner class Receiver : BroadcastReceiver() {
        private lateinit var tvB: TextView
        private lateinit var btnP: Button
        private lateinit var btnC: Button
        private lateinit var btnS: Button

        fun setBpmTextView(tv: TextView){
            tvB = tv
        }

        fun setPauseBtn(btnX: Button){
            btnP = btnX
        }

        fun setContinueBtn(btnX: Button){
            btnC = btnX
        }

        fun setStartBtn(btnX: Button){
            btnS = btnX
        }

        override fun onReceive(context: Context, intent: Intent) {
            val bpm = intent.getStringExtra("message").toString()
            Log.i(tag, tvB.text.toString())
            if(bpm == "Start" && tvB.text == "0 ") {
                // start when game not started
                btnS.performClick()
                Log.i(tag, "Start")
            } else if(bpm == "Start"  && tvB.text != "0 ") {
                // continue when game paused
                btnC.performClick()
                Log.i(tag, "Continue")
            } else if(bpm == "Stop" && tvB.text != "0 ") {
                // pause when game running
                Log.i(tag, "Stop")
                btnP.performClick()
            } else if(bpm == "Stop" && tvB.text == "0 ") {
                // cant stop before game started
            } else {
                // msg is bpm value
                Log.i(tag, "Incoming msg: $bpm")
                hr = bpm.toDouble().toLong()
                tvB.text = "$hr "
            }
        }
    }

    // tell wearable to start heart rate sampling
    private fun sendStartSignal() {
        val message = "Start"
        SendMsg("/eHealth", message).start()
    }

    // tell wearable to stop heart rate sampling
    private fun sendStopSignal() {
        val message = "Stop"
        SendMsg("/eHealth", message).start()
    }

    // tell wearable app is still alive at this time
    private fun sendTimeStamp() {
        val message = LocalDateTime.now().toString()
        SendMsg("/eHealth", message).start()
    }

    // send message to wearable
    internal inner class SendMsg
        (var path: String, private var message: String) : Thread() {
        override fun run() {
            // Retrieve the connected devices (nodes)
            val wearableList = Wearable.getNodeClient(applicationContext).connectedNodes
            try {
                val nodes = Tasks.await(wearableList)
                for (node in nodes) {
                    // Send the message
                    val sendMessageTask =
                        Wearable.getMessageClient(this@SportsGameActivity).sendMessage(node.id, path, message.toByteArray())
                    try {
                        val result = Tasks.await(sendMessageTask)
                        Log.i(tag, "Msg sent successfully")
                    } catch (exception: ExecutionException) {
                        Log.i(tag, exception.toString())
                    }
                }
            } catch (exception: ExecutionException) {
                Log.i(tag, exception.toString())
            }
        }
    }

    // game countdown timer
    private fun timer(timeToPlay:Long, interval:Long):CountDownTimer{

        return object: CountDownTimer(timeToPlay, interval) {
            private var almostDone = false
            private var quarterCheck = false
            private var halfCheck = false
            private var threeQuarterCheck = false
            private var bpmChecker = false

            override fun onTick(millisLeft: Long) {
                tvTime.text = formatTime(millisLeft)

                val bpm = tvBpm.text.dropLast(1).toString()
                hr = bpm.toLong()
                // also sets runner animation
                setHeartRateStatus()

                // check for heart rate message
                checkHeartRateMessage(millisLeft)

                // create time message
                if (millisLeft <= (0.1 * time).toLong() && !almostDone) {
                    Log.i(tag, "90%: $millisLeft")
                    btnMessage.text = "90% over. Almost finished!"
                    almostDone = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.25 * time).toLong() && !threeQuarterCheck) {
                    Log.i(tag, "75%: $millisLeft")
                    btnMessage.text = "75% over. Hold on!"
                    threeQuarterCheck = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.5 * time).toLong() && !halfCheck) {
                    Log.i(tag, "50%: $millisLeft")
                    btnMessage.text = "50% over. Halftime!"
                    halfCheck = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.75 * time).toLong() && !quarterCheck) {
                    Log.i(tag, "25%: $millisLeft")
                    btnMessage.text = "25% over. Keep going!"
                    quarterCheck = true
                    showTimeMessage.run()
                }
                currentTime = millisLeft
                sendTimeStamp()
            }

            override fun onFinish() {
                // time over

                gameStatus = false
                sendStopSignal()
                tvTime.text = "00:00 "
                btnStart.text = "Run again"
                runner.visibility = View.INVISIBLE
                bahn.visibility = View.VISIBLE
                circleAgain.visibility = View.VISIBLE
                circleHome.visibility = View.VISIBLE
                btnAgain.visibility = View.VISIBLE
                btnHome.visibility = View.VISIBLE
                btnMessage.visibility = View.INVISIBLE
                bgWaiting.visibility = View.VISIBLE
            }

            // set heart rate status and change runner animation accordingly
            private fun setHeartRateStatus() {

                // set default value of 180 maxHr (~correct if 40 years old)
                var maxHeartRate = 180
                // set default value of 50%
                var minTargetHeartRatePercent = 0.5
                if(age != -1) {
                    maxHeartRate = 220 - age
                }
                if(sportMode) {
                    minTargetHeartRatePercent = 0.6
                }


                if(hr >= minTargetHeartRatePercent*maxHeartRate && hr <= 0.85*maxHeartRate) {
                    // in target heart rate area -> very good
                    //Log.i(tag, "in thr")
                    status = HeartRateStatus.IN_THR
                    runner.setImageResource(R.drawable.runner_thr)
                } else if(hr > 0.85*maxHeartRate) {
                    // above target heart rate -> too much
                    //Log.i(tag, "above thr")
                    status = HeartRateStatus.ABOVE_THR
                    runner.setImageResource(R.drawable.runner_above_thr)
                } else if(hr <= 100) {
                    // in resting heart rate area -> too low
                    //Log.i(tag, "in rhr")
                    status = HeartRateStatus.IN_RHR
                    runner.setImageResource(R.drawable.runner_rhr)
                } else if(hr >= 100 && hr < minTargetHeartRatePercent*maxHeartRate) {
                    // above rhr, under thr -> ok
                    //Log.i(tag, "above rhr, under thr")
                    status = HeartRateStatus.UNDER_THR
                    runner.setImageResource(R.drawable.runner_rhr_to_thr)
                }
            }

            // check for heart rate message
            private fun checkHeartRateMessage(millisLeft: Long) {
                if (status == HeartRateStatus.IN_RHR && !bpmChecker) {
                    heartRateMsgTime = millisLeft
                    bpmChecker = true
                } else if((status == HeartRateStatus.IN_RHR) && bpmChecker && (millisLeft <= (heartRateMsgTime-10000L))) {
                    btnMessage.text = "Try harder! Get at least over 100 bpm!"
                    showHeartRateMessage.run()
                    bpmChecker = false
                } else if (status == HeartRateStatus.IN_THR && !bpmChecker) {
                    heartRateMsgTime = millisLeft
                    bpmChecker = true
                    btnMessage.text = "Very good! Stay between " + (0.6*180).toLong() + " - " + (0.85*180).toLong() +" BPM!"
                    showHeartRateMessage.run()
                } else if((status == HeartRateStatus.IN_THR) && bpmChecker && (millisLeft <= (heartRateMsgTime-20000L))) {
                    bpmChecker = false
                } else if (status == HeartRateStatus.ABOVE_THR && !bpmChecker) {
                    heartRateMsgTime = millisLeft
                    bpmChecker = true
                    btnMessage.text = "You are trying too hard. Slow down a little bit! Go under " + (0.85*180).toLong() +" BPM!"
                    showHeartRateMessage.run()
                } else if((status == HeartRateStatus.ABOVE_THR) && bpmChecker && (millisLeft <= (heartRateMsgTime-10000L))) {
                    bpmChecker = false
                } else if (status == HeartRateStatus.UNDER_THR && !bpmChecker) {
                    heartRateMsgTime = millisLeft
                    bpmChecker = true
                } else if((status == HeartRateStatus.UNDER_THR) && bpmChecker && (millisLeft <= (heartRateMsgTime-10000L))) {
                    btnMessage.text = "Try a bit harder! Get between " + (0.6*180).toLong() + " - " + (0.85*180).toLong() +" BPM!"
                    showHeartRateMessage.run()
                    bpmChecker = false
                }
            }

        }

    }

    // milliseconds to hh:mm:ss
    private fun formatTime(millisLeft: Long):String {
        val hours = TimeUnit.MILLISECONDS.toHours(millisLeft) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millisLeft) % 60
        var minutesString = "$minutes:"
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millisLeft) % 60
        var secondsString = seconds.toString()

        if(seconds < 10) {
            secondsString = "0$secondsString"
        }
        if(minutes < 10) {
            minutesString = "0$minutesString"
        }
        var timeString = ""
        if(hours == 0L) {
            if(minutes == 0L) {
                minutesString = ""
            }
            if(seconds == 0L) {
                secondsString = "00 "
            }
            timeString = "$minutesString$secondsString "
        }else if(hours > 0L) {
            if(minutes == 0L) {
                minutesString = "00"
            }
            if(seconds == 0L) {
                secondsString = "00 "
            }
            timeString = "$hours:$minutesString$secondsString "
        }
        return timeString
    }

    // show bpm message popups
    private var showHeartRateMessage = Runnable {
        val parent = findViewById<ViewGroup>(R.id.sportGame)
        val transition: Transition = Slide(Gravity.TOP)
        transition.duration = 1000
        transition.addTarget(R.id.btnMessage)
        TransitionManager.beginDelayedTransition(parent, transition)
        btnMessage.visibility = View.VISIBLE
        messageHandler.postDelayed(removeHeartRateMessage, bpmMsgDuration)
    }

    // remove bpm message popups
    private var removeHeartRateMessage = Runnable {
        val parent = findViewById<ViewGroup>(R.id.sportGame)
        val transition: Transition = Slide(Gravity.TOP)
        transition.duration = 1000
        transition.addTarget(R.id.btnMessage)
        TransitionManager.beginDelayedTransition(parent, transition)
        btnMessage.visibility = View.INVISIBLE
    }

    // show time message popups
    private var showTimeMessage = Runnable {
        val parent = findViewById<ViewGroup>(R.id.sportGame)
        val transition: Transition = Slide(Gravity.TOP)
        transition.duration = 1000
        transition.addTarget(R.id.btnMessage)
        TransitionManager.beginDelayedTransition(parent, transition)
        btnMessage.visibility = View.VISIBLE
        messageHandler.postDelayed(removeTimeMessage, timeMsgDuration)
    }

    // remove time message popups
    private var removeTimeMessage = Runnable {
        val parent = findViewById<ViewGroup>(R.id.sportGame)
        val transition: Transition = Slide(Gravity.TOP)
        transition.duration = 1000
        transition.addTarget(R.id.btnMessage)
        TransitionManager.beginDelayedTransition(parent, transition)
        btnMessage.visibility = View.INVISIBLE
    }

    // pulsing animation for start button
    private var pulseAnimation = object : Runnable {
        override fun run() {
            circleStart.animate().scaleX(2f).scaleY(2f).alpha(0f).setDuration(1200)
                .withEndAction {
                    circleStart.scaleX = 1f
                    circleStart.scaleY = 1f
                    circleStart.alpha = 1f
                }
            animationHandler.postDelayed(this, 1500)
        }
    }

    // logs user data if user is signed it, resets user variables if user is logged out
    private fun accountInfo(user: FirebaseUser?) {
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

    // reads user's year of birth out of database and calculates age
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

    // reads user's sport mode setting out of databse
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

    enum class HeartRateStatus {
        IN_THR, IN_RHR, ABOVE_THR, UNDER_THR
    }

}