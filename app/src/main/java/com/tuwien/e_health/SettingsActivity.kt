package com.tuwien.e_health

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    private val fitnessOptions: GoogleSignInOptionsExtension = FitnessOptions.builder()
        .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
        .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
        .addDataType(DataType.TYPE_WORKOUT_EXERCISE, FitnessOptions.ACCESS_READ)
        .build()

    private lateinit var auth: FirebaseAuth
    private val REQ_ONE_TAP = 3 // Can be any integer unique to the Activity
    private var showOneTapUI = true
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var database: DatabaseReference
    private val RC_SIGNIN = 0
    private val RC_PERMISSION = 1
    private var testCounter = 0
    private var average6hHeartRate = 0.0;
    private var knownUsers : MutableSet<String> = mutableSetOf()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        this.getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_settings)

        // gets instance of DatabaseReference object
        database = Firebase.database("https://e-health-347815-default-rtdb.europe-west1.firebasedatabase.app").reference

        // adds listener that reads all userID's stored in database and adds them to knownUsers
        addDatabaseEventListener(database.child("users"))

        // gets instance of FirebaseAuth object
        auth = Firebase.auth

        val textBtnSignInOut: Button = findViewById(R.id.btnSignInOut) as Button

        if(GoogleSignIn.getLastSignedInAccount(this) == null) {
            textBtnSignInOut.text = "Log \n in"
        } else if (GoogleSignIn.getLastSignedInAccount(this) != null) {
            textBtnSignInOut.text = "Log out"
        }

        btnSignInOut.setOnClickListener {
            if (GoogleSignIn.getLastSignedInAccount(this) == null) {
                textBtnSignInOut.text = "Log out"
                signIn()

            } else if (GoogleSignIn.getLastSignedInAccount(this) != null) {
                textBtnSignInOut.text = "Log \n in"
                tvAccountName.text = "No account singed in"
                tvAccountEmail.text = "-"
                logOut()
                Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setInfoText()
    }

    private fun setInfoText() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            tvAccountName.setText("Hello " + user.displayName)
            tvAccountEmail.setText(user.email)
        }
    }

    private fun reqPermissions() {
        // request Permissions specified in fitnessOptions

        GoogleSignIn.requestPermissions(
            this,
            RC_PERMISSION,
            getGoogleAccount(),
            fitnessOptions)
    }

    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            Log.i(TAG, "account signed in")
            Log.i(TAG, "personEmail: " + user.email)
            Log.i(TAG, "personName: " + user.displayName)
            Log.i(TAG, "personId: " + user.uid)
        }else{
            Log.i(TAG, "no account")
        }
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
                    Log.e(TAG, "Couldn't start One Tap UI: ${e.localizedMessage}")
                }
            }
            .addOnFailureListener(this) { e ->
                // No saved credentials found. Launch the One Tap sign-up flow, or
                // do nothing and continue presenting the signed-out UI.
                Log.d(TAG, e.localizedMessage)
            }
    }

    private fun oldGoogleAccSignIn() {
        // Old sign in function, still needed. If this were left out
        // GoogleSignIn.getLastSignedInAccount(this) would return null.
        // See comment in same function in MainActivity class.
        // TODO: Make this unnecessary.

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = mGoogleSignInClient.getSignInIntent()
        startActivityForResult(signInIntent, RC_SIGNIN)
    }

    private fun logOut() {
        // log out of Google Account

        Firebase.auth.signOut()


        // Old sign out function code, still needed. See comment in MainActivity's googleAccSignIn function.
        // TODO: Delete this as well as soon as it's figured out how to work without it.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        mGoogleSignInClient.signOut()
    }

    private fun writeNewUserToDatabase(user: FirebaseUser) {
        val newUser = UserData(user.email,user.displayName)

        database.child("users").child(user.uid).setValue(newUser)
            .addOnSuccessListener {
                Log.i(TAG,"new user ${newUser.email} created in database")
            }
            .addOnFailureListener { e ->
                Log.i(TAG, "there was a problem creating the new user ${newUser.email}", e)
            }
    }

    private fun addDatabaseEventListener(databaseReference: DatabaseReference) {
        val databaseListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val database = dataSnapshot.children
                for(uid in database) {
                    uid.key?.let {
                        knownUsers.add(it)
                        Log.w(TAG, "Database listener retrieved data: " + it)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException())
            }
        }
        databaseReference.addValueEventListener(databaseListener)
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
                                        Log.d(TAG, "signInWithCredential:success")
                                        val user = auth.currentUser
                                        oldGoogleAccSignIn()
                                        updateUI(user)
                                        if (user != null) {
                                            if (knownUsers.contains(user.uid)) {
                                                Log.d(TAG,"user already exists in database")
                                            } else {
                                                writeNewUserToDatabase(user)
                                            }
                                        } else {
                                            Log.d(TAG,"no user found")
                                        }
                                        Toast.makeText(this, "Signed In", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // If sign in fails, display a message to the user.
                                        Log.w(TAG, "signInWithCredential:failure", task.exception)
                                        updateUI(null)
                                    }
                                }
                        }
                        else -> {
                            // Shouldn't happen.
                            Log.d(TAG, "No ID token")
                        }
                    }

                } catch (e: ApiException) {
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> {
                            Log.d(TAG, "One-tap dialog was closed.")
                            // Don't re-prompt the user.
                            showOneTapUI = false
                        }
                        CommonStatusCodes.NETWORK_ERROR -> {
                            Log.d(TAG, "One-tap encountered a network error.")
                            // Try again or just ignore.
                        }
                        else -> {
                            Log.d(TAG, "Couldn't get credential from result." +
                                    " (${e.localizedMessage})")
                        }
                    }
                }
            }
        }

        // Old sign in function code, still needed. See comment in MainActivity's googleAccSignIn function.
        // TODO: Delete this as well as soon as it's figured out how to work without it.
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
            setInfoText()
        } catch (e: ApiException) {
            Log.w(ContentValues.TAG, "signInResult:failed code=" + e.statusCode)
        }
    }
}