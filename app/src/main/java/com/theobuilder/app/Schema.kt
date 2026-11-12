package com.theobuilder.app

import android.content.Context
import kotlin.runCatching
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FieldSpec(
    val name: String,
    val label: String,
    val description: String,
    val type: String,
    val group: String,
    val required: Boolean,
    val default: String?,
    val draftKey: String?,
    val min: Double?,
    val source: String?
)

data class ObjectSpec(
    val id: String,
    val label: String,
    val draftType: String,
    val compatibility: String,
    val sources: List<String>,
    val fields: List<FieldSpec>
)

data class PluginProject(
    val id: String = UUID.randomUUID().toString(),
    val typeId: String,
    val values: Map<String, String>,
    val textureFile: String? = null,
    val textureName: String? = null,
    val updated: Long = System.currentTimeMillis()
)

object SchemaStore {
    fun load(c: Context): List<ObjectSpec> {
        val json = JSONObject(c.assets.open("api-schema.json").bufferedReader().use { it.readText() })
        val typesArray = json.getJSONArray("types")
        return (0 until typesArray.length()).map { i ->
            val o = typesArray.getJSONObject(i)
            val propertiesArray = o.getJSONArray("properties")
            ObjectSpec(
                id = o.getString("id"),
                label = o.getString("label"),
                draftType = o.getString("draftType"),
                compatibility = o.optString("compatibility"),
                sources = strings(o.getJSONArray("sources")),
                fields = (0 until propertiesArray.length()).map { j ->
                    val x = propertiesArray.getJSONObject(j)
                    FieldSpec(
                        name = x.getString("name"),
                        label = x.optString("label"),
                        description = x.optString("description"),
                        type = x.optString("type"),
                        group = x.optString("group"),
                        required = x.optBoolean("required"),
                        default = if (x.has("default") && !x.isNull("default")) x.get("default").toString() else null,
                        draftKey = if (x.has("draftKey")) x.getString("draftKey") else null,
                        min = if (x.has("minimum")) x.getDouble("minimum") else null,
                        source = x.optString("source")
                    )
                }
            )
        }
    }

    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
}

object ProjectStore {
    private const val PREF = "theobuilder-local"
    private const val KEY = "projects"

    fun load(c: Context): List<PluginProject> = runCatching {
        val jsonArray = JSONArray(c.getSharedPreferences(PREF, 0).getString(KEY, "[]"))
        (0 until jsonArray.length()).map { i ->
            val o = jsonArray.getJSONObject(i)
            val v = o.optJSONObject("values") ?: JSONObject()
            val m = buildMap {
                val it = v.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    put(k, v.optString(k, ""))
                }
            }
            PluginProject(
                id = o.optString("id", UUID.randomUUID().toString()),
                typeId = o.optString("typeId", "park"),
                values = m,
                textureFile = o.optString("textureFile").takeIf { it.isNotBlank() },
                textureName = o.optString("textureName").takeIf { it.isNotBlank() },
                updated = o.optLong("updated")
            )
        }
    }.getOrDefault(emptyList())

    fun save(c: Context, ps: List<PluginProject>) {
        val jsonArray = JSONArray()
        ps.forEach { p ->
            val v = JSONObject()
            p.values.forEach { (k, x) -> v.put(k, x) }
            jsonArray.put(
                JSONObject()
                    .put("id", p.id)
                    .put("typeId", p.typeId)
                    .put("values", v)
                    .put("textureFile", p.textureFile ?: JSONObject.NULL)
                    .put("textureName", p.textureName ?: JSONObject.NULL)
                    .put("updated", p.updated)
            )
        }
        c.getSharedPreferences(PREF, 0).edit().putString(KEY, jsonArray.toString()).apply()
    }
}

object ProjectFactory {
    fun create(s: ObjectSpec): PluginProject {
        val m = s.fields.associate { f ->
            f.name to when (f.name) {
                "id" -> "\$theobuilder_${s.id}_${UUID.randomUUID().toString().take(8)}"
                "title" -> "${s.label} draft"
                else -> f.default ?: ""
            }
        }
        return PluginProject(typeId = s.id, values = m)
    }

    fun duplicate(p: PluginProject, s: ObjectSpec): PluginProject {
        val m = p.values.toMutableMap()
        m["id"] = "\$theobuilder_${s.id}_${UUID.randomUUID().toString().take(8)}"
        m["title"] = (m["title"] ?: s.label) + " copy"
        return p.copy(id = UUID.randomUUID().toString(), values = m, updated = System.currentTimeMillis())
    }
}
