package com.tuwien.e_health

import android.app.Activity
import android.os.Bundle

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.ExecutionException


class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonPanel.setOnClickListener {
            val bpmValue = "87"
            val dataPath = "/my_path"
            SendMsg(dataPath, bpmValue).start()
        }

        // register to receive local broadcasts
        val newFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver = Receiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, newFilter)
    }

    // receive message from smartphone
    inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Incoming msg: " + intent.getStringExtra("message").toString())
        }
    }

    // send message to smartphone
    internal inner class SendMsg
        (private var path: String, private var message: String) : Thread() {
        override fun run() {
            // Retrieve the connected devices (nodes)
            val nodeListTask = Wearable.getNodeClient(getApplicationContext()).connectedNodes
            try {
                val nodes = Tasks.await(nodeListTask)
                for (node in nodes) {
                    // Send the message
                    val sendMessageTask = Wearable.getMessageClient(this@MainActivity)
                        .sendMessage(node.id, path, message.toByteArray())
                    try {
                        val result = Tasks.await(sendMessageTask)
                        Log.i(TAG, "Msg sent successfully")
                    } catch (exception: InterruptedException) {
                        exception.toString()
                    }
                }
            } catch (exception: ExecutionException) {
                exception.toString()
            }
        }
    }
}