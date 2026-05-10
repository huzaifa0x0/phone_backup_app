package com.phonebackup.app.ui.backups

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.phonebackup.app.data.model.BackupFile
import com.phonebackup.app.databinding.ItemBackupFileBinding

class BackupsAdapter(
    private val onDownloadClick: (BackupFile) -> Unit,
    private val onDeleteClick: (BackupFile) -> Unit
) : ListAdapter<BackupFile, BackupsAdapter.BackupViewHolder>(BackupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupViewHolder {
        val binding = ItemBackupFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BackupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BackupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BackupViewHolder(private val binding: ItemBackupFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: BackupFile) {
            binding.tvFilename.text = file.original_name
            
            val sizeMb = file.size / (1024f * 1024f)
            val encryptionStatus = if (file.encrypted) "Encrypted" else "Plaintext"
            binding.tvDetails.text = String.format("%.2f MB • %s", sizeMb, encryptionStatus)

            binding.btnDownload.setOnClickListener { onDownloadClick(file) }
            binding.btnDelete.setOnClickListener { onDeleteClick(file) }
        }
    }

    class BackupDiffCallback : DiffUtil.ItemCallback<BackupFile>() {
        override fun areItemsTheSame(oldItem: BackupFile, newItem: BackupFile): Boolean {
            return oldItem.filename == newItem.filename
        }

        override fun areContentsTheSame(oldItem: BackupFile, newItem: BackupFile): Boolean {
            return oldItem == newItem
        }
    }
}