package com.theobuilder.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TheoApp() }
    }
}

private enum class Nav(val label: String) {
    Home("Home"),
    Projects("Projects"),
    Library("Library"),
    Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TheoApp() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val schemas = remember { SchemaStore.load(c) }
    var projects by remember { mutableStateOf(ProjectStore.load(c)) }
    var nav by remember { mutableStateOf(Nav.Home) }
    var editor by remember { mutableStateOf<String?>(null) }
    var create by remember { mutableStateOf(false) }
    var ren by remember { mutableStateOf<PluginProject?>(null) }
    var renText by remember { mutableStateOf("") }
    var del by remember { mutableStateOf<PluginProject?>(null) }
    var importId by remember { mutableStateOf<String?>(null) }
    var exportId by remember { mutableStateOf<String?>(null) }
    var dark by remember {
        mutableStateOf(
            c.getSharedPreferences("theobuilder-local", 0).getBoolean("dark", false)
        )
    }
    val snackbar = remember { SnackbarHostState() }

    fun save(ps: List<PluginProject>) {
        projects = ps
        ProjectStore.save(c, ps)
    }

    fun update(p: PluginProject) {
        save(projects.map { if (it.id == p.id) p else it })
    }

    fun make(s: ObjectSpec) {
        val p = ProjectFactory.create(s)
        save(listOf(p) + projects)
        editor = p.id
        create = false
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = importId
        if (uri != null && id != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(c.filesDir, "textures").apply { mkdirs() }
                        val f = File(dir, "${UUID.randomUUID()}.png")
                        c.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        } ?: error("Не удалось прочитать PNG")
                        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.absolutePath, opt)
                        if (opt.outWidth < 1 || opt.outHeight < 1) {
                            f.delete()
                            error("Невозможно прочитать PNG")
                        }
                        Triple(f.name, uri.lastPathSegment?.substringAfterLast('/') ?: "texture.png", opt.outWidth to opt.outHeight)
                    }
                }
                val p = projects.firstOrNull { it.id == id }
                if (result.isSuccess && p != null) {
                    val (a, b, d) = result.getOrThrow()
                    update(p.copy(textureFile = a, textureName = b, updated = System.currentTimeMillis()))
                    snackbar.showSnackbar("PNG сохранён без обработки: ${d.first}×${d.second}")
                } else {
                    snackbar.showSnackbar(result.exceptionOrNull()?.message ?: "Проект не найден")
                }
            }
        }
    }

    val zip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val p = projects.firstOrNull { it.id == exportId }
        val s = schemas.firstOrNull { it.id == p?.typeId }
        if (uri != null && p != null && s != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) {
                    runCatching { PluginExporter.write(c, p, s, uri) }
                }
                snackbar.showSnackbar(if (r.isSuccess) "ZIP сохранён" else "Экспорт не выполнен: ${r.exceptionOrNull()?.message}")
            }
        }
    }

    TheoTheme(dark) {
        val p = projects.firstOrNull { it.id == editor }
        val s = schemas.firstOrNull { it.id == p?.typeId }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(if (p != null) p.values["title"].orEmpty().ifBlank { "Редактор" } else "TheoBuilder") },
                    navigationIcon = {
                        if (p != null) {
                            IconButton(onClick = { editor = null }) {
                                Icon(Icons.Default.ArrowBack, "Назад")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (p == null) {
                    NavigationBar {
                        Nav.entries.forEach { n ->
                            val icon = when (n) {
                                Nav.Home -> Icons.Default.Home
                                Nav.Projects -> Icons.Default.Folder
                                Nav.Library -> Icons.Default.Collections
                                Nav.Settings -> Icons.Default.Settings
                            }
                            NavigationBarItem(
                                selected = nav == n,
                                onClick = { nav = n },
                                icon = { Icon(icon, null) },
                                label = { Text(n.label) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            if (p != null && s != null) {
                Editor(
                    p, s,
                    { update(it) },
                    { importId = p.id; pick.launch(arrayOf("image/png")) },
                    { exportId = p.id; zip.launch("theobuilder-plugin.zip") },
                    Modifier.padding(pad)
                )
            } else {
                when (nav) {
                    Nav.Home -> Home(projects, schemas, { create = true }, { editor = it.id; nav = Nav.Projects }, ::make, Modifier.padding(pad))
                    Nav.Projects -> ProjectList(
                        projects, schemas,
                        { create = true },
                        { editor = it.id },
                        { x -> val spec = schemas.firstOrNull { it.id == x.typeId }; if (spec != null) save(listOf(ProjectFactory.duplicate(x, spec)) + projects) },
                        { renText = it.values["title"].orEmpty(); ren = it },
                        { del = it },
                        { exportId = it.id; zip.launch("plugin.zip") },
                        Modifier.padding(pad)
                    )
                    Nav.Library -> Library(Modifier.padding(pad))
                    Nav.Settings -> Settings(dark, { dark = it; c.getSharedPreferences("theobuilder-local", 0).edit().putBoolean("dark", it).apply() }, Modifier.padding(pad))
                }
            }
        }

        if (create) {
            AlertDialog(
                onDismissRequest = { create = false },
                title = { Text("Выберите draft type") },
                text = {
                    Column {
                        Text("Частичный набор из документированных примеров.")
                        schemas.forEach {
                            TextButton(onClick = { make(it) }) { Text(it.label) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { create = false }) { Text("Закрыть") } }
            )
        }

        ren?.let { x ->
            AlertDialog(
                onDismissRequest = { ren = null },
                title = { Text("Переименовать") },
                text = { OutlinedTextField(renText, { renText = it }, label = { Text("Название") }) },
                confirmButton = { TextButton(onClick = { update(x.copy(values = x.values + ("title" to renText), updated = System.currentTimeMillis())); ren = null }) { Text("Сохранить") } },
                dismissButton = { TextButton(onClick = { ren = null }) { Text("Отмена") } }
            )
        }

        del?.let { x ->
            AlertDialog(
                onDismissRequest = { del = null },
                title = { Text("Удалить проект?") },
                text = { Text("Импортированный PNG останется в библиотеке.") },
                confirmButton = { TextButton(onClick = { save(projects.filterNot { it.id == x.id }); del = null }) { Text("Удалить") } },
                dismissButton = { TextButton(onClick = { del = null }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun Home(
    ps: List<PluginProject>,
    ss: List<ObjectSpec>,
    create: () -> Unit,
    open: (PluginProject) -> Unit,
    quick: (ObjectSpec) -> Unit,
    mod: Modifier
) {
    LazyColumn(mod.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Создавайте плагины визуально", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Text("Схемы основаны на примерах и не покрывают весь API.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = create, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text(" Создать плагин")
            }
        }
        item {
            Text("Быстрый старт", style = MaterialTheme.typography.titleMedium)
        }
        items(ss) {
            OutlinedButton(onClick = { quick(it) }, modifier = Modifier.fillMaxWidth()) {
                Text(it.label)
            }
        }
        item {
            Text("Недавние · ${ps.size} проектов", style = MaterialTheme.typography.titleMedium)
        }
        items(ps.take(3)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { open(it) }
                    .padding(12.dp)
            ) {
                Text(it.values["title"].orEmpty())
            }
        }
        if (ps.isEmpty()) {
            item { Text("Проектов пока нет") }
        }
    }
}

@Composable
private fun ProjectList(
    ps: List<PluginProject>,
    ss: List<ObjectSpec>,
    create: () -> Unit,
    open: (PluginProject) -> Unit,
    duplicate: (PluginProject) -> Unit,
    rename: (PluginProject) -> Unit,
    delete: (PluginProject) -> Unit,
    export: (PluginProject) -> Unit,
    mod: Modifier
) {
    Column(mod.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${ps.size} проектов")
            Button(onClick = create) { Text("Создать") }
        }
        LazyColumn {
            items(ps, key = { it.id }) { p ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).clickable { open(p) }.padding(8.dp)) {
                        Text(p.values["title"].orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(ss.firstOrNull { it.id == p.typeId }?.label ?: p.typeId, style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Действия") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Открыть") }, onClick = { menu = false; open(p) })
                            DropdownMenuItem(text = { Text("Дублировать") }, onClick = { menu = false; duplicate(p) })
                            DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; rename(p) })
                            DropdownMenuItem(text = { Text("Экспорт ZIP") }, onClick = { menu = false; export(p) })
                            DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; delete(p) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(
    p: PluginProject,
    s: ObjectSpec,
    change: (PluginProject) -> Unit,
    importPng: () -> Unit,
    export: () -> Unit,
    mod: Modifier
) {
    val c = LocalContext.current
    var page by remember(p.id) { mutableIntStateOf(0) }
    val issues = remember(p, s) { Validator.check(c, p, s) }
    Column(mod.fillMaxSize()) {
        TabRow(page) {
            listOf("Свойства", "Текстура", "Экспорт").forEachIndexed { i, t ->
                Tab(page == i, { page = i }, Modifier, { Text(t) })
            }
        }
        when (page) {
            0 -> LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(s.label, style = MaterialTheme.typography.titleLarge)
                    Text(s.compatibility, style = MaterialTheme.typography.bodySmall)
                }
                s.fields.groupBy { it.group }.forEach { (g, fs) ->
                    item {
                        Text(g, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    items(fs, key = { g + it.name }) { Field(it, p, change, importPng) }
                }
            }
            1 -> Texture(p, importPng, Modifier.padding(16.dp))
            else -> LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Проверка и ZIP", style = MaterialTheme.typography.titleLarge) }
                items(issues, key = { it.text }) {
                    Text(
                        (if (it.error) "Ошибка: " else "Предупреждение: ") + it.text,
                        color = if (it.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
                item {
                    Button(onClick = export, enabled = issues.none { it.error }, modifier = Modifier.fillMaxWidth()) {
                        Text("Сохранить ZIP")
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(f: FieldSpec, p: PluginProject, change: (PluginProject) -> Unit, importPng: () -> Unit) {
    val value = p.values[f.name].orEmpty()
    fun set(v: String) = change(p.copy(values = p.values + (f.name to v), updated = System.currentTimeMillis()))
    when (f.type) {
        "texture" -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(f.label)
                Text(p.textureName ?: "PNG не выбран", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = importPng) { Text("Импорт PNG") }
        }
        "boolean" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(f.label, Modifier.weight(1f))
            Switch(value.toBoolean(), { set(it.toString()) })
        }
        else -> OutlinedTextField(
            value = value,
            onValueChange = { set(it) },
            label = { Text(f.label) },
            supportingText = { Text(f.description) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = if (f.type == "number") KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default
        )
    }
}

@Composable
private fun Texture(p: PluginProject, importPng: () -> Unit, mod: Modifier) {
    val c = LocalContext.current
    val image = remember(p.textureFile) {
        p.textureFile?.let {
            BitmapFactory.decodeFile(File(c.filesDir, "textures/$it").absolutePath)?.asImageBitmap()
        }
    }
    Column(mod.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PNG без изменения оригинала")
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                Image(image, p.textureName, Modifier.fillMaxWidth().padding(12.dp), filterQuality = FilterQuality.None)
            } else {
                Text("Предпросмотр после импорта")
            }
        }
        Button(onClick = importPng) { Text("Выбрать PNG") }
    }
}

@Composable
private fun Library(mod: Modifier) {
    val c = LocalContext.current
    val files = remember { File(c.filesDir, "textures").listFiles()?.filter { it.isFile }.orEmpty() }
    Column(mod.padding(16.dp)) {
        Text("Библиотека · ${files.size} PNG", style = MaterialTheme.typography.titleLarge)
        LazyColumn {
            items(files) { Text(it.name, Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun Settings(dark: Boolean, set: (Boolean) -> Unit, mod: Modifier) {
    Column(mod.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Тёмная тема", Modifier.weight(1f))
            Switch(dark, set)
        }
        Text("Схема 0.1.0, неполное покрытие; архив не проверен в TheoTown.")
    }
}
