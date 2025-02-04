package com.company.chamberly.adapters


import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.text.style.TextAlign
import androidx.recyclerview.widget.RecyclerView
import com.company.chamberly.models.Message
import com.company.chamberly.R
import com.company.chamberly.models.toMap

class MessageAdapter(private val uid: String) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val messages: MutableList<Message> = mutableListOf()
    private var onMessageLongClickListener: OnMessageLongClickListener? = null

    fun setOnMessageLongClickListener(listener: OnMessageLongClickListener) {
        onMessageLongClickListener = listener
    }

    companion object {
        private const val VIEW_TYPE_ME = 1
        private const val VIEW_TYPE_SYSTEM = 2
        private const val VIEW_TYPE_OTHER = 3
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textSender: TextView = itemView.findViewById(R.id.text_gchat_user_other)
        val textMessage: TextView = itemView.findViewById(R.id.text_gchat_message_other)
        val reactedWithHolder: TextView = itemView.findViewById(R.id.reactedWith)
        val replyingToHolder: TextView = itemView.findViewById(R.id.replyingToHolder)
        init {
            // set on long click listener
            itemView.setOnLongClickListener {
                val message = messages[bindingAdapterPosition]
                onMessageLongClickListener!!.onMessageLongClick(message)
                onMessageLongClickListener!!.onSelfLongClick(message)
                true
            }
        }
    }

    inner class MessageViewHolderMe(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.text_gchat_message_me)
        val reactedWithHolder: TextView = itemView.findViewById(R.id.reactedWith)
        val replyingToHolder: TextView = itemView.findViewById(R.id.replyingToHolder)
    }

    inner class MessageViewHolderSystem(itemView: View): RecyclerView.ViewHolder(itemView) {
        val message: TextView = itemView.findViewById(R.id.text_system_message)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ME -> {
                val itemView = inflater.inflate(R.layout.item_chat_me, parent, false)
                MessageViewHolderMe(itemView)
            }
            VIEW_TYPE_SYSTEM -> {
                val itemView = inflater.inflate(R.layout.item_system_message, parent, false)
                MessageViewHolderSystem(itemView)
            }
            VIEW_TYPE_OTHER -> {
                val itemView = inflater.inflate(R.layout.item_chat_other, parent, false)
                MessageViewHolder(itemView)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is MessageViewHolderMe -> {
                holder.textMessage.text = message.message_content
                holder.itemView.setOnLongClickListener {
                    onMessageLongClickListener?.onSelfLongClick(message)
                    true
                }
                holder.reactedWithHolder.text = message.reactedWith.ifBlank { "" }
                holder.reactedWithHolder.visibility = if(message.reactedWith.isNotBlank()) View.VISIBLE else View.GONE
                holder.replyingToHolder.text = if(message.replyingTo.isNotBlank()) "Replying to: ${message.replyingTo}" else ""
                holder.replyingToHolder.visibility = if(message.replyingTo.isNotBlank()) View.VISIBLE else View.GONE
            }
            is MessageViewHolder -> {
                if(position != 0 && message.UID == messages[position - 1].UID && messages[position - 1].message_type == "text") {
                    holder.textSender.visibility = View.GONE
                } else {
                    holder.textSender.visibility = View.VISIBLE
                    holder.textSender.text = message.sender_name
                }
                holder.itemView.setOnLongClickListener {
                    onMessageLongClickListener?.onMessageLongClick(message)
                    true
                }
                holder.textMessage.text = message.message_content
                holder.reactedWithHolder.text = message.reactedWith.ifBlank { "" }
                holder.reactedWithHolder.visibility = if(message.reactedWith.isNotBlank()) View.VISIBLE else View.GONE
                holder.replyingToHolder.text = if(message.replyingTo.isNotBlank()) "Replying to: ${message.replyingTo}" else ""
                holder.replyingToHolder.visibility = if(message.replyingTo.isNotBlank()) View.VISIBLE else View.GONE
            }
            is MessageViewHolderSystem -> {
                holder.message.text = message.message_content
            }
        }
    }

    fun setMessages(messages: List<Message>) {
        this.messages.clear()
        this.messages.addAll(messages)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    fun getMessageId(position: Int): String {
        return messages[position].message_id
    }

    fun addMessage(message: Message, position: Int = -1) {
        if(position != -1) {
            messages.add(position, message)
            notifyItemInserted(position)
        } else {
            if(messages.contains(message)) {
                return
            }
            messages.add(message)
            notifyItemInserted(messages.size - 1)
        }
    }

    fun messageChanged(message: Message, messageId: String) {
        for(i in 0 until messages.size) {
            if (messages[i].message_id == messageId) {
                messages[i] = message
                notifyItemChanged(i)
                return
            }
        }
    }

    fun messageRemoved(message: Message) {
        for(i in 0 until messages.size) {
            if (messages[i].message_id == message.message_id) {
                messages.removeAt(i)
                notifyItemRemoved(i)
                return
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return if (message.UID == uid) {
            VIEW_TYPE_ME
        } else if (message.message_type == "custom" || message.message_type == "system" || message.message_type == "photo") {
            VIEW_TYPE_SYSTEM
        } else {
            VIEW_TYPE_OTHER
        }
    }

    fun messageAt(index: Int): Message {
        return messages[index]
    }

    interface OnMessageLongClickListener {
        fun onMessageLongClick(message: Message)
        fun onSelfLongClick(message: Message)
    }
}