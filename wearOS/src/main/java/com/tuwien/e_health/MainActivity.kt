package com.tuwien.e_health

import android.app.Activity
import android.os.Bundle
import com.tuwien.e_health.databinding.ActivityMainBinding

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutionException


class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding
    private var textview: TextView? = null
    private var textView: TextView? = null
    var receivedMessageNumber = 1
    var sentMessageNumber = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.text)

        //Create an OnClickListener//
        buttonPanel.setOnClickListener(View.OnClickListener {
            val onClickMessage = "I just sent the handheld a message " + sentMessageNumber++
            //textview!!.setText(onClickMessage)
            Log.i(TAG, onClickMessage)

            //Use the same path//
            val dataPath = "/my_path"
            SendMessage(dataPath, onClickMessage).start()
        })

        //Register to receive local broadcasts, which we'll be creating in the next step//
        val newFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver = Receiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, newFilter)
    }

    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            //Display the following when a new message is received//
            val onMessageReceived =
                "I just received a message from the handheld " + receivedMessageNumber++
            //textView!!.setText(onMessageReceived)
            Log.i(TAG, onMessageReceived)
        }
    }

    internal inner class SendMessage     //Constructor for sending information to the Data Layer//
        (var path: String, var message: String) : Thread() {
        override fun run() {
            //Retrieve the connected devices//
            val nodeListTask = Wearable.getNodeClient(getApplicationContext()).connectedNodes
            try {
                //Block on a task and get the result synchronously//
                val nodes = Tasks.await(nodeListTask)
                for (node in nodes) {
                    //Send the message///
                    val sendMessageTask = Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, path, message.toByteArray())
                    try {
                        val result = Tasks.await(sendMessageTask)
                        //Handle the errors//
                    } catch (exception: ExecutionException) {
                        Log.i(TAG, "5")
                    } catch (exception: InterruptedException) {
                        Log.i(TAG, "6")
                    }
                }
            } catch (exception: ExecutionException) {
                Log.i(TAG, "7")
            } catch (exception: InterruptedException) {
                Log.i(TAG, "8")
            }
        }
    }
}