package com.axolotl.mcart.i18n

import com.axolotl.mcart.util.AppConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * 极简国际化：中文 / 英文，运行时可切换（切换后重建主窗口生效）。
 * 词条以 UTF-8 properties 存放在 classpath:/i18n/messages_{lang}.properties。
 */
object I18n {

    private val zh: MutableMap<String, String> = HashMap()
    private val en: MutableMap<String, String> = HashMap()
    private var lang: String = AppConfig.language()

    init {
        load("zh", zh)
        load("en", en)
    }

    private fun load(lang: String, target: MutableMap<String, String>) {
        val res = "/i18n/messages_$lang.properties"
        try {
            I18n::class.java.getResourceAsStream(res)?.use { input ->
                BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { r ->
                    r.lineSequence().forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        val eq = line.indexOf('=')
                        if (eq <= 0) return@forEach
                        val key = line.substring(0, eq).trim()
                        val value = line.substring(eq + 1).trim()
                        target[key] = value.replace("\\n", "\n")
                    }
                }
            }
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun language(): String = lang

    @JvmStatic
    fun setLanguage(l: String?) {
        lang = if (l == AppConfig.LANG_EN) AppConfig.LANG_EN else AppConfig.LANG_ZH
    }

    @JvmStatic
    fun t(key: String): String {
        val m = if (lang == AppConfig.LANG_EN) en else zh
        return m[key] ?: zh[key] ?: key
    }

    @JvmStatic
    fun t(key: String, vararg args: Any?): String {
        var s = t(key)
        for (i in args.indices) {
            s = s.replace("{$i}", args[i].toString())
        }
        return s
    }

    @JvmStatic
    fun isEn(): Boolean = lang == AppConfig.LANG_EN
}
