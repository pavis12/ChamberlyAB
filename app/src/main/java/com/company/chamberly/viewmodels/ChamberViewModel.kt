package com.company.chamberly.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.company.chamberly.OkHttpHandler
import com.company.chamberly.models.Message
import com.company.chamberly.models.UserRatingModel
import com.company.chamberly.models.toMap
import com.company.chamberly.states.ChamberState
import com.company.chamberly.utils.logEvent
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.json.JSONObject

class ChamberViewModel(application: Application): AndroidViewModel(application = application) {

    private val _chamberState = MutableLiveData<ChamberState>()
    val chamberState: LiveData<ChamberState> = _chamberState

    private val _messages = MutableLiveData<MutableList<Message>>()
    val messages: LiveData<List<Message>> = _messages as LiveData<List<Message>>

    private val realtimeDatabase = Firebase.database
    private val firestore = Firebase.firestore
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(getApplication())
    private val sharedPreferences = application.getSharedPreferences("cache", Context.MODE_PRIVATE)

    fun setChamber(chamberID: String, UID: String) {
        Log.d("CHAMBER1", chamberID)
        firestore.collection("GroupChatIds")
            .document(chamberID)
            .get()
            .addOnSuccessListener { chamberSnapshot ->
                Log.d("CHAMBER1", chamberSnapshot.data.toString())
                if(chamberSnapshot.data != null) {
                    _chamberState.value = getChamberFromSnapshot(chamberSnapshot.data!!)
                    Log.d("CHAMBER", _chamberState.value.toString())
                    removeNotificationKey(UID)
                    getMessages()
                }
            }
            .addOnFailureListener {
                Log.d("CHAMBEREXCEPTION", it.toString())
            }
    }

    private fun getChamberFromSnapshot(chamberData: Map<String, Any>): ChamberState {
        return ChamberState(
            chamberID = chamberData["groupChatId"].toString(),
            chamberTitle = chamberData["groupTitle"].toString(),
            members = chamberData["members"] as List<String>
        )
    }

    fun getMessages() {
        val messagesQuery =
            realtimeDatabase
                .getReference(_chamberState.value!!.chamberID)
                .child("messages")
                .orderByKey()
                .limitToLast(40)

        messagesQuery.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _messages.value = mutableListOf()
                for(childSnapshot in snapshot.children) {
                    if (childSnapshot.value is Map<*, *>) {
                        try {
                            val message = childSnapshot.getValue(Message::class.java)
                            if (message != null) {
                                if (message.message_type == "custom" && message.message_content == "gameCard") {
                                    message.message_content = message.game_content
                                } else if (message.message_type == "photo") {
                                    message.message_content = "Images are not available to display on Android"
                                }
                                addMessage(message)
                            }
                        } catch (e: Exception) {
                            Log.d("EXCEPTION", e.toString())
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // TODO: Network error, will handle later
            }
        })

        messagesQuery
            .addChildEventListener(object: ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    try {
                        val message = snapshot.getValue(Message::class.java)
                        if (message != null) {
                            if (
                                message.message_type == "custom" &&
                                message.message_content == "gameCard"
                            ) {
                                message.message_content = message.game_content
                            } else if (message.message_type == "photo") {
                                message.message_content = "Images are not available to display on Android."
                            }
                            if(!(messages.value!!.contains(message))) {
                                addMessage(message = message)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    try {
                        val message = snapshot.getValue(Message::class.java)
                        if (message != null) {
                            if (
                                message.message_type == "custom" &&
                                message.message_content == "gameCard"
                            ) {
                                message.message_content = message.game_content
                            } else if (message.message_type == "photo") {
                                message.message_content = "Images are not available to display on Android"
                            }
                            changeMessage(message)
                        }
                    } catch (e: Exception) {
                        Log.e("Message update error", e.message.toString())
                    }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    try {
                        val message = snapshot.getValue(Message::class.java)
                        if(message != null) {
                            removeMessage(message)
                        }
                    } catch (e: Exception) {
                        Log.e("Message deletion error", e.message.toString())
                    }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {

                }

                override fun onCancelled(error: DatabaseError) {

                }

            })
    }

    fun sendMessage(
        message: Message,
        successCallback: () -> Unit = {}
    ) {

        val messageRef = realtimeDatabase
            .getReference(_chamberState.value!!.chamberID)
            .child("messages")

        val messageID = messageRef.push().key
        message.message_id = messageID!!
        messageRef
            .child(messageID)
            .setValue(message.toMap())
            .addOnSuccessListener {
                successCallback()
                sendNotificationToInactiveMembers()
            }

        logEventToAnalytics(
            eventName = "message_sent",
            params = hashMapOf(
                "groupChatId" to chamberState.value!!.chamberID
            )
        )
    }

    private fun addMessage(message: Message) {
        val updatedMessages = _messages.value!!
        updatedMessages.add(message)
        _messages.postValue(updatedMessages)
    }

    private fun changeMessage(message: Message) {
        val updatedMessages = _messages.value!!
        val index = updatedMessages.indexOfFirst {
            it.message_id == message.message_id
        }

        if (index != -1) {
            updatedMessages[index] = message
            _messages.postValue(updatedMessages)
        }
    }

    private fun removeMessage(message: Message) {
        val updatedMessages = _messages.value!!
        val index = updatedMessages.indexOfFirst {
            it.message_id == message.message_id
        }
        if(index != -1) {
            updatedMessages.removeAt(index)
            _messages.postValue(updatedMessages)
        }
    }

    fun react(messageId: String, reaction: String) {
        realtimeDatabase
            .reference
            .child(_chamberState.value!!.chamberID)
            .child("messages")
            .child(messageId)
            .child("reactedWith")
            .setValue(reaction)
    }

    fun rateUser(
        userToRate: String,
        starRating: Double,
        UID: String
    ) {
        var reviewCountFlag = true
        var starRatingChange = starRating

        val collectionRef = firestore.collection("StarReviews")

        val review = UserRatingModel(
            from = UID,
            to = userToRate,
            stars = starRating
        )

        collectionRef
            .whereEqualTo("To", userToRate)
            .whereEqualTo("From", UID)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val userReview = documents.documents[0]
                    starRatingChange -= userReview.getDouble("Stars")!!
                    reviewCountFlag = false
                }

                collectionRef
                    .whereEqualTo("To", userToRate)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { previousRatings ->
                        val latestReview = previousRatings.documents.getOrNull(0)
                        var totalStars = latestReview?.getDouble("TotalStars") ?: 0.0
                        var reviewsCount = latestReview?.getLong("ReviewsCount") ?: 0

                        totalStars += starRatingChange

                        if(reviewCountFlag) {
                            reviewsCount++
                        }

                        logEventToAnalytics(
                            eventName = "user_rated",
                            params = hashMapOf(
                                "user_rated" to starRating
                            )
                        )
                        review.totalStars = totalStars
                        review.reviewCount = reviewsCount.toInt()
                        review.averageStars = totalStars / reviewsCount
                        collectionRef.add(review.toMap())
                    }
            }
    }

