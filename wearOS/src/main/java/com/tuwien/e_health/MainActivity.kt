package com.tuwien.e_health

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.fitness.request.OnDataPointListener
import com.google.android.gms.fitness.request.SensorRequest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.android.synthetic.main.activity_main.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit


class MainActivity : Activity() {

    private val fitnessOptions: GoogleSignInOptionsExtension = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_LOCATION_SAMPLE, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_WORKOUT_EXERCISE, FitnessOptions.ACCESS_READ)
        .build()

    private val RC_SIGNIN = 0
    private val RC_PERMISSION = 1
    private var messageHandler = Handler()
    private var gameStatus = GameStatus.NOT_RUNNING


    enum class GameStatus {
        RUNNING, NOT_RUNNING
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var bpmValue = 80
        /* TODO: for testing (for everyone who doesn't have a smartwatch), remove at the end
        buttonPanel.setOnClickListener {
            bpmValue++
            if(bpmValue > 180) {
                bpmValue = 80
            }
            val dataPath = "/eHealth"
            SendMsg(dataPath, bpmValue.toString()).start()
        }
         */

        // change button text if account saved from previous session
        if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            buttonPanelLogIn.text = "Log Out"
        } else {
            buttonPanelLogIn.text = "Log In"
        }

        buttonPanelLogIn.setOnClickListener {
            if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                signIn()
            } else {
                logOut()
            }
        }

        btnPlay.setOnClickListener {
            if(GoogleSignIn.getLastSignedInAccount(this) != null) {
                if (gameStatus == GameStatus.NOT_RUNNING) {
                    sendStartSignal()
                } else if (gameStatus == GameStatus.RUNNING) {
                    sendStopSignal()
                }
            }
        }

        // register to receive local broadcasts
        val newFilter = IntentFilter(Intent.ACTION_SEND)
        val messageReceiver = Receiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, newFilter)

        // check for android permissions
        checkPermissions()
    }

    // tell smartphone that sampling starts
    private fun sendStartSignal() {
        val message = "Start"
        //findFitnessDataSources()
        SendMsg("/eHealth", message).start()
    }

    // tell smartphone that sampling stops
    private fun sendStopSignal() {
        val message = "Stop"
        //removeListener()
        SendMsg("/eHealth", message).start()
    }

    // check for android permissions
    private fun checkPermissions() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                RC_PERMISSION
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                RC_PERMISSION
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                RC_PERMISSION
            )
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                RC_PERMISSION
            )
        }
    }

    // receive message from smartphone
    inner class Receiver : BroadcastReceiver() {
        private lateinit var lastMsgTimeStamp: LocalDateTime

        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Incoming msg: " + intent.getStringExtra("message").toString())
            if (intent.getStringExtra("message").toString() == "Start") {
                findFitnessDataSources()
                lastMsgTimeStamp = LocalDateTime.now()
                timeChecker.run()
                btnPlay.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.ic_baseline_pause_24)
                gameStatus = GameStatus.RUNNING
            } else if (intent.getStringExtra("message").toString() == "Stop" && gameStatus == GameStatus.RUNNING) {
                removeListener()
                messageHandler.removeCallbacks(timeChecker)
                btnPlay.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, R.drawable.ic_baseline_play_arrow_24)
                gameStatus = GameStatus.NOT_RUNNING
            } else if (intent.getStringExtra("message").toString() == "Stop" && gameStatus == GameStatus.NOT_RUNNING) {
                removeListener()
                gameStatus = GameStatus.NOT_RUNNING
            } else {
                lastMsgTimeStamp = LocalDateTime
                    .parse(intent.getStringExtra("message").toString(), DateTimeFormatter.ISO_DATE_TIME)
            }
        }

        // pulsing animation for start button
        private var timeChecker = object : Runnable {
            override fun run() {
                if(::lastMsgTimeStamp.isInitialized) {
                    Log.i(TAG, "$lastMsgTimeStamp " + LocalDateTime.now())
                    if (lastMsgTimeStamp < LocalDateTime.now().minusSeconds(10)) {
                        // no alive msg from smartwatch since given time -> turn off sampling
                        //removeListener()
                        Log.i(TAG, "I haven't received a msg since $lastMsgTimeStamp")
                        messageHandler.removeCallbacks(this)
                        return
                    }
                }
                messageHandler.postDelayed(this, 1000)
            }
        }
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

    private fun logOut() {
        // log out of Google Account

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        mGoogleSignInClient.signOut()
        buttonPanelLogIn.text = "Log In"
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

        val acct = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        if (acct != null) {
            Log.i(TAG, "account signed in")
            Log.i(TAG, "personEmail: " + acct.email)
            Log.i(TAG, "personName: " + acct.displayName)
            Log.i(TAG, "personId: " + acct.id)
        } else {
            Log.i(TAG, "no account")
        }
    }

    private fun oAuthPermissionsApproved() =
        GoogleSignIn.hasPermissions(
            GoogleSignIn.getAccountForExtension(this, fitnessOptions),
            fitnessOptions
        )

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
            val account = completedTask.getResult(ApiException::class.java)
            accountInfo()
            buttonPanelLogIn.text = "Log Out"
        } catch (e: ApiException) {
            Log.w(TAG, "signInResult:failed code=" + e.statusCode)
        }
    }

    private var dataPointListener: OnDataPointListener? = null


    // find data sources for specified data type
    private fun findFitnessDataSources() {
        Log.i(TAG, "Finding data sources ..")
        Fitness.getSensorsClient(this, getGoogleAccount())
            .findDataSources(
                DataSourcesRequest.Builder()
                    .setDataTypes(DataType.TYPE_HEART_RATE_BPM)
                    .setDataSourceTypes(DataSource.TYPE_RAW)
                    .build()
            )
            .addOnSuccessListener { dataSources ->
                for (dataSource in dataSources) {
                    Log.i(TAG, "Data source found: $dataSource")
                    Log.i(TAG, "Data Source type: " + dataSource.dataType.name)
                    if (dataSource.dataType == DataType.TYPE_HEART_RATE_BPM) {
                        Log.i(TAG, "Data source found!  Registering.")
                        registerFitnessDataListener(dataSource, DataType.TYPE_HEART_RATE_BPM)
                    }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "failed", e) }
    }

    // register a listener on incoming data of given data source
    private fun registerFitnessDataListener(dataSource: DataSource, dataType: DataType) {
        Log.i(TAG, "Registering Listener on data source ..")
        dataPointListener = OnDataPointListener { dataPoint ->
            for (field in dataPoint.dataType.fields) {
                val value = dataPoint.getValue(field)
                Log.i(TAG, "Detected DataPoint field: ${field.name}")
                Log.i(TAG, "Detected DataPoint value: $value")
                SendMsg("/eHealth", value.toString()).start()
            }
        }
        Fitness.getSensorsClient(this, getGoogleAccount())
            .add(
                SensorRequest.Builder()
                    .setDataSource(dataSource)
                    .setDataType(dataType)
                    .setSamplingRate(1, TimeUnit.SECONDS)
                    .build(),
                dataPointListener!!
            )
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "Listener registered!")
                } else {
                    Log.e(TAG, "Listener not registered.", task.exception)
                }
            }
    }

    // remove listener on data source
    private fun removeListener() {
        Fitness.getSensorsClient(this, GoogleSignIn.getAccountForExtension(this, fitnessOptions))
            .remove(dataPointListener!!)
            .addOnSuccessListener {
                Log.i(TAG, "Listener was removed!")
            }
            .addOnFailureListener {
                Log.i(TAG, "Listener was not removed.")
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
                        //Log.i(TAG, "Msg sent successfully")
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