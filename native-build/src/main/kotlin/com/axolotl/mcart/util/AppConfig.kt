package com.axolotl.mcart.util

import java.nio.file.Files
import java.util.Properties

/**
 * 全局配置（config.properties）：主题、语言、布局模式、最近文件、窗口尺寸等。
 * 所有读写均容错：文件损坏 / 目录不可写时回退默认值，绝不影响主程序启动。
 */
object AppConfig {

    const val THEME_DARK: String = "dark"
    const val THEME_LIGHT: String = "light"
    const val THEME_HIGH_CONTRAST: String = "high-contrast"

    const val LANG_ZH: String = "zh"
    const val LANG_EN: String = "en"

    const val LAYOUT_FULL: String = "full"
    const val LAYOUT_PREVIEW: String = "preview"
    const val LAYOUT_SOURCE: String = "source"

    private val properties: Properties = Properties()
    private var loaded: Boolean = false

    @JvmStatic
    @Synchronized
    fun load() {
        if (loaded) return
        loaded = true
        AppDirs.ensure()
        val f = AppDirs.configFile()
        if (Files.isRegularFile(f)) {
            try {
                Files.newInputStream(f).use(properties::load)
            } catch (ignored: Exception) {
            }
        }
    }

    @JvmStatic
    fun get(key: String, def: String?): String? {
        load()
        return properties.getProperty(key, def)
    }

    @JvmStatic
    fun getInt(key: String, def: Int): Int =
        try {
            get(key, def.toString())!!.toInt()
        } catch (e: NumberFormatException) {
            def
        }

    @JvmStatic
    fun getBool(key: String, def: Boolean): Boolean =
        get(key, def.toString()).toBoolean()

    @JvmStatic
    fun set(key: String, value: String?) {
        load()
        if (value == null) properties.remove(key) else properties.setProperty(key, value)
    }

    @JvmStatic
    @Synchronized
    fun save() {
        load()
        AppDirs.ensure()
        try {
            Files.newOutputStream(AppDirs.configFile()).use {
                properties.store(it, "mc-pixel-art-converter config")
            }
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic fun theme(): String = get("theme", THEME_DARK)!!
    @JvmStatic fun language(): String = get("language", LANG_ZH)!!
    @JvmStatic fun layout(): String = get("layout", LAYOUT_FULL)!!
    @JvmStatic fun restoreSession(): Boolean = getBool("restoreSession", true)

    @JvmStatic fun setTheme(v: String) = set("theme", v)
    @JvmStatic fun setLanguage(v: String) = set("language", v)
    @JvmStatic fun setLayout(v: String) = set("layout", v)

    /** 最近打开（工程 / 图片），最多 8 个，新的在前 */
    @JvmStatic
    fun recentFiles(): List<String> {
        val raw = get("recent", "")!!
        return if (raw.isBlank()) emptyList()
        else raw.split("|").filter { it.isNotBlank() }
    }

    @JvmStatic
    fun pushRecent(path: String?) {
        if (path.isNullOrBlank()) return
        val list = mutableListOf(path)
        for (p in recentFiles()) {
            if (p != path && list.size < 8) list.add(p)
        }
        set("recent", list.joinToString("|"))
        save()
    }

    @JvmStatic
    fun clearRecent() {
        set("recent", "")
        save()
    }
}
