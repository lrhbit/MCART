package com.axolotl.mcart.io;

import com.axolotl.mcart.model.Project;
import com.axolotl.mcart.util.AppDirs;
import com.axolotl.mcart.util.MiniJson;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 工程文件读写：
 * <ul>
 *   <li>{@code .mcpx}：ZIP，内含 project.json（全部参数）+ source.png（源图）；</li>
 *   <li>{@code .mcpt} 模板：ZIP，仅 project.json（套用参数，不含图片）；</li>
 *   <li>会话恢复：把当前打开的全部工程自动保存到 mc-pixel-art-data/projects/session/。</li>
 * </ul>
 */
public final class ProjectIO {

    public static final String EXT_PROJECT = ".mcpx";
    public static final String EXT_TEMPLATE = ".mcpt";

    private ProjectIO() {}

    /** 保存工程（.mcpx） */
    public static void save(Project p, Path file) throws IOException {
        if (p.getSourceImage() != null && p.getSourceBytes() == null) {
            p.setSourceImage(p.getSourceImage(), p.getSourceImageName());
        }
        Files.createDirectories(file.toAbsolutePath().getParent() == null
                ? Paths.get(".") : file.toAbsolutePath().getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("mcpixel/project.json"));
            zip.write(MiniJson.write(p.toJson()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (p.getSourceBytes() != null) {
                zip.putNextEntry(new ZipEntry("mcpixel/source.png"));
                zip.write(p.getSourceBytes());
                zip.closeEntry();
            }
        }
        p.setProjectFile(file.toFile());
        p.clearDirty();
    }

    /** 保存模板（.mcpt，仅参数） */
    public static void saveTemplate(Project p, Path file) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("mcpixel/template.json"));
            zip.write(MiniJson.write(p.toJson()).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    /** 打开工程或模板（.mcpx / .mcpt） */
    public static Project open(Path file) throws IOException {
        byte[] json = null;
        byte[] png = null;
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                byte[] data = readAll(zis);
                if (e.getName().endsWith("project.json") || e.getName().endsWith("template.json")) {
                    json = data;
                } else if (e.getName().endsWith(".png")) {
                    png = data;
                }
            }
        }
        if (json == null) throw new IOException("不是有效的工程文件（缺少 project.json）");
        Map<String, Object> m = MiniJson.parseObject(new String(json, StandardCharsets.UTF_8));
        Project p = Project.fromJson(m, png);
        p.setProjectFile(file.toFile());
        p.clearDirty();
        return p;
    }

    /** 把模板参数套用到现有工程（restore 不动源图，源图与图片名保留） */
    public static void applyTemplate(Project target, Path template) throws IOException {
        Project t = open(template);
        target.restore(t.snapshot());
        target.markDirty();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    // ================= 会话恢复 =================

    /** 保存整个会话（多个工程） */
    public static void saveSession(List<Project> projects, int activeIndex) {
        try {
            Path dir = AppDirs.sessionDir();
            AppDirs.ensure();
            // 清旧会话
            if (Files.isDirectory(dir)) {
                try (var s = Files.list(dir)) {
                    s.forEach(f -> {
                        try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                    });
                }
            }
            List<Object> entries = new ArrayList<>();
            for (int i = 0; i < projects.size(); i++) {
                Project p = projects.get(i);
                if (!p.hasImage() && p.getProjectFile() == null) continue;
                String fn = "doc-" + i + ".mcpx";
                try {
                    save(p, dir.resolve(fn));
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("file", fn);
                    e.put("original", p.getProjectFile() == null ? null
                            : p.getProjectFile().getAbsolutePath());
                    entries.add(e);
                } catch (IOException ignored) {
                }
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("active", activeIndex);
            meta.put("docs", entries);
            Files.writeString(dir.resolve("session.json"), MiniJson.write(meta), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** 读取上次会话；无会话返回 null */
    public static List<Project> loadSession() {
        try {
            Path dir = AppDirs.sessionDir();
            Path meta = dir.resolve("session.json");
            if (!Files.isRegularFile(meta)) return null;
            Map<String, Object> m = MiniJson.parseObject(Files.readString(meta, StandardCharsets.UTF_8));
            List<Project> list = new ArrayList<>();
            for (Object o : MiniJson.arr(m, "docs")) {
                if (!(o instanceof Map<?, ?> raw)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> em = (Map<String, Object>) raw;
                String fn = MiniJson.str(em, "file", "");
                Path f = dir.resolve(fn);
                if (Files.isRegularFile(f)) {
                    try {
                        list.add(open(f));
                    } catch (IOException ignored) {
                    }
                }
            }
            return list.isEmpty() ? null : list;
        } catch (IOException e) {
            return null;
        }
    }

    public static void clearSession() {
        Path dir = AppDirs.sessionDir();
        if (!Files.isDirectory(dir)) return;
        try (var s = Files.list(dir)) {
            s.forEach(f -> {
                try { Files.deleteIfExists(f); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }
}
