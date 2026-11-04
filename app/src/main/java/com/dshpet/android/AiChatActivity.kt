package com.dshpet.android

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AiChatActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var input: EditText
    private lateinit var result: TextView
    private lateinit var send: Button

    private val prefs by lazy { getSharedPreferences("dafeiyu_ai", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0.45f)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val d = resources.displayMetrics.density
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            setOnClickListener { finish() }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * d).toInt(), (16 * d).toInt(), (18 * d).toInt(), (14 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 20f * d
                setColor(0xFF161A21.toInt())
                setStroke((1 * d).toInt(), 0xFF2C3442.toInt())
            }
            setOnClickListener { /* 吃掉点击 */ }
        }

        card.addView(TextView(this).apply {
            text = "问大肥鱼 · DeepSeek"
            setTextColor(0xFFEAF1F8.toInt())
            textSize = 17f
            setPadding(0, 0, 0, (10 * d).toInt())
        })

        input = EditText(this).apply {
            hint = "输入问题，比如：今天吃什么"
            setHintTextColor(0xFF6E7A8A.toInt())
            setTextColor(0xFFEAF1F8.toInt())
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
            maxLines = 4
            setPadding((10 * d).toInt(), (8 * d).toInt(), (10 * d).toInt(), (8 * d).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 12f * d
                setColor(0xFF0E1218.toInt())
                setStroke((1 * d).toInt(), 0xFF2C3442.toInt())
            }
        }
        card.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (10 * d).toInt(), 0, 0)
        }
        send = Button(this).apply {
            text = "发送"
            setOnClickListener { ask() }
        }
        row.addView(send)
        row.addView(Button(this).apply {
            text = "关闭"
            setOnClickListener { finish() }
        })
        card.addView(row)

        result = TextView(this).apply {
            setTextColor(0xFFCFDCEA.toInt())
            textSize = 14f
            setPadding(0, (12 * d).toInt(), 0, 0)
            visibility = View.GONE
        }
        card.addView(result)

        val scroll = ScrollView(this)
        scroll.addView(card)
        root.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { setMargins((18 * d).toInt(), 0, (18 * d).toInt(), 0) })

        setContentView(root)
    }

    private fun ask() {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return toast("先输入问题")
        val key = prefs.getString("key", "").orEmpty()
        if (key.isBlank()) {
            // 第一次用：把输入框当 Key 输入
            input.hint = "这里填 DeepSeek API Key（sk-...），再点发送"
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            input.setText("")
            // 用一个标记，下一次点击保存 Key
            send.text = "保存 Key"
            send.setOnClickListener {
                val k = input.text.toString().trim()
                if (!k.startsWith("sk-")) { toast("Key 应以 sk- 开头"); return@setOnClickListener }
                prefs.edit().putString("key", k).apply()
                toast("Key 已保存")
                finish()
            }
            return
        }
        send.isEnabled = false
        result.visibility = View.VISIBLE
        result.text = "思考中..."
        Thread {
            val r = callDeepSeek(q, key)
            main.post {
                send.isEnabled = true
                result.text = r.getOrElse { "出错：${it.message}" }
            }
        }.start()
    }

    private fun callDeepSeek(q: String, key: String): Result<String> {
        return try {
            val body = JSONObject()
                .put("model", "deepseek-chat")
                .put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", q)
                )).toString()
            val conn = (URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $key")
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, "UTF-8")).readText()
            if (code !in 200..299) return Result.failure(Exception("HTTP $code: $text"))
            val content = JSONObject(text).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content")
            Result.success(content)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
