package com.inkdiary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inkdiary.databinding.ActivityConfigBinding
import com.inkdiary.memory.MemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuration screen + backup/restore (export/import the whole memories/
 * directory — all notes, strokes, and memory.md — as a .zip).
 */
class ConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConfigBinding
    private lateinit var memoryStore: MemoryStore

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? -> if (uri != null) doExport(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) doImport(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        memoryStore = MemoryStore(this)

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
        binding.btnExport.setOnClickListener {
            exportLauncher.launch("inkdiary-backup.zip")
        }
        binding.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }
    }

    private fun doExport(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        memoryStore.exportToZip(out)
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            showToast(if (ok) R.string.export_ok else R.string.export_fail)
        }
    }

    private fun doImport(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { inp ->
                        memoryStore.importFromZip(inp)
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            showToast(if (ok) R.string.import_ok else R.string.import_fail)
        }
    }

    private fun showToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }
}
