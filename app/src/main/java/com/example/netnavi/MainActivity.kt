
package com.netnavi.prototype

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var levelText: TextView
    private var tts: TextToSpeech? = null
    private var level = 1
    private var experience = 0

    companion object {
        private const val SPEECH_REQUEST = 100
        private const val MIC_REQUEST = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        val title = TextView(this).apply {
            text = "NETNAVI"
            textSize = 30f
        }

        levelText = TextView(this).apply {
            text = "LEVEL 1"
            textSize = 22f
        }

        statusText = TextView(this).apply {
            text = "ネットナビ起動完了！"
            textSize = 20f
        }

        val talkButton = Button(this).apply {
            text = "話しかける"
            setOnClickListener {
                startListening()
            }
        }

        layout.addView(title)
        layout.addView(levelText)
        layout.addView(statusText)
        layout.addView(talkButton)

        setContentView(layout)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.JAPANESE
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_REQUEST
            )
            return
        }

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "ja-JP"
            )
        }

        try {
            startActivityForResult(intent, SPEECH_REQUEST)
        } catch (e: Exception) {
            statusText.text = "音声認識を起動できませんでした"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults
        )

        if (requestCode == MIC_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST &&
            resultCode == RESULT_OK
        ) {
            val results = data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

            val message = results?.firstOrNull() ?: return

            experience += 10

            if (experience >= 100) {
                level++
                experience = 0
            }

            levelText.text = "LEVEL $level"

            val reply = when {
                message.contains("こんにちは") ->
                    "こんにちは！今日もよろしくね！"

                message.contains("名前") ->
                    "僕は君のネットナビだよ！"

                message.contains("レベル") ->
                    "現在のレベルは${level}だよ！"

                else ->
                    "なるほど！君はそう考えているんだね！"
            }

            statusText.text =
                "あなた：$message\n\nネットナビ：$reply"

            tts?.speak(
                reply,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "netnavi_reply"
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
