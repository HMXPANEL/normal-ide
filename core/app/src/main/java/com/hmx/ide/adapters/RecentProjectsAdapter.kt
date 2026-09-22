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

package com.hmx.ide.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hmx.ide.databinding.LayoutRecentProjectItemBinding
import java.io.File

/**
 * Adapter for the list of projects shown in the Open Existing Project sheet.
 */
class RecentProjectsAdapter(
  private val onClick: (File) -> Unit
) : RecyclerView.Adapter<RecentProjectsAdapter.VH>() {

  private val items = mutableListOf<Pair<String, Boolean>>()

  class VH(val binding: LayoutRecentProjectItemBinding) : RecyclerView.ViewHolder(binding.root)

  fun submit(list: List<Pair<String, Boolean>>) {
    items.clear()
    items.addAll(list)
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
    VH(LayoutRecentProjectItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

  override fun getItemCount() = items.size

  override fun onBindViewHolder(holder: VH, position: Int) {
    val (path, isRecent) = items[position]
    val file = File(path)
    holder.binding.projectName.text = file.name.ifBlank { path }
    holder.binding.projectPath.text = path
    holder.binding.recentBadge.visibility = if (isRecent) android.view.View.VISIBLE else android.view.View.GONE
    holder.binding.root.setOnClickListener { onClick(file) }
  }
}
