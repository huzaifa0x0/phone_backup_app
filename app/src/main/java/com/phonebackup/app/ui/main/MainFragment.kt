package com.phonebackup.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.phonebackup.app.R
import com.phonebackup.app.data.api.ApiClient
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import com.phonebackup.app.network.ConnectionManager
import com.phonebackup.app.databinding.FragmentMainBinding
import com.phonebackup.app.worker.AutoBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: BackupPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            setupWorkManager(true)
        } else {
            Toast.makeText(context, "Permissions required for backup", Toast.LENGTH_SHORT).show()
            binding.switchAutoBackup.isChecked = false
        }
    }

    private val manualBackupLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        handleManualBackupSelection(uris)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        prefs = BackupPreferences(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_my_backups -> {
                    findNavController().navigate(R.id.action_mainFragment_to_backupsFragment)
                    true
                }
                R.id.action_settings -> {
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                    true
                }
                else -> false
            }
        }

        binding.switchAutoBackup.isChecked = prefs.isAutoBackupEnabled
        updateStatusText()

        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndStart()
            } else {
                setupWorkManager(false)
            }
        }

        binding.btnInitialSync.setOnClickListener {
            // Run a one-time immediate sync
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(requireContext()).enqueue(syncWork)
            Toast.makeText(context, "Initial Sync Started", Toast.LENGTH_SHORT).show()
        }

        binding.btnManualBackup.setOnClickListener {
            manualBackupLauncher.launch("*/*")
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            setupWorkManager(true)
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun setupWorkManager(enable: Boolean) {
        prefs.isAutoBackupEnabled = enable
        val workManager = WorkManager.getInstance(requireContext())

        if (enable) {
            val networkType = if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresCharging(prefs.chargingOnly)
                .build()

            val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "NightlyBackup",
                ExistingPeriodicWorkPolicy.UPDATE,
                backupRequest
            )
        } else {
            workManager.cancelUniqueWork("NightlyBackup")
        }
        updateStatusText()
    }

    private fun updateStatusText() {
        binding.tvAutoBackupStatus.text = if (prefs.isAutoBackupEnabled) {
            getString(R.string.status_running)
        } else {
            getString(R.string.status_stopped)
        }
    }

    private fun handleManualBackupSelection(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) {
            Toast.makeText(context, getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val summary = withContext(Dispatchers.IO) {
                uploadSelectedFiles(appContext, uris)
            }

            val message = when {
                summary.failureCount == 0 -> "Manual backup completed"
                summary.successCount > 0 -> "Manual backup completed with errors"
                else -> "Manual backup failed"
            }
            if (isAdded) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadSelectedFiles(
        context: android.content.Context,
        uris: List<android.net.Uri>
    ): UploadSummary {
        val connectionManager = ConnectionManager(prefs)
        val apiService = ApiClient.apiService ?: ApiClient.buildService(context, prefs, connectionManager)
        val repository = BackupRepository(apiService, prefs)
        val resolver = context.contentResolver
        val uploadedFiles = prefs.uploadedFiles.toMutableSet()
        var successCount = 0
        var failureCount = 0

        uris.forEach { uri ->
            val mimeType = resolver.getType(uri).orEmpty()
            if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) {
                return@forEach
            }

            var tempFile: java.io.File? = null
            try {
                val displayName = resolveDisplayName(resolver, uri).ifBlank {
                    "manual_${System.currentTimeMillis()}"
                }
                val safeName = displayName.replace(java.io.File.separatorChar, '_')
                if (uploadedFiles.contains(safeName)) {
                    return@forEach
                }

                tempFile = copyUriToCacheFile(context, uri, safeName)
                val result = repository.uploadFile(tempFile)
                if (result.isSuccess) {
                    successCount += 1
                    if (uploadedFiles.add(safeName)) {
                        prefs.uploadedFiles = uploadedFiles
                    }
                } else {
                    failureCount += 1
                }
            } catch (e: SecurityException) {
                failureCount += 1
            } catch (e: Exception) {
                failureCount += 1
            } finally {
                tempFile?.delete()
            }
        }

        return UploadSummary(successCount, failureCount)
    }

    private fun resolveDisplayName(
        resolver: android.content.ContentResolver,
        uri: android.net.Uri
    ): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun copyUriToCacheFile(
        context: android.content.Context,
        uri: android.net.Uri,
        displayName: String
    ): java.io.File {
        val cacheFile = java.io.File(context.cacheDir, displayName)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open input stream")
        return cacheFile
    }

    private data class UploadSummary(
        val successCount: Int,
        val failureCount: Int
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}