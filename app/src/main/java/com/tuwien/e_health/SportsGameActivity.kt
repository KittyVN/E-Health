package com.tuwien.e_health

import android.content.Intent
import android.nfc.Tag
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.google.android.material.tabs.TabLayout
import android.content.ContentValues.TAG
import android.view.Gravity
import android.view.ViewGroup
import androidx.transition.Slide
import androidx.transition.Transition
import androidx.transition.TransitionManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_sports_game.*

class SportsGameActivity : AppCompatActivity() {

    private var animationHandler = Handler()
    private var messageHandler = Handler()
    private var time = 30000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_sports_game)

        pulseAnimation.run()
        // start
        btnStart.setOnClickListener {
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
            val Intent = Intent(this, MainActivity::class.java)
            startActivity(Intent)
        }
    }


    // game countdown
    private fun timer() {
        object : CountDownTimer(time, 500) {

            private var almostDone = false
            private var quarterCheck = false
            private var halfCheck = false
            private var threeQuarterCheck = false

            override fun onTick(millisLeft: Long) {
                tvTime.setText((millisLeft / 1000).toString() + " ")

                if(millisLeft <= (0.1*time).toLong() && !almostDone) {
                    Log.i(TAG, "90%: $millisLeft")
                    btnMessage.setText("90% over. Almost finished!")
                    almostDone = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.25*time).toLong() && !threeQuarterCheck) {
                    Log.i(TAG, "75%: $millisLeft")
                    btnMessage.setText("75% over. Hold on!")
                    threeQuarterCheck = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.5*time).toLong() && !halfCheck) {
                    Log.i(TAG, "50%: $millisLeft")
                    btnMessage.setText("50% over. Halftime!")
                    halfCheck = true
                    showTimeMessage.run()
                } else if (millisLeft <= (0.75*time).toLong() && !quarterCheck) {
                    Log.i(TAG, "25%: $millisLeft")
                    btnMessage.setText("25% over. Keep going!")
                    quarterCheck = true
                    showTimeMessage.run()
                }

            }

            override fun onFinish() {
                // time over

                tvTime.setText("00:00 ")
                btnStart.setText("Run again")
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