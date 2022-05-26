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
import android.content.ContentValues.TAG
import android.content.Context
import android.content.IntentFilter
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
import kotlinx.android.synthetic.main.activity_sports_game.*
import java.util.concurrent.ExecutionException

class SportsGameActivity : AppCompatActivity() {

    private var animationHandler = Handler()
    private var messageHandler = Handler()
    private var time = 30000L

    var talkbutton: Button? = null
    var textview: TextView? = null
    protected var myHandler: Handler? = null
    var receivedMessageNumber = 1
    var sentMessageNumber = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_sports_game)

        pulseAnimation.run()
        // start
        btnStart.setOnClickListener {
            talkClick()
            timer()
            runner.visibility = View.VISIBLE
            bahn.visibility = View.INVISIBLE
            btnStart.visibility = View.INVISIBLE
            circleStart.visibility = View.INVISIBLE
            circleStart2.visibility = View.INVISIBLE
            bgWaiting.visibility = View.INVISIBLE
            animationHandler.removeCallbacks(pulseAnimation)
        }

        // play again
        btnAgain.setOnClickListener {
            timer()
            runner.visibility = View.VISIBLE
            bahn.visibility = View.INVISIBLE
            circleAgain.visibility = View.INVISIBLE
            circleHome.visibility = View.INVISIBLE
            btnAgain.visibility = View.INVISIBLE
            btnHome.visibility = View.INVISIBLE
            bgWaiting.visibility = View.INVISIBLE
        }

        // go back home
        btnHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        //Create a message handler//
        myHandler = Handler { msg ->
            val stuff = msg.data
            messageText(stuff.getString("messageText"))
            true
        }

        //Register to receive local broadcasts, which we'll be creating in the next step//
        val messageFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver: SportsGameActivity.Receiver = SportsGameActivity().Receiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter)

    }

    //################################################

    private fun messageText(newInfo: String?) {
        /*
        if (newInfo!!.compareTo("") != 0) {
            textview!!.append("""$newInfo""".trimIndent()
            )
        }
         */
    }

    //Define a nested class that extends BroadcastReceiver//
    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            //Upon receiving each message from the wearable, display the following text//
            val message = "I just received a message from the wearable " + receivedMessageNumber++
            //textview!!.text = message
            Log.i(TAG, message)
        }
    }

    fun talkClick() {
        val message = "Sending message.... "
        //textview!!.text = message
        Log.i(TAG, message)
        //Sending a message can block the main UI thread, so use a new thread//
        NewThread("/my_path", message).start()
    }

    //Use a Bundle to encapsulate our message//
    fun sendMessage(messageText: String?) {
        val bundle = Bundle()
        bundle.putString("messageText", messageText)
        val msg = myHandler!!.obtainMessage()
        msg.data = bundle
        myHandler!!.sendMessage(msg)
    }

    internal inner class NewThread
    //Constructor for sending information to the Data Layer//
        (var path: String, var message: String) : Thread() {
        override fun run() {
            //Retrieve the connected devices, known as nodes//
            val wearableList = Wearable.getNodeClient(applicationContext).connectedNodes
            try {
                val nodes = Tasks.await(wearableList)
                for (node in nodes) {
                    val sendMessageTask =  //Send the message//
                        Wearable.getMessageClient(this@SportsGameActivity).sendMessage(node.id, path, message.toByteArray())
                    try {
                        //Block on a task and get the result synchronously//
                        val result = Tasks.await(sendMessageTask)
                        sendMessage("I just sent the wearable a message " + sentMessageNumber++)

                        //if the Task fails, then…..//
                    } catch (exception: ExecutionException) {
                        Log.i(TAG, "1")
                        //TO DO: Handle the exception//
                    } catch (exception: InterruptedException) {
                        Log.i(TAG, "2")
                        //TO DO: Handle the exception//
                    }
                }
            } catch (exception: ExecutionException) {
                Log.i(TAG, "3")
                //TO DO: Handle the exception//
            } catch (exception: InterruptedException) {
                Log.i(TAG, "4")
                //TO DO: Handle the exception//
            }
        }
    }

    //################################################
    // game countdown
    private fun timer() {
        object : CountDownTimer(time, 500) {

            private var almostDone = false
            private var quarterCheck = false
            private var halfCheck = false
            private var threeQuarterCheck = false

            override fun onTick(millisLeft: Long) {
                tvTime.text = (millisLeft / 1000).toString() + " "

                if(millisLeft <= (0.1*time).toLong() && !almostDone) {
                    Log.i(TAG, "90%: $millisLeft")
                    btnMessage.text = "90% over. Almost finished!"
                    almostDone = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.25*time).toLong() && !threeQuarterCheck) {
                    Log.i(TAG, "75%: $millisLeft")
                    btnMessage.text = "75% over. Hold on!"
                    threeQuarterCheck = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.5*time).toLong() && !halfCheck) {
                    Log.i(TAG, "50%: $millisLeft")
                    btnMessage.text = "50% over. Halftime!"
                    halfCheck = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.75*time).toLong() && !quarterCheck) {
                    Log.i(TAG, "25%: $millisLeft")
                    btnMessage.text = "25% over. Keep going!"
                    quarterCheck = true
                    showTimeMessage.run()
                }

            }

            override fun onFinish() {
                // time over

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
        }.start()
    }

    // show time message popups
    private var showTimeMessage = Runnable {
        val parent = findViewById<ViewGroup>(R.id.sportGame)
        val transition: Transition = Slide(Gravity.TOP)
        transition.duration = 1000
        transition.addTarget(R.id.btnMessage)
        TransitionManager.beginDelayedTransition(parent, transition)
        btnMessage.visibility = View.VISIBLE
        messageHandler.postDelayed(removeTimeMessage, 2000)
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