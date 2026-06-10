package com.fersaiyan.cyanbridge.automation.runtime

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.home.v2.V2Theme
import com.fersaiyan.cyanbridge.ui.home.v2.v2dp

/**
 * The editor / runner for a single automation action (Module G).
 *
 * One parameterized Activity backs every [AutomationActionType]: it renders only the fields the
 * given type needs, persists to [AutomationActionStore], and runs the action through
 * [AutomationExecutor] (network actions on a worker thread, UI actions on the main thread).
 * Results are shown inline in a result card; nothing here can crash the app — every run returns
 * an [AutomationActionResult].
 *
 * Opened in two ways:
 *  - new action: pass [EXTRA_TYPE] (a [AutomationActionType.storageKey]).
 *  - edit/run saved action: also pass [EXTRA_ACTION_ID].
 *
 * Styling reuses the V2 design tokens ([V2Theme]) so it matches the rest of the product shell.
 */
class AutomationActionEditorActivity : AppCompatActivity() {

    private lateinit var type: AutomationActionType
    private lateinit var store: AutomationActionStore
    private var existingId: String? = null

    private lateinit var resultContainer: LinearLayout

    // Field references — only the ones relevant to [type] are created.
    private var nameField: EditText? = null
    private var urlField: EditText? = null
    private var headersField: EditText? = null
    private var bodyField: EditText? = null
    private var intentActionField: EditText? = null
    private var packageField: EditText? = null
    private var dataUriField: EditText? = null
    private var extrasField: EditText? = null
    private var textField: EditText? = null
    private var methodSelected: String = "GET"
    private var settingsSelected: String = AutomationExecutor.settingsPresets.first().key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        type = AutomationActionType.fromStorageKey(intent.getStringExtra(EXTRA_TYPE))
            ?: AutomationActionType.HTTP_REQUEST
        store = AutomationActionStore(this)
        existingId = intent.getStringExtra(EXTRA_ACTION_ID)
        val existing = existingId?.let { store.get(it) }
        title = type.displayName

        val pad = v2dp(16)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(title(type.displayName))
        column.addView(subtitle(subtitleFor(type)))

        nameField = field(column, "Название", "Например, ${type.displayName}")
        buildTypeFields(column)
        buildButtons(column)

        resultContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = v2dp(16)
            }
        }
        column.addView(resultContainer)

        prefill(existing)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(V2Theme.BG)
            isFillViewport = true
            addView(column)
        })
    }

    // --- Type-specific fields ----------------------------------------------------------------

    private fun buildTypeFields(column: LinearLayout) {
        when (type) {
            AutomationActionType.HTTP_REQUEST -> {
                column.addView(label("Метод"))
                column.addView(methodSelector())
                urlField = field(column, "URL", "https://example.com/api", url = true)
                headersField = field(column, "Заголовки (Key: Value, по строке)", "Authorization: Bearer …", multiline = true)
                bodyField = field(column, "Тело запроса", "{ }", multiline = true)
            }
            AutomationActionType.WEBHOOK_POST -> {
                urlField = field(column, "URL", "https://hooks.example.com/…", url = true)
                bodyField = field(column, "JSON payload", DEFAULT_WEBHOOK_PAYLOAD, multiline = true)
            }
            AutomationActionType.ANDROID_INTENT -> {
                intentActionField = field(column, "Action", "android.intent.action.VIEW")
                packageField = field(column, "Package (необязательно)", "com.example.app")
                dataUriField = field(column, "Data URI (необязательно)", "https://example.com")
                extrasField = field(column, "Extras (key=value, по строке)", "title=Привет", multiline = true)
            }
            AutomationActionType.OPEN_APP -> {
                packageField = field(column, "Package", "com.android.chrome")
                column.addView(secondaryButton("Выбрать из установленных") { showInstalledAppsPicker() })
            }
            AutomationActionType.OPEN_SETTINGS -> {
                column.addView(label("Раздел настроек"))
                column.addView(settingsSelector())
            }
            AutomationActionType.SHARE_TEXT -> {
                textField = field(column, "Текст", "Текст для отправки…", multiline = true)
            }
        }
    }

    private fun buildButtons(column: LinearLayout) {
        when (type) {
            AutomationActionType.HTTP_REQUEST,
            AutomationActionType.WEBHOOK_POST,
            AutomationActionType.ANDROID_INTENT -> {
                column.addView(primaryButton("Тест") { run(persistFirst = false) })
                column.addView(secondaryButton("Сохранить") { save() })
                column.addView(secondaryButton("Выполнить") { run(persistFirst = true) })
            }
            AutomationActionType.OPEN_APP,
            AutomationActionType.OPEN_SETTINGS -> {
                column.addView(primaryButton("Открыть") { run(persistFirst = false) })
                column.addView(secondaryButton("Сохранить") { save() })
            }
            AutomationActionType.SHARE_TEXT -> {
                column.addView(primaryButton("Поделиться") { run(persistFirst = false) })
                column.addView(secondaryButton("Сохранить") { save() })
            }
        }
    }

    // --- Collect / persist / run -------------------------------------------------------------

    private fun collect(): AutomationAction {
        val name = nameField?.text?.toString()?.trim().orEmpty().ifBlank { type.displayName }
        return AutomationAction(
            id = existingId ?: AutomationAction(type = type).id,
            type = type,
            name = name,
            method = methodSelected,
            url = urlField?.text?.toString()?.trim().orEmpty(),
            headers = headersField?.text?.toString().orEmpty(),
            body = bodyField?.text?.toString().orEmpty(),
            intentAction = intentActionField?.text?.toString()?.trim().orEmpty(),
            packageName = packageField?.text?.toString()?.trim().orEmpty(),
            dataUri = dataUriField?.text?.toString()?.trim().orEmpty(),
            extras = extrasField?.text?.toString().orEmpty(),
            settingsTarget = settingsSelected,
            text = textField?.text?.toString().orEmpty(),
        )
    }

    private fun save() {
        val action = collect()
        runCatching { store.save(action) }
            .onSuccess {
                existingId = action.id
                Toast.makeText(this, "Сохранено в «Мои действия»", Toast.LENGTH_SHORT).show()
            }
            .onFailure { Toast.makeText(this, "Не удалось сохранить", Toast.LENGTH_SHORT).show() }
    }

    private fun run(persistFirst: Boolean) {
        val action = collect()
        if (persistFirst) {
            runCatching { store.save(action) }.onSuccess { existingId = action.id }
        }
        showResultLoading()
        if (AutomationExecutor.isNetwork(type)) {
            Thread {
                val result = runCatching { AutomationExecutor.executeNetwork(action) }
                    .getOrElse { AutomationActionResult.error("Ошибка", it.message ?: "Сбой выполнения") }
                runOnUiThread { showResult(result) }
            }.start()
        } else {
            val result = runCatching { AutomationExecutor.executeUi(this, action) }
                .getOrElse { AutomationActionResult.error("Ошибка", it.message ?: "Сбой выполнения") }
            showResult(result)
        }
    }

    // --- Installed apps picker (Open App) ----------------------------------------------------

    private fun showInstalledAppsPicker() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        val apps = runCatching {
            pm.queryIntentActivities(launcher, 0)
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    val label = info.loadLabel(pm).toString().ifBlank { pkg }
                    label to pkg
                }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
        }.getOrDefault(emptyList())

        if (apps.isEmpty()) {
            Toast.makeText(this, "Не удалось получить список приложений", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { "${it.first}\n${it.second}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Установленные приложения")
            .setItems(labels) { _, which ->
                packageField?.setText(apps[which].second)
                if (nameField?.text?.isNotBlank() != true) nameField?.setText(apps[which].first)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // --- Result rendering --------------------------------------------------------------------

    private fun showResultLoading() {
        resultContainer.removeAllViews()
        resultContainer.addView(resultCard("Выполняется…", "", isError = false, accentLoading = true))
    }

    private fun showResult(result: AutomationActionResult) {
        resultContainer.removeAllViews()
        val sb = StringBuilder()
        result.statusCode?.let { sb.append("Код: ").append(it).append('\n') }
        sb.append(result.message)
        result.responsePreview?.takeIf { it.isNotBlank() }?.let {
            sb.append("\n\nОтвет:\n").append(it)
        }
        val header = if (result.success) "✓ ${result.title}" else "✕ ${result.title}"
        resultContainer.addView(resultCard(header, sb.toString(), isError = !result.success))
    }

    private fun resultCard(header: String, body: String, isError: Boolean, accentLoading: Boolean = false): View {
        val accent = when {
            accentLoading -> V2Theme.TEXT_SECONDARY
            isError -> ERROR_COLOR
            else -> V2Theme.ACCENT
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(v2dp(16), v2dp(16), v2dp(16), v2dp(16))
            background = GradientDrawable().apply {
                cornerRadius = v2dp(16).toFloat()
                setColor(V2Theme.CARD)
                setStroke(v2dp(1), accent)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        card.addView(TextView(this).apply {
            text = header
            textSize = 15f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
        })
        if (body.isNotBlank()) {
            card.addView(TextView(this).apply {
                text = body
                textSize = 13f
                setTextColor(V2Theme.TEXT_SECONDARY)
                setPadding(0, v2dp(8), 0, 0)
                setTextIsSelectable(true)
            })
        }
        return card
    }

    // --- Prefill saved values ----------------------------------------------------------------

    private fun prefill(existing: AutomationAction?) {
        if (existing == null) {
            // Sensible default payload for a brand-new webhook.
            if (type == AutomationActionType.WEBHOOK_POST) bodyField?.setText(DEFAULT_WEBHOOK_PAYLOAD)
            return
        }
        nameField?.setText(existing.name)
        urlField?.setText(existing.url)
        headersField?.setText(existing.headers)
        bodyField?.setText(existing.body)
        intentActionField?.setText(existing.intentAction)
        packageField?.setText(existing.packageName)
        dataUriField?.setText(existing.dataUri)
        extrasField?.setText(existing.extras)
        textField?.setText(existing.text)
        if (existing.method.isNotBlank()) selectMethod(existing.method.uppercase())
        if (existing.settingsTarget.isNotBlank()) selectSettings(existing.settingsTarget)
    }

    // --- Small styled widgets (V2 tokens) ----------------------------------------------------

    private val httpMethods = listOf("GET", "POST", "PUT", "DELETE")
    private val methodPills = mutableMapOf<String, TextView>()
    private val settingsPills = mutableMapOf<String, TextView>()

    private fun methodSelector(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        httpMethods.forEach { method ->
            val pill = togglePill(method) { selectMethod(method) }
            methodPills[method] = pill
            row.addView(pill)
        }
        selectMethod(methodSelected)
        return row
    }

    private fun selectMethod(method: String) {
        if (method !in httpMethods) return
        methodSelected = method
        methodPills.forEach { (m, pill) -> stylePill(pill, m == method) }
    }

    private fun settingsSelector(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        AutomationExecutor.settingsPresets.forEach { preset ->
            val pill = togglePill(preset.title) { selectSettings(preset.key) }.apply {
                layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
                    width = MATCH_PARENT
                    topMargin = v2dp(8)
                }
                gravity = Gravity.CENTER
            }
            settingsPills[preset.key] = pill
            column.addView(pill)
        }
        selectSettings(settingsSelected)
        return column
    }

    private fun selectSettings(key: String) {
        settingsSelected = key
        settingsPills.forEach { (k, pill) -> stylePill(pill, k == key) }
    }

    private fun togglePill(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(v2dp(14), v2dp(9), v2dp(14), v2dp(9))
            gravity = Gravity.CENTER
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                rightMargin = v2dp(8)
                topMargin = v2dp(4)
            }
            setOnClickListener { onClick() }
            stylePill(this, false)
        }

    private fun stylePill(pill: TextView, active: Boolean) {
        pill.setTextColor(if (active) V2Theme.ACCENT else V2Theme.TEXT_SECONDARY)
        pill.background = GradientDrawable().apply {
            cornerRadius = v2dp(12).toFloat()
            setColor(if (active) V2Theme.ACCENT_DIM else V2Theme.SURFACE)
            setStroke(v2dp(1), if (active) V2Theme.ACCENT else V2Theme.CARD_STROKE)
        }
    }

    /** Adds a label + styled input to [column] and returns the created [EditText]. */
    private fun field(
        column: LinearLayout,
        labelText: String,
        hint: String,
        multiline: Boolean = false,
        url: Boolean = false,
    ): EditText {
        column.addView(label(labelText))
        val edit = EditText(this).apply {
            this.hint = hint
            setHintTextColor(V2Theme.TEXT_MUTED)
            setTextColor(V2Theme.TEXT)
            textSize = 14f
            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
            } else {
                setSingleLine(true)
            }
            if (url) {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            }
            setPadding(v2dp(14), v2dp(12), v2dp(14), v2dp(12))
            background = GradientDrawable().apply {
                cornerRadius = v2dp(12).toFloat()
                setColor(V2Theme.SURFACE)
                setStroke(v2dp(1), V2Theme.CARD_STROKE)
            }
            contentDescription = labelText
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = v2dp(2)
                bottomMargin = v2dp(4)
            }
        }
        column.addView(edit)
        return edit
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(V2Theme.TEXT_SECONDARY)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, v2dp(12), 0, v2dp(2))
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 24f
        setTextColor(V2Theme.TEXT)
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun subtitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(V2Theme.TEXT_SECONDARY)
        setPadding(0, v2dp(4), 0, v2dp(8))
    }

    private fun primaryButton(text: String, onClick: () -> Unit): View = actionButton(text, onClick, primary = true)
    private fun secondaryButton(text: String, onClick: () -> Unit): View = actionButton(text, onClick, primary = false)

    private fun actionButton(text: String, onClick: () -> Unit, primary: Boolean): View =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (primary) V2Theme.BG else V2Theme.TEXT)
            background = GradientDrawable().apply {
                cornerRadius = v2dp(14).toFloat()
                setColor(if (primary) V2Theme.ACCENT else V2Theme.CARD)
                setStroke(v2dp(1), if (primary) V2Theme.ACCENT else V2Theme.CARD_STROKE)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = v2dp(10)
            }
            setOnClickListener { runCatching { onClick() } }
        }

    companion object {
        const val EXTRA_TYPE = "automation_action_type"
        const val EXTRA_ACTION_ID = "automation_action_id"

        const val DEFAULT_WEBHOOK_PAYLOAD =
            "{\n  \"source\": \"cyanbridge\",\n  \"event\": \"manual_test\",\n  \"message\": \"Hello from CyanBridge\"\n}"

        private val ERROR_COLOR = android.graphics.Color.parseColor("#FF6B6B")

        fun open(activity: AppCompatActivity, type: AutomationActionType, actionId: String? = null) {
            val intent = Intent(activity, AutomationActionEditorActivity::class.java)
                .putExtra(EXTRA_TYPE, type.storageKey)
            if (actionId != null) intent.putExtra(EXTRA_ACTION_ID, actionId)
            activity.startActivity(intent)
        }

        private fun subtitleFor(type: AutomationActionType): String = when (type) {
            AutomationActionType.HTTP_REQUEST -> "Произвольный HTTP-запрос с таймаутами"
            AutomationActionType.WEBHOOK_POST -> "POST JSON на webhook"
            AutomationActionType.ANDROID_INTENT -> "Безопасный запуск системного Intent"
            AutomationActionType.OPEN_APP -> "Открыть установленное приложение"
            AutomationActionType.OPEN_SETTINGS -> "Открыть системный раздел настроек"
            AutomationActionType.SHARE_TEXT -> "Системное меню «Поделиться»"
        }
    }
}
