package com.phonebackup.app.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.phonebackup.app.data.api.ApiClient
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import com.phonebackup.app.databinding.FragmentLoginBinding
import com.phonebackup.app.network.ConnectionManager

class LoginFragment : Fragment() {

    companion object {
        private const val TEST_USERNAME = "testuser"
        private const val TEST_PASSWORD = "testpass"
    }

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LoginViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Manual dependency injection for brevity
        val prefs = BackupPreferences(requireContext())
        val connectionManager = ConnectionManager(prefs)
        val apiService = ApiClient.buildService(requireContext(), prefs, connectionManager)
        val repository = BackupRepository(apiService, prefs)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(repository, prefs) as T
            }
        })[LoginViewModel::class.java]

        // Prefill the fixed Cloudflare tunnel URL and test credentials for testing.
        binding.etServerUrl.setText(BackupPreferences.DEFAULT_SERVER_URL)
        binding.etUsername.setText(TEST_USERNAME)
        binding.etPassword.setText(TEST_PASSWORD)
        binding.etServerUrl.isEnabled = false

        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.login(username, password)
        }
    }

    private fun setupObservers() {
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is LoginViewModel.LoginState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.btnLogin.isEnabled = false
                }
                is LoginViewModel.LoginState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    findNavController().navigate(com.phonebackup.app.R.id.action_loginFragment_to_mainFragment)
                }
                is LoginViewModel.LoginState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnLogin.isEnabled = true
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}