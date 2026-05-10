package com.phonebackup.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Single Activity hosting the Navigation Component's NavHostFragment.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}