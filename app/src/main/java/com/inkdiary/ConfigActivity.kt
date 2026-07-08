package com.inkdiary

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.inkdiary.databinding.ActivityConfigBinding

/**
 * Configuration screen for API key, base URL, model, and persona.
 * Entered on first launch (when no API key) or via three-finger long-press.
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val config = ConfigStore.load(this)
        binding.etApiKey.setText(config.apiKey)
        binding.etBaseUrl.setText(config.baseUrl)
        binding.etModel.setText(config.model)
        binding.etPersona.setText(config.persona)

        binding.btnSave.setOnClickListener {
            val updated = ConfigStore.Config(
                apiKey = binding.etApiKey.text.toString().trim(),
                baseUrl = binding.etBaseUrl.text.toString().trim().ifBlank { ConfigStore.DEFAULT_BASE_URL },
                model = binding.etModel.text.toString().trim().ifBlank { ConfigStore.DEFAULT_MODEL },
                persona = binding.etPersona.text.toString().trim().ifBlank { ConfigStore.DEFAULT_PERSONA }
            )
            ConfigStore.save(this, updated)
            Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show()

            // Restart into diary
            val intent = Intent(this, DiaryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        binding.btnPresetOpenai.setOnClickListener {
            binding.etBaseUrl.setText("https://api.openai.com/v1")
            binding.etModel.setText("gpt-4o-mini")
            showToast(R.string.preset_applied)
        }

        binding.btnPresetOpenrouter.setOnClickListener {
            binding.etBaseUrl.setText("https://openrouter.ai/api/v1")
            binding.etModel.setText("openai/gpt-4o-mini")
            showToast(R.string.preset_applied)
        }

        binding.btnPresetGemini.setOnClickListener {
            binding.etBaseUrl.setText("https://generativelanguage.googleapis.com/v1beta/openai")
            binding.etModel.setText("gemini-2.0-flash")
            showToast(R.string.preset_applied)
        }
    }

    private fun showToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }
}
