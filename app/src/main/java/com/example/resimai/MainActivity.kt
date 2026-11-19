package com.example.resimai

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var image: ImageView
    private lateinit var prompt: EditText
    private lateinit var modeSpinner: Spinner
    private var generator: ImageGenerator? = null
    private val modes = listOf("Otomatik", "4 GB modu", "8 GB modu", "12 GB modu")
    // Mod -> adım sayısı (az adım = hızlı ama daha kaba resim)
    private val steps = mapOf(4 to 10, 8 to 20, 12 to 30)
    private val modelDir get() = File(filesDir, "model")
    private val bg = Executors.newSingleThreadExecutor()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                modes
            )
        }
        prompt = EditText(this).apply { hint = "Ne çizelim? (İngilizce daha iyi sonuç verir)" }
        val pick = Button(this).apply {
            text = "Model klasörünü seç"
            setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1) }
        }
        val gen = Button(this).apply { text = "Üret"; setOnClickListener { generate() } }
        status = TextView(this)
        image = ImageView(this).apply { adjustViewBounds = true }
        listOf(modeSpinner, prompt, pick, gen, status, image).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) })
        status.text = if (modelDir.exists()) "Model hazır." else "Önce model klasörünü seç."
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != 1 || res != RESULT_OK) return
        status.text = "Model kopyalanıyor, bekle..."
        bg.execute {
            try {
                generator?.close(); generator = null
                modelDir.deleteRecursively(); modelDir.mkdirs()
                copyTree(DocumentFile.fromTreeUri(this, uri)!!, modelDir)
                runOnUiThread { status.text = "Model kopyalandı." }
            } catch (e: Throwable) {
                runOnUiThread { status.text = "Hata: ${e.message}" }
            }
        }
    }

    private fun copyTree(src: DocumentFile, dst: File) {
        for (f in src.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) {
                copyTree(f, File(dst, name).also { it.mkdirs() })
            } else {
                contentResolver.openInputStream(f.uri)!!.use { i ->
                    File(dst, name).outputStream().use { o -> i.copyTo(o) }
                }
            }
        }
    }

    // Telefonun gerçek RAM'i etiketten biraz düşük görünür (8 GB telefon ~7.4 GB gösterir)
    private fun deviceMode(): Int {
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
        return when {
            gb < 5.5 -> 4
            gb < 9.5 -> 8
            else -> 12
        }
    }

    private fun currentMode(): Int = when (modeSpinner.selectedItemPosition) {
        1 -> 4
        2 -> 8
        3 -> 12
        else -> deviceMode()
    }

    private fun generate() {
        val text = prompt.text.toString().trim()
        if (text.isEmpty() || !modelDir.exists()) {
            status.text = "Bir şey yaz ve model klasörünü seç."
            return
        }
        val mode = currentMode()
        val n = steps.getValue(mode)
        status.text = "Üretiliyor ($mode GB modu, $n adım)..." +
            if (mode == 4) "\nDüşük RAM: uygulama kapanabilir, olursa tekrar dene." else ""
        bg.execute {
            try {
                if (generator == null) {
                    val opts = ImageGenerator.ImageGeneratorOptions.builder()
                        .setImageGeneratorModelDirectory(modelDir.absolutePath)
                        .build()
                    generator = ImageGenerator.createFromOptions(this, opts)
                }
                val result = generator!!.generate(text, n, (0..9999).random())
                val bmp: Bitmap = BitmapExtractor.extract(result.generatedImage())
                runOnUiThread { image.setImageBitmap(bmp); status.text = "Bitti." }
            } catch (e: Throwable) {
                runOnUiThread { status.text = "Hata: ${e.message}" }
            }
        }
    }
}
