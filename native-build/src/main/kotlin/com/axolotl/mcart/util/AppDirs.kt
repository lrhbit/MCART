package com.axolotl.mcart.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 用户数据目录（便携化、配置分离）：所有用户数据统一放在
 * 程序 jar 同级的 `mc-pixel-art-data/` 目录，
 * 不写入用户主目录 / 系统盘，整个软件可随 U 盘 / 文件夹整体迁移。
 *
 * 可用启动参数 `-Dmcart.home=路径` 覆盖数据目录位置。
 */
object AppDirs {

    /** 同级数据目录名 */
    const val DATA_DIR_NAME: String = "mc-pixel-art-data"

    private var root: Path? = null

    /** 数据目录默认放在运行 jar（或 classes 目录）的同级位置。 */
    private fun defaultLocation(): Path {
        try {
            val cs = AppDirs::class.java.protectionDomain.codeSource
            if (cs != null && cs.location != null) {
                val p = Paths.get(cs.location.toURI())
                val dir = if (Files.isRegularFile(p)) p.parent else p
                if (dir != null) return dir.resolve(DATA_DIR_NAME)
            }
        } catch (ignored: Exception) {
            // 无法定位时退回工作目录
        }
        return Paths.get(System.getProperty("user.dir"), DATA_DIR_NAME)
    }

    @JvmStatic
    @Synchronized
    fun root(): Path {
        root?.let { return it }
        val override = System.getProperty("mcart.home")
        return (if (!override.isNullOrBlank()) Paths.get(override) else defaultLocation())
            .also { root = it }
    }

    @JvmStatic fun configFile(): Path = root().resolve("config.properties")
    @JvmStatic fun paletteFile(): Path = root().resolve("palette.properties")
    @JvmStatic fun customBlocksFile(): Path = root().resolve("custom-blocks.json")
    @JvmStatic fun presetsDir(): Path = root().resolve("presets")
    @JvmStatic fun pluginsDir(): Path = root().resolve("plugins")
    @JvmStatic fun pluginBlocksDir(): Path = pluginsDir().resolve("blocks")
    @JvmStatic fun pluginPresetsDir(): Path = pluginsDir().resolve("presets")
    @JvmStatic fun projectsDir(): Path = root().resolve("projects")
    @JvmStatic fun sessionDir(): Path = projectsDir().resolve("session")
    @JvmStatic fun logsDir(): Path = root().resolve("logs")

    /** 确保所有目录存在（失败不致命，仅记录到返回列表） */
    @JvmStatic
    fun ensure(): List<Path> {
        val dirs = listOf(
            root(), presetsDir(), pluginBlocksDir(), pluginPresetsDir(),
            sessionDir(), logsDir(),
        )
        val ok = mutableListOf<Path>()
        for (p in dirs) {
            try {
                Files.createDirectories(p)
                ok.add(p)
            } catch (ignored: Exception) {
                // 目录不可写时程序仍可运行（仅丢失持久化能力）
            }
        }
        return ok
    }
}
