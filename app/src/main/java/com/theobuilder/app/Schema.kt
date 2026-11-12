package com.theobuilder.app
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class FieldSpec(val name:String,val label:String,val description:String,val type:String,val group:String,val required:Boolean,val default:String?,val draftKey:String?,val min:Double?,val source:String?)
data class ObjectSpec(val id:String,val label:String,val draftType:String,val compatibility:String,val sources:List<String>,val fields:List<FieldSpec>)
data class PluginProject(val id:String=UUID.randomUUID().toString(),val typeId:String,val values:Map<String,String>,val textureFile:String?=null,val textureName:String?=null,val updated:Long=System.currentTimeMillis())
object SchemaStore {
 fun load(c:Context):List<ObjectSpec>{val r=JSONObject(c.assets.open("api-schema.json").bufferedReader().use{it.readText()});val a=r.getJSONArray("types");return (0 until a.length()).map{i->val o=a.getJSONObject(i);val f=o.getJSONArray("properties");ObjectSpec(o.getString("id"),o.getString("label"),o.getString("draftType"),o.optString("compatibility"),strings(o.getJSONArray("sources")),(0 until f.length()).map{j->val x=f.getJSONObject(j);FieldSpec(x.getString("name"),x.optString("label"),x.optString("description"),x.optString("type"),x.optString("group"),x.optBoolean("required"),if(x.has("default")&&!x.isNull("default"))x.get("default").toString() else null,if(x.has("draftKey"))x.getString("draftKey") else null,if(x.has("minimum"))x.getDouble("minimum") else null,x.optString("source"))})}}
 private fun strings(a:JSONArray)=(0 until a.length()).map{a.getString(it)}
}
object ProjectStore {
 private const val PREF="theobuilder-local";private const val KEY="projects"
 fun load(c:Context):List<PluginProject> = runCatching{val a=JSONArray(c.getSharedPreferences(PREF,0).getString(KEY,"[]"));(0 until a.length()).map{i->val o=a.getJSONObject(i);val v=o.optJSONObject("values")?:JSONObject();val m=buildMap{val it=v.keys();while(it.hasNext()){val k=it.next();put(k,v.optString(k,""))}};PluginProject(o.optString("id",UUID.randomUUID().toString()),o.optString("typeId","park"),m,o.optString("textureFile").takeIf{it.isNotBlank()},o.optString("textureName").takeIf{it.isNotBlank()},o.optLong("updated"))}}.getOrDefault(emptyList())
 fun save(c:Context,ps:List<PluginProject>){val a=JSONArray();ps.forEach{p->val v=JSONObject();p.values.forEach{(k,x)->v.put(k,x)};a.put(JSONObject().put("id",p.id).put("typeId",p.typeId).put("values",v).put("textureFile",p.textureFile?:JSONObject.NULL).put("textureName",p.textureName?:JSONObject.NULL).put("updated",p.updated))};c.getSharedPreferences(PREF,0).edit().putString(KEY,a.toString()).apply()}
}
object ProjectFactory {
 fun create(s:ObjectSpec):PluginProject{val m=s.fields.associate{f->f.name to when(f.name){"id"->'$' + "theobuilder_${s.id}_${UUID.randomUUID().toString().take(8)}";"title"->"${s.label} draft";else->f.default?:""}};return PluginProject(typeId=s.id,values=m)}
 fun duplicate(p:PluginProject,s:ObjectSpec):PluginProject{val m=p.values.toMutableMap();m["id"]='$' + "theobuilder_${s.id}_${UUID.randomUUID().toString().take(8)}";m["title"]=(m["title"]?:s.label)+" copy";return p.copy(id=UUID.randomUUID().toString(),values=m,updated=System.currentTimeMillis())}
}
