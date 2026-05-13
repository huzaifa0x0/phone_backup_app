package com.phonebackup.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.phonebackup.app.R
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.databinding.FragmentMainBinding
import com.phonebackup.app.worker.AutoBackupWorker
import java.util.concurrent.TimeUnit

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: BackupPreferences
    private var pendingPermissionAction = PendingPermissionAction.None

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            when (pendingPermissionAction) {
                PendingPermissionAction.EnableAutoBackup -> setupWorkManager(true)
                PendingPermissionAction.InitialSync -> startInitialSync()
                PendingPermissionAction.None -> Unit
            }
        } else {
            Toast.makeText(context, "Permissions required for backup", Toast.LENGTH_SHORT).show()
            if (pendingPermissionAction == PendingPermissionAction.EnableAutoBackup) {
                binding.switchAutoBackup.isChecked = false
            }
        }
        pendingPermissionAction = PendingPermissionAction.None
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
        if (prefs.isAutoBackupEnabled) {
            checkPermissionsAndRun(PendingPermissionAction.EnableAutoBackup)
        }

        binding.switchAutoBackup.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkPermissionsAndRun(PendingPermissionAction.EnableAutoBackup)
            } else {
                setupWorkManager(false)
            }
        }

        binding.btnInitialSync.setOnClickListener {
            checkPermissionsAndRun(PendingPermissionAction.InitialSync)
        }
    }

    private fun checkPermissionsAndRun(action: PendingPermissionAction) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissions = mutableListOf<String>()
            if (prefs.backupPhotos) {
                permissions += Manifest.permission.READ_MEDIA_IMAGES
            }
            if (prefs.backupVideos) {
                permissions += Manifest.permission.READ_MEDIA_VIDEO
            }
            permissions.toTypedArray()
        } else {
            if (prefs.backupPhotos || prefs.backupVideos) {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            } else {
                emptyArray()
            }
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            when (action) {
                PendingPermissionAction.EnableAutoBackup -> setupWorkManager(true)
                PendingPermissionAction.InitialSync -> startInitialSync()
                PendingPermissionAction.None -> Unit
            }
        } else {
            pendingPermissionAction = action
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startInitialSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(syncWork)
        Toast.makeText(context, "Initial Sync Started", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class PendingPermissionAction {
        None,
        EnableAutoBackup,
        InitialSync
    }
}
