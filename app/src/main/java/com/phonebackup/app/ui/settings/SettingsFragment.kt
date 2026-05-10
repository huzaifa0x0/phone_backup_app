package com.phonebackup.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.WorkManager
import com.phonebackup.app.R
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: BackupPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefs = BackupPreferences(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load Prefs
        binding.switchEncryption.isChecked = prefs.encryptionEnabled
        binding.switchPhotos.isChecked = prefs.backupPhotos
        binding.switchVideos.isChecked = prefs.backupVideos
        binding.switchWifi.isChecked = prefs.wifiOnly

        // Save Prefs
        binding.switchEncryption.setOnCheckedChangeListener { _, isChecked -> prefs.encryptionEnabled = isChecked }
        binding.switchPhotos.setOnCheckedChangeListener { _, isChecked -> prefs.backupPhotos = isChecked }
        binding.switchVideos.setOnCheckedChangeListener { _, isChecked -> prefs.backupVideos = isChecked }
        binding.switchWifi.setOnCheckedChangeListener { _, isChecked -> prefs.wifiOnly = isChecked }

        binding.btnLogout.setOnClickListener {
            // Clear credentials
            prefs.clearCredentials()
            
            // Stop background work
            WorkManager.getInstance(requireContext()).cancelAllWork()
            
            // Navigate to login
            findNavController().navigate(R.id.loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}