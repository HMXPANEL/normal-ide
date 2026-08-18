/*
 *  This file is part of HMX IDE.
 *
 *  HMX IDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  HMX IDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with HMX IDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hmx.ide.activities.aichat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hmx.ide.databinding.LayoutChatMessageBinding

class AIChatAdapter : RecyclerView.Adapter<AIChatAdapter.VH>() {

  private val items = mutableListOf<ChatMessage>()

  class VH(val binding: LayoutChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

  fun add(message: ChatMessage) {
    items.add(message)
    notifyItemInserted(items.size - 1)
  }

  fun setLastContent(content: String) {
    items.lastOrNull()?.let {
      items[items.size - 1] = it.copy(content = content)
      notifyItemChanged(items.size - 1)
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    VH(LayoutChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun getItemCount() = items.size

  override fun onBindViewHolder(holder: VH, position: Int) {
    val msg = items[position]
    val isUser = msg.role == "user"
    holder.binding.messageText.text = msg.content
    val lp = holder.binding.root.layoutParams as ViewGroup.MarginLayoutParams
    lp.marginStart = if (isUser) 48 else 0
    lp.marginEnd = if (isUser) 0 else 48
    holder.binding.root.layoutParams = lp
    val attr = if (isUser) android.R.attr.colorPrimary else android.R.attr.colorBackground
    val ta = holder.binding.root.context.obtainStyledAttributes(intArrayOf(attr))
    val color = ta.getColor(0, 0)
    ta.recycle()
    holder.binding.bubble.setCardBackgroundColor(color)
  }
}
