package com.example.netnavi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private data class Navi(val name: String, val color: Int, val accent: Int, val pitch: Float)
    private val navis = listOf(
        Navi("ロックマン", Color.rgb(30, 124, 238), Color.CYAN, 1.16f),
        Navi("ガッツマン", Color.rgb(220, 90, 36), Color.YELLOW, 0.85f),
        Navi("ロール", Color.rgb(240, 95, 160), Color.WHITE, 1.32f),
        Navi("グライド", Color.rgb(60, 166, 130), Color.YELLOW, 0.95f),
        Navi("ブルース", Color.rgb(190, 45, 65), Color.WHITE, 0.89f)
    )
    private val prefs by lazy { getSharedPreferences("netnavi", MODE_PRIVATE) }
    private lateinit var speech: TextToSpeech
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var history: TextView
    private lateinit var portrait: Portrait
    private var selected = 0
    private var xp = 0
    private var bond = 0
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = prefs.getInt("selected_navi", 0).coerceIn(0, navis.lastIndex)
        xp = prefs.getInt("xp", 0)
        bond = prefs.getInt("bond", 0)
        speech = TextToSpeech(this, this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22, 24, 22, 30)
            setBackgroundColor(Color.rgb(11, 25, 44))
        }
        fun text(value: String, size: Float): TextView = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 10)
        }
        root.addView(text("NET NAVI  /  PET", 26f))
        stats = text("", 15f).also { root.addView(it) }
        root.addView(text("ナビを選んでね", 15f))
        val selector = Spinner(this)
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, navis.map { it.name })
        root.addView(selector)
        portrait = Portrait().also { root.addView(it, LinearLayout.LayoutParams(-1, 260)) }
        status = text("話しかけてみて！", 18f).also { root.addView(it) }
        root.addView(Button(this).apply { text = "🎤 話しかける"; setOnClickListener { listen() } }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply { text = "ナビの声を聞く"; setOnClickListener { say(greeting()) } }, LinearLayout.LayoutParams(-1, -2))
        history = text("", 14f).also { root.addView(it) }
        selector.setSelection(selected)
        selector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selected = position
                prefs.edit().putInt("selected_navi", selected).apply()
                portrait.invalidate()
                if (ttsReady) speech.setPitch(navis[selected].pitch)
                status.text = "${navis[selected].name}を選択中"
            }
        }
        setContentView(ScrollView(this).apply { addView(root) })
        updateStats()
        history.text = prefs.getString("last", "会話履歴はこの端末に保存されます")
    }

    private inner class Portrait : View(this@MainActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            val cx = w / 2; val cy = h / 2
            val n = navis[selected]
            p.color = Color.rgb(25, 51, 78); canvas.drawRoundRect(8f, 8f, w-8f, h-8f, 24f, 24f, p)
            p.color = n.color
            canvas.drawOval(cx-104f, cy-104f, cx+104f, cy+102f, p)
            canvas.drawRoundRect(cx-95f, cy+55f, cx+95f, cy+116f, 20f, 20f, p)
            p.color = Color.rgb(255, 210, 173)
            canvas.drawRoundRect(cx-67f, cy-42f, cx+67f, cy+68f, 35f, 35f, p)
            p.color = n.accent
            canvas.drawCircle(cx, cy-85f, 18f, p)
            p.color = Color.rgb(26, 43, 63)
            canvas.drawCircle(cx-26f, cy+1f, 7f, p); canvas.drawCircle(cx+26f, cy+1f, 7f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f
            canvas.drawArc(cx-22f, cy+12f, cx+22f, cy+43f, 15f, 150f, false, p)
            p.style = Paint.Style.FILL
            p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER; p.textSize = 25f
            canvas.drawText(n.name, cx, h-15f, p)
        }
    }

    private fun greeting() = when (selected) {
        0 -> "やあ！ボクはロックマン。今日もよろしくね！"
        1 -> "オレはガッツマン！元気にいくぞ！"
        2 -> "こんにちは！ロールよ。今日もよろしくね！"
        3 -> "お呼びでしょうか。グライドです。"
        else -> "ブルースだ。用件を聞こう。"
    }
    private fun respond(input: String): String {
        if (input.contains("名前") || input.contains("誰")) return greeting()
        if (input.contains("レベル")) return "現在のレベルは${1 + xp / 50}だ。"
        val kind = when {
            listOf("おはよう", "こんにちは", "こんばんは").any { input.contains(it) } -> 0
            input.contains("疲れ") -> 1
            input.contains("ありがとう") -> 2
            else -> 3
        }
        val lines = listOf(
            listOf("やあ！今日もよろしくね！", "無理しないでね。一緒に休もう！", "こちらこそ、ありがとう！", "なるほど！もっと聞かせてよ。"),
            listOf("おう！今日も気合い入れていくぞ！", "疲れたなら休むんだぞ！", "おう！任せとけ！", "そうか！もっと話してみろ！"),
            listOf("こんにちは！会えてうれしいな。", "お疲れさま。少し休憩しよ？", "こちらこそ、ありがとう！", "そうなんだ。続きも聞きたいな。"),
            listOf("ごきげんよう。本日もよろしくお願いいたします。", "どうぞご無理なさらず。", "お役に立てて光栄です。", "承知しました。もう少し詳しくお聞かせください。"),
            listOf("来たか。今日もよろしく頼む。", "無理は禁物だ。休め。", "礼には及ばない。", "なるほどな。続きを聞こう。")
        )
        return lines[selected][kind]
    }
    private fun updateStats() {
        stats.text = "LEVEL ${1 + xp / 50}  |  EXP ${xp % 50}/50  |  親密度 $bond"
        prefs.edit().putInt("xp", xp).putInt("bond", bond).apply()
    }
    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 4); return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "${navis[selected].name}に話しかけてね")
        }
        try { startActivityForResult(intent, 10) }
        catch (e: Exception) { status.text = "音声認識アプリが見つかりません" }
    }
    @Deprecated("Legacy speech result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            val response = respond(heard)
            xp += 10; bond += 2; updateStats()
            val saved = ((prefs.getString("last", "") ?: "") + "\nあなた: $heard\n${navis[selected].name}: $response").takeLast(3000)
            prefs.edit().putString("last", saved).apply()
            history.text = saved
            say(response)
        }
    }
    private fun say(message: String) {
        status.text = message
        if (ttsReady) speech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "navi")
        else status.text = "$message\n（音声を準備中です）"
    }
    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            ttsReady = speech.setLanguage(Locale.JAPANESE) != TextToSpeech.LANG_MISSING_DATA
            speech.setPitch(navis[selected].pitch)
            speech.setSpeechRate(1.05f)
        }
    }
    override fun onDestroy() { speech.stop(); speech.shutdown(); super.onDestroy() }
}
