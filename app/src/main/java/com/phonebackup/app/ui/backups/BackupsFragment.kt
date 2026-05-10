package com.phonebackup.app.ui.backups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phonebackup.app.data.api.ApiClient
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import com.phonebackup.app.databinding.FragmentBackupsBinding
import com.phonebackup.app.network.ConnectionManager

class BackupsFragment : Fragment() {

    private var _binding: FragmentBackupsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: BackupsViewModel
    private lateinit var adapter: BackupsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBackupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigation back
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Setup Manual DI
        val prefs = BackupPreferences(requireContext())
        val connectionManager = ConnectionManager(prefs)
        val apiService = ApiClient.buildService(requireContext(), prefs, connectionManager)
        val repository = BackupRepository(apiService, prefs)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BackupsViewModel(repository) as T
            }
        })[BackupsViewModel::class.java]

        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = BackupsAdapter(
            onDownloadClick = { file ->
                Toast.makeText(context, "Downloading ${file.original_name}...", Toast.LENGTH_SHORT).show()
                // Implementation for saving to disk goes here
            },
            onDeleteClick = { file ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete File")
                    .setMessage("Are you sure you want to delete ${file.original_name} from the server?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteFile(file) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvBackups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBackups.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.files.observe(viewLifecycleOwner) { files ->
            adapter.submitList(files)
            binding.tvEmptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}