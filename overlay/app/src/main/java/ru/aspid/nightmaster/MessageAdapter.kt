package ru.aspid.nightmaster

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Message(
    val id: String,
    val content: String,
    val isUser: Boolean
)

class MessageAdapter(
    private val messages: List<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int =
        if (messages[position].isUser) TYPE_USER else TYPE_MASTER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_USER) {
            R.layout.item_message_user
        } else {
            R.layout.item_message_master
        }
        return MessageHolder(
            LayoutInflater.from(parent.context).inflate(layout, parent, false)
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.findViewById<TextView>(R.id.message_text).text =
            messages[position].content
    }

    override fun getItemCount(): Int = messages.size

    private class MessageHolder(view: View) : RecyclerView.ViewHolder(view)

    private companion object {
        const val TYPE_USER = 1
        const val TYPE_MASTER = 2
    }
}
