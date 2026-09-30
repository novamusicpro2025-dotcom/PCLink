package com.pcmaster.control

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PcFile(val name: String, val path: String, val isDir: Boolean, val size: String = "")

class FileAdapter(
    private var files: List<PcFile>,
    private val onItemClick: (PcFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileName)
        val fileSize: TextView = view.findViewById(R.id.fileSize)
        val btnDownload: ImageButton = view.findViewById(R.id.btnDownload)
        val icon: android.widget.ImageView = view.findViewById(R.id.fileIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name
        holder.fileSize.text = if (file.isDir) "" else file.size
        
        if (file.isDir) {
            holder.icon.setImageResource(android.R.drawable.ic_menu_directions) // Better than nothing
            holder.btnDownload.visibility = View.GONE
        } else {
            holder.icon.setImageResource(android.R.drawable.ic_menu_save)
            holder.btnDownload.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener { onItemClick(file) }
        holder.btnDownload.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = files.size

    fun updateFiles(newFiles: List<PcFile>) {
        files = newFiles
        notifyDataSetChanged()
    }
}