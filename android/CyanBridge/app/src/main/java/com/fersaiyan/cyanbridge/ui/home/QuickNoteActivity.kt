package com.fersaiyan.cyanbridge.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.DateFormat
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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Быстрая заметка — a simple, self-contained local quick-note screen (Module E).
 *
 * Notes are stored locally in this screen's own SharedPreferences as a small JSON array.
 * There is intentionally NO Notion/Telegram/cloud integration here — just a local store so
 * the user can jot something down quickly. It does no networking and touches no other module.
 */
class QuickNoteActivity : AppCompatActivity() {

    private val density: Float by lazy { resources.displayMetrics.density }

    private lateinit var input: EditText
    private lateinit var notesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Быстрая заметка"

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "Быстрая заметка"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Заметки сохраняются локально на телефоне"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })

        input = EditText(this).apply {
            hint = "Введите текст заметки…"
            setHintTextColor(Color.parseColor("#6B7178"))
            setTextColor(Color.WHITE)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B2026"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        root.addView(input)

        root.addView(Button(this).apply {
            text = "Сохранить заметку"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener { saveNote() }
        })

        root.addView(TextView(this).apply {
            text = "Сохранённые заметки"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        })

        notesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(notesContainer)
        }
        root.addView(scroll)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        renderNotes()
    }

    private fun saveNote() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Заметка пустая", Toast.LENGTH_SHORT).show()
            return
        }
        val notes = loadNotes()
        notes.put(
            JSONObject()
                .put("text", text)
                .put("createdAt", System.currentTimeMillis())
        )
        prefs().edit().putString(KEY_NOTES, notes.toString()).apply()
        input.setText("")
        Toast.makeText(this, "Заметка сохранена", Toast.LENGTH_SHORT).show()
        renderNotes()
    }

    private fun renderNotes() {
        notesContainer.removeAllViews()
        val notes = loadNotes()
        if (notes.length() == 0) {
            notesContainer.addView(TextView(this).apply {
                text = "Пока нет сохранённых заметок"
                textSize = 13f
                setTextColor(Color.parseColor("#9AA0A6"))
            })
            return
        }
        // Newest first.
        for (i in notes.length() - 1 downTo 0) {
            val note = notes.optJSONObject(i) ?: continue
            notesContainer.addView(buildNoteView(note.optString("text"), note.optLong("createdAt")))
        }
    }

    private fun buildNoteView(text: String, createdAt: Long): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B2026"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        card.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.WHITE)
        })
        if (createdAt > 0L) {
            card.addView(TextView(this).apply {
                this.text = DateFormat.format("dd.MM.yyyy HH:mm", createdAt)
                textSize = 12f
                setTextColor(Color.parseColor("#6B7178"))
                setPadding(0, dp(6), 0, 0)
            })
        }
        return card
    }

    private fun loadNotes(): JSONArray {
        val raw = prefs().getString(KEY_NOTES, null) ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private const val PREFS = "cyanbridge_quick_notes"
        private const val KEY_NOTES = "notes"
    }
}
