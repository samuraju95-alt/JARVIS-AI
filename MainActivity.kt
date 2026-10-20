package com.example.jarvisai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private lateinit var tts: TextToSpeech
    private val executor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("jarvis_prefs", MODE_PRIVATE) }
    private val requestCodeSpeech = 721
    private var pendingSpeechCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        web = WebView(this)
        web.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(JarvisBridge(), "AndroidJarvis")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
        }
    }

    inner class JarvisBridge {
        @JavascriptInterface
        fun getApiKeyConfigured(): Boolean =
            !prefs.getString("api_key", "").isNullOrBlank()

        @JavascriptInterface
        fun configureApiKey() {
            runOnUiThread {
                val layout = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(48, 24, 48, 8)
                }
                layout.addView(TextView(this@MainActivity).apply {
                    text = "Enter your OpenAI API key. It stays on this phone and is not included in the APK."
                })
                val input = EditText(this@MainActivity).apply {
                    hint = "sk-..."
                    setSingleLine(true)
                    inputType = 129 // textPassword
                    setText(prefs.getString("api_key", ""))
                }
                layout.addView(input)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("JARVIS API setup")
                    .setView(layout)
                    .setPositiveButton("Save") { _, _ ->
                        prefs.edit().putString("api_key", input.text.toString().trim()).apply()
                        web.evaluateJavascript(
                            "window.jarvisApiStatus && window.jarvisApiStatus(${getApiKeyConfigured()});",
                            null
                        )
                    }
                    .setNeutralButton("Remove key") { _, _ ->
                        prefs.edit().remove("api_key").apply()
                        web.evaluateJavascript("window.jarvisApiStatus && window.jarvisApiStatus(false);", null)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        @JavascriptInterface
        fun startListening(language: String) {
            runOnUiThread {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "ta") "ta-IN" else "en-IN")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
                    }
                    pendingSpeechCallback = true
                    startActivityForResult(intent, requestCodeSpeech)
                } catch (e: Exception) {
                    web.evaluateJavascript("window.jarvisSpeechResult && window.jarvisSpeechResult('');", null)
                }
            }
        }

        @JavascriptInterface
        fun speak(text: String, language: String) {
            runOnUiThread {
                if (::tts.isInitialized) {
                    val locale = if (language == "ta") Locale("ta", "IN") else Locale("en", "IN")
                    val result = tts.setLanguage(locale)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.language = Locale("en", "IN")
                    }
                    tts.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, null, "jarvis-reply")
                }
            }
        }

        @JavascriptInterface
        fun ask(message: String, historyJson: String) {
            executor.execute {
                val key = prefs.getString("api_key", "").orEmpty()
                if (key.isBlank()) {
                    sendResult(null, "API key set pannunga: Settings button tap pannunga.")
                    return@execute
                }
                try {
                    val history = JSONArray(historyJson)
                    val input = JSONArray()
                    val instructions = """
                        You are JARVIS, a helpful personal AI inspired by a futuristic movie assistant.
                        Reply naturally in Tamil, Tanglish, or English matching the user.
                        Be concise but useful. Help with coding, learning, and everyday tasks.
                        Do not claim you performed phone actions unless the app actually did so.
                        Ask before consequential actions. Never reveal secrets or API keys.
                    """.trimIndent()
                    for (i in 0 until history.length()) {
                        val item = history.optJSONObject(i) ?: continue
                        val role = item.optString("role")
                        val content = item.optString("content")
                        if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                            input.put(JSONObject().put("role", role).put("content", content.take(4000)))
                        }
                    }
                    input.put(JSONObject().put("role", "user").put("content", message.take(4000)))
                    val body = JSONObject()
                        .put("model", "gpt-4.1-mini")
                        .put("instructions", instructions)
                        .put("input", input)
                        .put("max_output_tokens", 700)

                    val conn = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection)
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.doOutput = true
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val responseText = stream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (code !in 200..299) {
                        val detail = try { JSONObject(responseText).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                        sendResult(null, "API error ($code): ${detail ?: "Check API key, model access, and billing."}")
                        return@execute
                    }
                    val json = JSONObject(responseText)
                    val output = json.optJSONArray("output") ?: JSONArray()
                    val parts = StringBuilder()
                    for (i in 0 until output.length()) {
                        val item = output.optJSONObject(i) ?: continue
                        val content = item.optJSONArray("content") ?: continue
                        for (j in 0 until content.length()) {
                            val part = content.optJSONObject(j) ?: continue
                            if (part.optString("type") == "output_text") {
                                parts.append(part.optString("text"))
                            }
                        }
                    }
                    val reply = parts.toString().ifBlank { "I couldn't produce a text reply. Please try again." }
                    sendResult(reply, null)
                } catch (e: Exception) {
                    sendResult(null, "Connection failed. Check internet, API access, and server response.")
                }
            }
        }

        private fun sendResult(reply: String?, error: String?) {
            runOnUiThread {
                val payload = JSONObject()
                    .put("reply", reply ?: JSONObject.NULL)
                    .put("error", error ?: JSONObject.NULL)
                web.evaluateJavascript(
                    "window.jarvisResponse && window.jarvisResponse(${JSONObject.quote(payload.toString())});",
                    null
                )
            }
        }
    }

    @Deprecated("Deprecated in Android, retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCodeSpeech) {
            val result = if (resultCode == RESULT_OK)
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            else ""
            web.evaluateJavascript(
                "window.jarvisSpeechResult && window.jarvisSpeechResult(${JSONObject.quote(result)});",
                null
            )
            pendingSpeechCallback = false
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        executor.shutdownNow()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
