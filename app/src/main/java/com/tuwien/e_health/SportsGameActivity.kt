package com.tuwien.e_health

import android.content.BroadcastReceiver
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.content.Context
import android.content.IntentFilter
import android.view.Gravity
import android.view.ViewGroup
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_sports_game.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

// TODO: heart rate impact -> animation and messages

class SportsGameActivity : AppCompatActivity() {

    private var animationHandler = Handler()
    private var messageHandler = Handler()
    private var time = 30000L
    private var timeMsgDuration = 2000L
    private var currentTime = 0L
    private val tag = "[SportsGameActivity]"
    private var hr = 144L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_sports_game)

        // get time from timer spinner activity
        val passedTime = intent.getLongExtra("data", 60000L)
        time = passedTime
        Log.i(tag, "Passed time: $time")

        // game countdown timer
        var gameTimer = timer(time, 1000)

        // start button pulse animation
        pulseAnimation.run()

        // start game
        btnStart.setOnClickListener {
            sendStartSignal()
            gameTimer.start()
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
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // register to receive local broadcasts
        val messageFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver: SportsGameActivity.Receiver = SportsGameActivity().Receiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter)

    }

    // receive message from wearable
    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val bpm = intent.getStringExtra("message").toString() + " "
            Log.i(tag, "Incoming msg: $bpm")
            tvBpm.text = bpm
            hr = bpm.toLong()
        }
    }

    // tell wearable to start heart rate sampling
    private fun sendStartSignal() {
        val message = "Start"
        SendMsg("/my_path", message).start()
    }

    // tell wearable to stop heart rate sampling
    private fun sendStopSignal() {
        val message = "Stop"
        SendMsg("/my_path", message).start()
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

            override fun onTick(millisLeft: Long) {
                tvTime.text = formatTime(millisLeft)
                setBpmAnimation()
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

            }

            override fun onFinish() {
                // time over

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

            // milliseconds to hh:mm:ss
            private fun formatTime(millisLeft: Long):String {
                val hours = TimeUnit.MILLISECONDS.toHours(millisLeft) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisLeft) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisLeft) % 60
                var timeString = ""
                if(hours == 0L) {
                    timeString = "$minutes:$seconds "
                    if(minutes == 0L) {
                        timeString = "$seconds "
                    }
                }else if(hours > 0L) {
                    timeString = "$hours:$minutes:$seconds "
                }
                return timeString
            }
        }

    }

    // change runner animation according to heart rate
    private fun setBpmAnimation() {
        if(hr >= 0.6*180 && hr <= 0.85*180) {
            // in target heart rate area -> very good
            //Log.i(tag, "in thr")
        } else if(hr > 0.85*180) {
            // above target heart rate -> too much
            //Log.i(tag, "above thr")
        } else if(hr <= 100) {
            // in resting heart rate area -> too low
            //Log.i(tag, "in rhr")
        } else if(hr >= 100 && hr < 0.6*180) {
            // above rhr, under thr -> ok
            //Log.i(tag, "above rhr, under thr")
        }
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

}