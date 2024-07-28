package com.company.chamberly.adapters

import android.app.AlertDialog
import android.icu.util.Calendar
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.company.chamberly.R
import com.company.chamberly.viewmodels.JournalEntry
import com.google.android.flexbox.FlexboxLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class JournalAdapter(
    private val entries: List<JournalEntry>,
    private val onEditClick: (JournalEntry, Boolean) -> Unit, // Update to accept two parameters
    private val onDeleteClick: (JournalEntry) -> Unit
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_journal_entry, parent, false)
        return JournalViewHolder(view, onEditClick, onDeleteClick) // Pass the correct lambda
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount() = entries.size

    class JournalViewHolder(
        itemView: View,
        private val onEditClick: (JournalEntry, Boolean) -> Unit,
        private val onDeleteClick: (JournalEntry) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val emojiView: ImageView = itemView.findViewById(R.id.emojiView)
        private val emotionTextView: TextView = itemView.findViewById(R.id.emotionEditText)
        private val summaryContainer: FlexboxLayout = itemView.findViewById(R.id.summaryContainer)
        private val editIcon: ImageView = itemView.findViewById(R.id.editIcon)
        private val firestore = FirebaseFirestore.getInstance()
        private val auth = FirebaseAuth.getInstance()
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)

        private val scrollView: ScrollView = itemView.findViewById(R.id.scrollView)

        init {
            // Prevent parent scrolling when interacting with the ScrollView
            scrollView.setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                v.onTouchEvent(event)
                true
            }
        }

        fun bind(entry: JournalEntry) {
            emojiView.setImageResource(getEmojiResource(entry.emoji))
            emotionTextView.text = entry.emotions
            dateTextView.text = getFormattedDate(entry.date)

            summaryContainer.removeAllViews()
            entry.summary.split(",").forEach { summaryItem ->
                val textView = TextView(itemView.context).apply {
                    text = summaryItem.trim()
                    setBackgroundResource(R.drawable.summary_background)
                    setPadding(18, 14, 18, 14)
                    setTextColor(context.resources.getColor(android.R.color.black))
                    layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(8, 8, 8, 8)
                    }
                    setOnClickListener {
                        showEditSummaryDialog(entry, summaryItem)
                    }
                }
                summaryContainer.addView(textView)
            }


            emojiView.setOnClickListener {
                showEmojiPopup(entry)
            }

            emotionTextView.setOnClickListener {
                showEditDialog(entry)
            }

            itemView.setOnClickListener {
                showPopup(entry)
            }
        }
        private fun getFormattedDate(dateString: String): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = dateFormat.parse(dateString)

            val calendar = Calendar.getInstance()
            calendar.time = date

            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

            return when {
                isSameDay(calendar, today) -> "Today"
                isSameDay(calendar, yesterday) -> "Yesterday"
                else -> dateString
            }
        }
        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun showPopup(entry: JournalEntry) {
            val popupMenu = PopupMenu(itemView.context, editIcon)
            popupMenu.menuInflater.inflate(R.menu.journal_entry_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.edit -> onEditClick(entry,true)
                    R.id.delete -> onDeleteClick(entry)
                }
                true
            }
            popupMenu.show()
        }

        private fun showEmojiPopup(entry: JournalEntry) {
            val inflater = LayoutInflater.from(itemView.context)
            val popupView = inflater.inflate(R.layout.item_emoji_popup, null)

            val emojiVerySad: ImageView = popupView.findViewById(R.id.emojiVerySad)
            val emojiSad: ImageView = popupView.findViewById(R.id.emojiSad)
            val emojiNeutral: ImageView = popupView.findViewById(R.id.emojiNeutral)
            val emojiHappy: ImageView = popupView.findViewById(R.id.emojiHappy)
            val emojiVeryHappy: ImageView = popupView.findViewById(R.id.emojiVeryHappy)

            val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

            val emojiClickListener = View.OnClickListener { view ->
                val emojiCode = when (view.id) {
                    R.id.emojiVerySad -> "0"
                    R.id.emojiSad -> "1"
                    R.id.emojiNeutral -> "2"
                    R.id.emojiHappy -> "3"
                    R.id.emojiVeryHappy -> "4"
                    else -> "2" // default to neutral
                }
                entry.emoji = emojiCode
                emojiView.setImageResource(getEmojiResource(emojiCode))
                saveChanges(entry)
                popupWindow.dismiss()
            }

            emojiVerySad.setOnClickListener(emojiClickListener)
            emojiSad.setOnClickListener(emojiClickListener)
            emojiNeutral.setOnClickListener(emojiClickListener)
            emojiHappy.setOnClickListener(emojiClickListener)
            emojiVeryHappy.setOnClickListener(emojiClickListener)

            // Calculate the offset to center the popup within the card view
            popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val popupWidth = popupView.measuredWidth
            val popupHeight = popupView.measuredHeight

            // Calculate the exact x and y positions based on the card view dimensions
            val location = IntArray(2)
            itemView.getLocationOnScreen(location)
            val offsetX = location[0] + (itemView.width - popupWidth) / 2
            val offsetY = location[1] - popupHeight + 325

            // Show the popup window with calculated offsets
            popupWindow.showAtLocation(itemView, Gravity.NO_GRAVITY, offsetX, offsetY)
        }

        private fun showEditDialog(entry: JournalEntry) {
            val dialogView = LayoutInflater.from(itemView.context).inflate(R.layout.dialog_edit_emotion, null)
            val dialogBuilder = AlertDialog.Builder(itemView.context)
                .setView(dialogView)

            val dialog = dialogBuilder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val emotionEditText = dialogView.findViewById<EditText>(R.id.emotionEditText)
            emotionEditText.setText(entry.emotions)

            dialogView.findViewById<TextView>(R.id.cancelButton).setOnClickListener {
                dialog.dismiss()
            }

            dialogView.findViewById<TextView>(R.id.saveButton).setOnClickListener {
                entry.emotions = emotionEditText.text.toString()
                saveChanges(entry)
                emotionTextView.text = entry.emotions
                dialog.dismiss()
            }

            dialog.show()
        }


        private fun showEditSummaryDialog(entry: JournalEntry, summaryItem: String) {
            val dialogView = LayoutInflater.from(itemView.context).inflate(R.layout.dialog_edit_summary, null)
            val dialogBuilder = AlertDialog.Builder(itemView.context)
                .setView(dialogView)

            val dialog = dialogBuilder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val summaryEditText = dialogView.findViewById<EditText>(R.id.summaryEditText)
            summaryEditText.setText(summaryItem)

            dialogView.findViewById<TextView>(R.id.cancelButton).setOnClickListener {
                dialog.dismiss()
            }

            dialogView.findViewById<TextView>(R.id.saveButton).setOnClickListener {
                val newSummaryList = entry.summary.split(",").map { it.trim() }.toMutableList()
                val index = newSummaryList.indexOf(summaryItem.trim())
                if (index != -1) {
                    newSummaryList[index] = summaryEditText.text.toString().trim()
                }
                entry.summary = newSummaryList.joinToString(", ")
                saveChanges(entry)
                bind(entry)
                dialog.dismiss()
            }

            dialog.show()
        }


        private fun saveChanges(entry: JournalEntry) {
            val uid = auth.currentUser?.uid ?: return
            val userDocRef = firestore.collection("Journal").document(uid)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userDocRef)
                if (snapshot.exists()) {
                    val data = snapshot.data?.toMutableMap() ?: mutableMapOf()
                    val entryMap = data[entry.date] as? MutableMap<String, Any> ?: mutableMapOf()
                    entryMap["Emoji"] = entry.emoji
                    entryMap["Emotions"] = entry.emotions
                    entryMap["Summary"] = entry.summary
                    data[entry.date] = entryMap
                    transaction.set(userDocRef, data)
                }
            }.addOnSuccessListener {
                // Optionally handle success
            }.addOnFailureListener { exception ->
                // Optionally handle failure
                exception.printStackTrace()
            }
        }

        private fun getEmojiResource(emojiCode: String): Int {
            return when (emojiCode) {
                "0" -> R.drawable.very_sad
                "1" -> R.drawable.sad
                "2" -> R.drawable.neutral
                "3" -> R.drawable.happy
                "4" -> R.drawable.very_happy
                else -> R.drawable.very_happy
            }
        }
    }
}