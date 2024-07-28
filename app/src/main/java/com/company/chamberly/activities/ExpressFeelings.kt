package com.company.chamberly.activities

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.company.chamberly.R
import com.company.chamberly.databinding.ActivityExpressFeelingsBinding
import com.company.chamberly.fragments.JournalFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.IOException
import org.json.JSONArray
import org.json.JSONObject


// ExpressFeelingsActivity.kt
class ExpressFeelings : AppCompatActivity() {

    //private lateinit var binding: ActivityExpressFeelingsBinding
    private lateinit var selectedDate: String
    private val openAiApiKey = "sk-proj-JM48cpfuMzo6ifrYIlC4T3BlbkFJlH7OnfMQmdPxngrOP2sx"
    private var isEditMode: Boolean = false
    private lateinit var dottedProgressBar: DottedCircularProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //binding = ActivityExpressFeelingsBinding.inflate(layoutInflater)
        setContentView(R.layout.activity_express_feelings)

        selectedDate = intent.getStringExtra("selectedDate") ?: ""
        val add_button=findViewById<Button>(R.id.add_button)
        val journalEditText=findViewById<EditText>(R.id.feelings_input)
        dottedProgressBar = findViewById(R.id.dotted_progress_bar)
        add_button.setOnClickListener {
            val journalEntry = journalEditText.text.toString()
            if (journalEntry.isNotEmpty()) {
                dottedProgressBar.visibility = View.VISIBLE
                dottedProgressBar.startAnimation()
                processJournalEntry(journalEntry)
            }
        }
        val emotionText = intent.getStringExtra("emotionText")

        // Set the retrieved emotionText to the EditText
        if (!emotionText.isNullOrEmpty()) {
            journalEditText.setText(emotionText)
        }
        isEditMode = intent.getBooleanExtra("isEditMode", false)
        if (isEditMode) {
            add_button.text = "Apply Changes"
        }

    }


    private fun processJournalEntry(journalEntry: String) {
        val moodPrompt = "From 0 to 4, how good am I feeling today (4 is feeling great), based on my following journal data \"$journalEntry\". Reply only with an integer value. Don't say anything else"
        val emotionsPrompt = "Write 2 emotions I am likely feeling based on the following text: \"$journalEntry\". Don't write 2 emotions unless you are sure the second one is clearly the case. Write your answer separated by a comma for each emotion. Each emotion must start with a capital letter. You may translate the emotion to an appropriate language based on the text provided."
        val summaryPrompt = "Summarize shortly what I did and felt based on the following text \"$journalEntry\". Write each thing I did, happened to me or felt separated by a comma, with first character being a capital letter. Focus on the major stuff that I did in the day and things that happened to me, for example, don't include everyday behavior such as, 'woke up, hit snooze, took a shower, packed my bag, used mobile phone for a bit' But rather more major things. If you are not sure then you can generalize. You can add emoji(s) for each item you list, but the emoji must accurately represent the item."

        val prompts = listOf(
            Pair("You are a mood tracker.", moodPrompt),
            Pair("You are a mood tracker.", emotionsPrompt),
            Pair("You are a mood tracker.", summaryPrompt)
        )

        lifecycleScope.launch {
            val results = prompts.map { (systemPrompt, userPrompt) ->
                callOpenAi(systemPrompt, userPrompt)
            }

            val emoji = results[0]
            val emotions = results[1]
            val summary = results[2]

            saveJournalEntry(selectedDate, emoji, emotions, summary,journalEntry)

        }
    }

    private suspend fun callOpenAi(systemPrompt: String, userPrompt: String): String {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val json = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                })
                put("max_tokens", 100)
                put("temperature", 0.7)
                put("n", 1)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(RequestBody.create("application/json".toMediaTypeOrNull(), json.toString()))
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d("ExpressFeelingsActivity", "Request JSON: $json")

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d("ExpressFeelingsActivity", "Response Code: ${response.code}")
                Log.d("ExpressFeelingsActivity", "Response Body: $responseBody")

                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonResponse = JSONObject(responseBody ?: throw IOException("Empty response"))
                jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    .getString("content").trim()
            }
        }
    }

    private fun saveJournalEntry(date: String, emoji: String, emotions: String, summary: String, emotionText: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid

        val firestore = FirebaseFirestore.getInstance()
        val userDocRef = firestore.collection("Journal").document(uid)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            if (snapshot.exists()) {
                // Document exists, update or create the date entry
                val journalData = snapshot.data ?: mutableMapOf<String, Any>()
                val dateData = mapOf(
                    "Emoji" to emoji,
                    "Emotions" to emotions,
                    "Summary" to summary,
                    "EmotionText" to emotionText
                )
                journalData[date] = dateData
                transaction.update(userDocRef, journalData)
            } else {
                // Create a new document with the date entry
                val dateData = mapOf(
                    "Emoji" to emoji,
                    "Emotions" to emotions,
                    "Summary" to summary,
                    "EmotionText" to emotionText
                )
                val journalData = mapOf(
                    date to dateData
                )
                transaction.set(userDocRef, journalData)
            }
        }.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d("ExpressFeelingsActivity", "Journal document created/updated for user $uid")
                dottedProgressBar.stopAnimation()
                dottedProgressBar.visibility = View.GONE
                finish()


            } else {
                Log.e("ExpressFeelingsActivity", "Failed to create/update journal document", it.exception)
                dottedProgressBar.stopAnimation()
                dottedProgressBar.visibility = View.GONE
                finish()
            }
        }
    }



}