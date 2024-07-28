package com.company.chamberly.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class JournalViewModel : ViewModel() {
    private val _journalEntries = MutableLiveData<List<JournalEntry>>()
    val journalEntries: LiveData<List<JournalEntry>> get() = _journalEntries

    fun fetchJournalEntries(uid: String) {
        val firestore = FirebaseFirestore.getInstance()
        val userDocRef = firestore.collection("Journal").document(uid)

        userDocRef.get().addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                val data = document.data ?: return@addOnSuccessListener
                val entries = data.map { (date, entry) ->
                    val entryMap = entry as Map<*, *>
                    JournalEntry(
                        date = date,
                        emoji = entryMap["Emoji"] as String,
                        emotions = entryMap["Emotions"] as String,
                        summary = entryMap["Summary"] as String,
                        emotionText = entryMap["EmotionText"] as String
                    )
                }.sortedByDescending { parseDate(it.date) } // Sort by date in descending order

                _journalEntries.value = entries
            }
        }
    }

    private fun parseDate(date: String): Date {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.parse(date) ?: Date(0)
    }
}

data class JournalEntry(
    val date: String,
    var emoji: String,
    var emotions: String,
    var summary: String,
    val emotionText: String
)