    fun reportUser(report: HashMap<String, Any>) {
        firestore
            .collection("Reports")
            .add(report)
            .addOnSuccessListener {
                if(report["selfReport"] as Boolean) {
                    Toast.makeText(
                        getApplication(),
                        "User reported",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    fun sendExitMessage(message: Message, callback: () -> Unit) {
        val messageRef =
            realtimeDatabase
                .getReference(chamberState.value!!.chamberID)
                .child("messages")
        val messageId = messageRef.push().key!!

        messageRef
            .child(messageId)
            .setValue(message.copy(message_id = messageId).toMap())
            .addOnSuccessListener { callback() }
    }

    fun exitChamber(UID: String) {
        firestore
            .collection("GroupChatIds")
            .document(chamberState.value!!.chamberID)
            .update("members", FieldValue.arrayRemove(UID))
            .addOnSuccessListener {
                realtimeDatabase
                    .reference
                    .child(chamberState.value!!.chamberID)
                    .child("users")
                    .child("members")
                    .child(UID)
                    .removeValue()
            }

        logEventToAnalytics(eventName = "ended_chat")
    }

    fun clear() {
        _messages.value?.clear()
        _chamberState.value = ChamberState(
            chamberID = "",
            chamberTitle = ""
        )
    }

    private fun sendNotificationToInactiveMembers() {
        val notificationKey =
            sharedPreferences
                .getString("notificationKey", "") ?: ""
        val notificationPayload = JSONObject()
        realtimeDatabase
            .reference
            .child(_chamberState.value!!.chamberID)
            .child("users")
            .child("members")
            .get()
            .addOnSuccessListener { membersSnapshot ->
                for(snapshot in membersSnapshot.children) {
                    val token = snapshot.child("notificationKey").value as String?
                    if(!token.isNullOrBlank() && token != notificationKey) {
                        try {
                            notificationPayload.put(
                                "title",
                                sharedPreferences.getString("displayName", "")
                            )
                            notificationPayload.put(
                                "body",
                                "sent you a message"
                            )
                            notificationPayload.put(
                                "groupChatId",
                                _chamberState.value!!.chamberID
                            )
                            OkHttpHandler(notificationPayload, token).execute()
                        } catch (e: Exception) {
                            Log.e("Error sending notifications", e.message.toString())
                        }
                    }
                }
            }
    }

    fun addNotificationKey(
        UID: String,
        notificationKey: String
    ) {
        if(chamberState.value != null) {
            realtimeDatabase
                .reference
                .child(chamberState.value!!.chamberID)
                .child("users")
                .child("members")
                .child(UID)
                .child("notificationKey")
                .setValue(notificationKey)
        }

    }

    fun removeNotificationKey(
        UID: String
    ) {
        realtimeDatabase
            .reference
            .child(_chamberState.value!!.chamberID)
            .child("users")
            .child("members")
            .child(UID)
            .child("notificationKey")
            .removeValue()
    }

    fun logEventToAnalytics(eventName: String, params: HashMap<String, Any> = hashMapOf()) {
        params["UID"] = sharedPreferences.getString("uid", "") ?: ""
        params["name"] = sharedPreferences.getString("displayName", "") ?: ""
        Log.d("LOG", params.toString())
        logEvent(
            firebaseAnalytics = firebaseAnalytics,
            eventName = eventName,
            params = params
        )
    }
}