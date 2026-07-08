package com.inkdiary

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry point. Routes to ConfigActivity if no API key is set, otherwise to DiaryActivity.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = ConfigStore.load(this)
        if (config.apiKey.isBlank()) {
            startActivity(Intent(this, ConfigActivity::class.java))
        } else {
            startActivity(Intent(this, DiaryActivity::class.java))
        }
        finish()
    }
}
