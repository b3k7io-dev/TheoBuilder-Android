package com.theobuilder.app

import android.content.Context
import android.net.Uri
import androidx.core.content.Charsets
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Issue(val error: Boolean, val text: String)

object Validator {
    private val idRegex = Regex("^" + Regex.escape('\$'.toString()) + "[A-Za-z0-9_.-]{3,80}" + '\$')

    fun check(c: Context, p: PluginProject, s: ObjectSpec): List<Issue> {
        val out = mutableListOf<Issue>()
        s.fields.forEach { f ->
            val x = p.values[f.name].orEmpty().trim()
            if (f.required && f.type != "texture" && x.isEmpty()) {
                out += Issue(true, "Заполните «${f.label}»")
            }
            if (f.name == "id" && x.isNotEmpty() && !idRegex.matches(x)) {
                out += Issue(true, "Некорректный ID")
            }
            if (f.type == "number" && x.isNotEmpty()) {
                val n = x.toDoubleOrNull()
                if (n == null || !n.isFinite()) {
                    out += Issue(true, "«${f.label}» должно быть числом")
                } else if (f.min != null && n < f.min) {
                    out += Issue(true, "Минимум для «${f.label}»: ${f.min}")
                }
            }
        }
        if (p.values["width"]?.toIntOrNull()?.let { it > 0 } != true) {
            out += Issue(true, "Ширина должна быть положительным целым")
        }
        if (p.values["height"]?.toIntOrNull()?.let { it > 0 } != true) {
            out += Issue(true, "Высота должна быть положительным целым")
        }
        val f = p.textureFile?.let { File(c.filesDir, "textures/$it") }
        if (f?.isFile != true || f.length() == 0L) {
            out += Issue(true, "Импортируйте PNG перед экспортом")
        }
        out += Issue(false, "Учебный формат; проверка в установленной игре не выполнена")
        return out.distinctBy { it.text }
    }
}

object PluginExporter {
    fun write(c: Context, p: PluginProject, s: ObjectSpec, u: Uri) {
        val errors = Validator.check(c, p, s).filter { it.error }
        check(errors.isEmpty()) { errors.joinToString("; ") { it.text } }

        val png = safe(p.textureName ?: "texture.png")
        val d = JSONObject().put("type", s.draftType)
        s.fields.forEach { f ->
            val k = f.draftKey ?: return@forEach
            if (f.type == "texture") return@forEach
            val raw = p.values[f.name]?.trim().orEmpty()
            if (raw.isBlank()) return@forEach
            val value: Any = when (f.type) {
                "number" -> raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: error("Некорректное число: ${f.label}")
                "boolean" -> raw.toBooleanStrictOrNull() ?: error("Некорректный boolean: ${f.label}")
                else -> raw
            }
            d.put(k, value)
        }
        d.put("frames", JSONArray().put(JSONObject().put("bmp", png)))

        val title = p.values["title"].orEmpty().ifBlank { s.label }
        val manifest = JSONObject()
            .put("id", "\$theobuilder_${s.id}_${p.id.take(8)}")
            .put("version", 1)
            .put("title", title)
            .put("author", p.values["author"].orEmpty())

        val image = File(c.filesDir, "textures/${p.textureFile}")
        check(image.isFile)
        val output = c.contentResolver.openOutputStream(u) ?: error("Не удалось открыть файл")
        ZipOutputStream(output.buffered()).use { z ->
            text(z, "plugin.manifest", manifest.toString(2))
            text(z, "drafts.json", JSONArray().put(d).toString(2))
            z.putNextEntry(ZipEntry(png))
            image.inputStream().buffered().use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    private fun text(z: ZipOutputStream, n: String, t: String) {
        z.putNextEntry(ZipEntry(n))
        z.write(t.toByteArray(Charsets.UTF_8))
        z.closeEntry()
    }

    private fun safe(s: String) = s.substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(80)
        .ifBlank { "texture.png" }
}
