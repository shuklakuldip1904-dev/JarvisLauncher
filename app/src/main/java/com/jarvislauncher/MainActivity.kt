package com.jarvislauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }
    private lateinit var messages: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var commandInput: EditText
    private lateinit var speechButton: Button
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechReady = false
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BACKGROUND
        window.navigationBarColor = COLOR_BACKGROUND
        textToSpeech = TextToSpeech(this, this)
        createHome()
        addMessage("Hi, I'm Jarvis. Ask me something, say “Open [app]”, or try “scroll down”.", false)
    }

    override fun onInit(status: Int) {
        speechReady = status == TextToSpeech.SUCCESS
        if (speechReady) textToSpeech?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun createHome() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            setBackgroundColor(COLOR_BACKGROUND)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(TextView(this).apply {
            text = "JARVIS"
            setTextColor(COLOR_ACCENT)
            textSize = 12f
            letterSpacing = 0.18f
            setTypeface(null, Typeface.BOLD)
        })
        titleBlock.addView(TextView(this).apply {
            text = "Your personal assistant"
            setTextColor(COLOR_TEXT)
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
        })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(actionButton("Settings") { showSettings() })
        page.addView(header)

        chatScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(18), 0, dp(14))
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        chatScroll.addView(messages)
        page.addView(
            chatScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        commandInput = EditText(this).apply {
            hint = "Message Jarvis..."
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_TEXT)
            textSize = 15f
            maxLines = 3
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = roundedBackground(COLOR_SURFACE, dp(18))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitInput()
                    true
                } else {
                    false
                }
            }
        }
        inputRow.addView(
            commandInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        )
        speechButton = actionButton("Mic", ::toggleListening)
        inputRow.addView(speechButton)
        val sendButton = actionButton("Send", ::submitInput).apply {
            setTextColor(COLOR_BACKGROUND)
            background = roundedBackground(COLOR_ACCENT, dp(16))
        }
        inputRow.addView(sendButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(48)
        ).apply { marginStart = dp(8) })
        page.addView(inputRow)

        val appsButton = actionButton("▦   Apps") { showAppDrawer() }.apply {
            gravity = Gravity.CENTER
            textSize = 15f
            background = roundedBackground(COLOR_SURFACE, dp(16))
        }
        page.addView(appsButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48)
        ).apply { topMargin = dp(10) })
        setContentView(page)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(COLOR_TEXT)
            minWidth = dp(48)
            minHeight = dp(48)
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(COLOR_SURFACE, dp(16))
            setOnClickListener { action() }
        }

    private fun addMessage(text: String, fromUser: Boolean, speak: Boolean = false) {
        val bubble = TextView(this).apply {
            this.text = text
            setTextColor(if (fromUser) COLOR_BACKGROUND else COLOR_TEXT)
            textSize = 15f
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = roundedBackground(if (fromUser) COLOR_ACCENT else COLOR_SURFACE, dp(18))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (fromUser) Gravity.END else Gravity.START
            addView(bubble, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = if (fromUser) dp(54) else 0
                marginEnd = if (fromUser) 0 else dp(54)
                bottomMargin = dp(10)
            })
        }
        messages.addView(row)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
        if (speak && speechReady) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-response")
        }
    }

    private fun submitInput() {
        val prompt = commandInput.text.toString().trim()
        if (prompt.isEmpty()) return
        commandInput.text.clear()
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(commandInput.windowToken, 0)
        addMessage(prompt, true)
        if (handleLocalCommand(prompt)) return

        val provider = preferences.getString(KEY_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        val apiKey = preferences.getString(KEY_API_KEY, "").orEmpty().trim()
        if (apiKey.isEmpty()) {
            addMessage("Set a Gemini or Groq API key in Settings to chat with the AI. I can still open apps and handle local commands.", false)
            return
        }
        addMessage("Thinking...", false)
        backgroundExecutor.execute {
            val answer = try {
                requestAssistantResponse(provider, apiKey, prompt)
            } catch (error: Exception) {
                "I couldn't reach the AI service: ${error.message ?: "request failed"}"
            }
            runOnUiThread {
                val last = messages.getChildAt(messages.childCount - 1) as? LinearLayout
                (last?.getChildAt(0) as? TextView)?.let { bubble ->
                    if (bubble.text == "Thinking...") {
                        messages.removeView(last)
                    }
                }
                addMessage(answer, false, speak = true)
            }
        }
    }

    private fun handleLocalCommand(command: String): Boolean {
        val normalized = command.trim()
        val scrollMatch = Regex("(?i)^scroll\\s+(down|up)$").matchEntire(normalized)
        if (scrollMatch != null) {
            val service = JarvisAccessibilityService.activeService
            if (service == null) {
                addMessage("To use scroll commands, enable Jarvis Assistant in Android Accessibility settings.", false)
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                val started = service.scroll(scrollMatch.groupValues[1].equals("down", ignoreCase = true))
                addMessage(if (started) "Scrolling ${scrollMatch.groupValues[1].lowercase(Locale.ROOT)}." else "I couldn't start the scroll gesture.", false)
            }
            return true
        }
        val tapMatch = Regex("(?i)^tap\\s+(\\d+)[,\\s]+(\\d+)$").matchEntire(normalized)
        if (tapMatch != null) {
            val service = JarvisAccessibilityService.activeService
            if (service == null) {
                addMessage("Enable Jarvis Assistant in Android Accessibility settings before using tap commands.", false)
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                val started = service.tap(tapMatch.groupValues[1].toInt(), tapMatch.groupValues[2].toInt())
                addMessage(if (started) "Tap gesture sent." else "That tap coordinate is outside the screen or the gesture could not start.", false)
            }
            return true
        }
        val openMatch = Regex("(?i)^open\\s+(.+)$").matchEntire(normalized)
        if (openMatch != null) {
            val appName = openMatch.groupValues[1].trim()
            if (appName.isEmpty()) {
                addMessage("Tell me the name of the app to open.", false)
                return true
            }
            if (appName.equals("settings", ignoreCase = true)) {
                return launchIntent(Intent(android.provider.Settings.ACTION_SETTINGS), "Settings")
            }
            val app = findApp(appName)
            if (app == null) {
                addMessage("I couldn't find an installed app named “$appName”. Try Apps to browse installed apps.", false)
            } else {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launchIntent == null) {
                    addMessage("“${app.loadLabel(packageManager)}” doesn't have a launchable activity.", false)
                } else {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntentSafely(launchIntent, "Opening ${app.loadLabel(packageManager)}.")
                }
            }
            return true
        }
        return false
    }

    private fun launchIntent(intent: Intent, label: String): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntentSafely(intent, "Opening $label.")
        return true
    }

    private fun launchIntentSafely(intent: Intent, successMessage: String) {
        try {
            startActivity(intent)
            addMessage(successMessage, false)
        } catch (_: android.content.ActivityNotFoundException) {
            addMessage("I couldn't open that destination on this device.", false)
        } catch (_: SecurityException) {
            addMessage("Android didn't allow that app to be opened.", false)
        }
    }

    private fun findApp(name: String): ResolveInfo? {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = packageManager.queryIntentActivities(launchIntent, 0)
        val query = normalizeName(name)
        return candidates.firstOrNull {
            normalizeName(it.loadLabel(packageManager).toString()) == query
        } ?: candidates.firstOrNull {
            val label = normalizeName(it.loadLabel(packageManager).toString())
            label.contains(query) || query.contains(label)
        }
    }

    private fun showAppDrawer() {
        val drawer = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            setBackgroundColor(COLOR_BACKGROUND)
        }
        content.addView(TextView(this).apply {
            text = "Your apps"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(COLOR_TEXT)
        })
        val search = EditText(this).apply {
            hint = "Search installed apps"
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_TEXT)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = roundedBackground(COLOR_SURFACE, dp(16))
        }
        content.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14); bottomMargin = dp(10) })
        val appList = installedApps()
        val appAdapter = AppListAdapter(appList)
        val list = ListView(this).apply {
            adapter = appAdapter
            divider = null
            setBackgroundColor(COLOR_BACKGROUND)
            setOnItemClickListener { _, _, position, _ ->
                val app = appAdapter.getItem(position)
                val launchIntent = packageManager.getLaunchIntentForPackage(app.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(launchIntent)
                        drawer.dismiss()
                    } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "This app could not be opened.", Toast.LENGTH_SHORT).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(this@MainActivity, "Android didn't allow this app to be opened.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        content.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        drawer.setContentView(content)
        drawer.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        drawer.show()
        drawer.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun installedApps(): List<ResolveInfo> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launchIntent, 0).sortedBy {
            it.loadLabel(packageManager).toString().lowercase(Locale.ROOT)
        }
    }

    private fun showSettings() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        form.addView(TextView(this).apply {
            text = "AI provider"
            setTextColor(COLOR_TEXT)
            textSize = 14f
        })
        val providerPicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Gemini", "Groq")
            )
            setSelection(if (preferences.getString(KEY_PROVIDER, PROVIDER_GEMINI) == PROVIDER_GROQ) 1 else 0)
        }
        form.addView(providerPicker)
        form.addView(TextView(this).apply {
            text = "API key (stored on this device)"
            setTextColor(COLOR_TEXT)
            textSize = 14f
            setPadding(0, dp(14), 0, dp(4))
        })
        val keyInput = EditText(this).apply {
            hint = "Paste your API key"
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_TEXT)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(preferences.getString(KEY_API_KEY, ""))
            setSelection(text.length)
        }
        form.addView(keyInput)
        AlertDialog.Builder(this)
            .setTitle("Assistant settings")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear key") { _, _ ->
                preferences.edit().remove(KEY_API_KEY).apply()
                Toast.makeText(this, "API key removed.", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Save") { _, _ ->
                val provider = if (providerPicker.selectedItemPosition == 1) PROVIDER_GROQ else PROVIDER_GEMINI
                preferences.edit()
                    .putString(KEY_PROVIDER, provider)
                    .putString(KEY_API_KEY, keyInput.text.toString().trim())
                    .apply()
                Toast.makeText(this, "Assistant settings saved on this device.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun toggleListening() {
        if (listening) {
            speechRecognizer?.stopListening()
            listening = false
            speechButton.text = "Mic"
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage("Speech recognition isn't available on this device.", false)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        listening = false
                        speechButton.text = "Mic"
                    }
                    override fun onError(error: Int) {
                        listening = false
                        speechButton.text = "Mic"
                        if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                            error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        ) {
                            addMessage("Speech recognition failed. Please try again.", false)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        listening = false
                        speechButton.text = "Mic"
                        val spoken = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                        if (!spoken.isNullOrBlank()) {
                            commandInput.setText(spoken)
                            submitInput()
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Jarvis")
        }
        listening = true
        speechButton.text = "Stop"
        speechRecognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                addMessage("Microphone permission is needed for voice input.", false)
            }
        }
    }

    private fun requestAssistantResponse(provider: String, apiKey: String, prompt: String): String {
        val endpoint: String
        val body: JSONObject
        if (provider == PROVIDER_GROQ) {
            endpoint = "https://api.groq.com/openai/v1/chat/completions"
            body = JSONObject()
                .put("model", "llama-3.3-70b-versatile")
                .put("messages", JSONArray().put(JSONObject()
                    .put("role", "user")
                    .put("content", prompt)))
        } else {
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
            body = JSONObject().put(
                "contents",
                JSONArray().put(JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", prompt))
                ))
            )
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (provider == PROVIDER_GROQ) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            } else {
                setRequestProperty("x-goog-api-key", apiKey)
            }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = try {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                } catch (_: org.json.JSONException) {
                    null
                }?.takeIf(String::isNotBlank)
                throw IOException(detail ?: "HTTP $status from $provider.")
            }
            val response = JSONObject(responseText)
            val answer = if (provider == PROVIDER_GROQ) {
                response.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
            } else {
                response.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
            }
            return answer?.takeIf(String::isNotBlank) ?: "The AI service returned an empty response."
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeName(value: String): String =
        value.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class AppListAdapter(apps: List<ResolveInfo>) : BaseAdapter() {
        private val allApps = apps
        private var visibleApps = apps

        override fun getCount(): Int = visibleApps.size
        override fun getItem(position: Int): ResolveInfo = visibleApps[position]
        override fun getItemId(position: Int): Long = visibleApps[position].activityInfo.packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(ImageView(this@MainActivity), LinearLayout.LayoutParams(dp(42), dp(42)))
                addView(TextView(this@MainActivity).apply {
                    setTextColor(COLOR_TEXT)
                    textSize = 16f
                    setPadding(dp(12), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            val app = getItem(position)
            (row.getChildAt(0) as ImageView).setImageDrawable(app.loadIcon(packageManager))
            (row.getChildAt(1) as TextView).text = app.loadLabel(packageManager)
            return row
        }

        fun filter(query: String) {
            val normalized = normalizeName(query)
            visibleApps = if (normalized.isEmpty()) allApps else allApps.filter {
                normalizeName(it.loadLabel(packageManager).toString()).contains(normalized)
            }
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "jarvis_settings"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_API_KEY = "ai_api_key"
        private const val PROVIDER_GEMINI = "gemini"
        private const val PROVIDER_GROQ = "groq"
        private const val REQUEST_RECORD_AUDIO = 41
        private const val NETWORK_TIMEOUT_MS = 20_000
        private const val COLOR_BACKGROUND = 0xFF101319.toInt()
        private const val COLOR_SURFACE = 0xFF20262E.toInt()
        private const val COLOR_ACCENT = 0xFF70D6C2.toInt()
        private const val COLOR_TEXT = 0xFFF1F4F6.toInt()
        private const val COLOR_MUTED = 0xFF929BA6.toInt()
    }
}
