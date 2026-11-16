@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
package com.krashkanter.kela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialExpressiveTheme(colorScheme = colors) { KelaApp() }
        }
    }
}

@Composable
fun ActionIcon(label: String, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = action, enabled = enabled) { Icon(icon, contentDescription = label) }
    }
}

private data class Section(val title: String, val icon: ImageVector)
private val sections = listOf(Section("Containers", Icons.Rounded.ViewInAr), Section("Images", Icons.Rounded.Layers), Section("Storage", Icons.Rounded.Storage), Section("Networks", Icons.Rounded.Hub))

@Composable
fun KelaApp(model: KelaModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var section by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var createImage by remember { mutableStateOf("") }
    var pull by rememberSaveable { mutableStateOf(false) }
    var newVolume by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var delete by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.refresh(true)
            while (true) { delay(5000); model.refresh() }
        }
    }
    LaunchedEffect(state.error, state.notice) {
        (state.error ?: state.notice)?.let { snackbar.showSnackbar(it); model.clearMessage() }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Kela", fontWeight = FontWeight.SemiBold) }, actions = {
            ActionIcon("Refresh", Icons.Rounded.Refresh, !state.refreshing) { model.refresh(true) }
            ActionIcon("Connection", Icons.Rounded.Tune) { settings = true }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                sections.forEachIndexed { index, item ->
                    NavigationBarItem(selected = section == index, onClick = { section = index; query = "" },
                        icon = { Icon(item.icon, item.title) }, label = { Text(item.title) }, alwaysShowLabel = false)
                }
            }
        },
        floatingActionButton = {
            if (section < 3) FloatingActionButton(onClick = {
                when (section) { 0 -> { createImage = ""; create = true }; 1 -> pull = true; 2 -> newVolume = true }
            }) { Icon(if (section == 1) Icons.Rounded.Download else Icons.Rounded.Add, if (section == 1) "Pull image" else if (section == 2) "Create volume" else "Create container") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HostSummary(state)
            if (state.pull != null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Text(state.pull.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text(sections[section].title) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
                shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            AnimatedContent(targetState = section, label = "section", modifier = Modifier.weight(1f)) { tab ->
                when (tab) {
                    0 -> {
                        val filtered = state.containers.filter { containerName(it).contains(query, true) || it.optString("Image").contains(query, true) }
                        if (filtered.isEmpty()) EmptyState(Icons.Rounded.ViewInAr, if (query.isBlank()) "No containers" else "No matches")
                        else LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(filtered, key = { it.getString("Id") }) { item ->
                                ContainerRow(item, item.getString("Id") in state.busy, open = { selected = item },
                                    action = { verb -> model.action(item.getString("Id"), "POST", "containers/${item.getString("Id")}/$verb") }, remove = { delete = "container" to item })
                            }
                        }
                    }
                    1 -> {
                        val filtered = state.images.filter { imageName(it).contains(query, true) }
                        if (filtered.isEmpty()) EmptyState(Icons.Rounded.Layers, "No images")
                        else LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(filtered, key = { it.getString("Id") }) { item ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(imageName(item), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(bytes(item.optLong("Size")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        ActionIcon("Create container", Icons.Rounded.AddBox) { createImage = imageName(item); create = true }
                                        ActionIcon("Remove image", Icons.Rounded.DeleteOutline, item.getString("Id") !in state.busy) { delete = "image" to item }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        val filtered = state.volumes.filter { it.optString("Name").contains(query, true) }
                        if (filtered.isEmpty()) EmptyState(Icons.Rounded.Storage, "No volumes")
                        else LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(filtered, key = { it.getString("Name") }) { item ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.optString("Name"), style = MaterialTheme.typography.titleSmall)
                                            Text(item.optString("Driver"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        ActionIcon("Remove volume", Icons.Rounded.DeleteOutline, item.optString("Name") !in state.busy) { delete = "volume" to item }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(state.networks.filter { it.optString("Name").contains(query, true) }, key = { it.getString("Id") }) { item ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(item.optString("Name"), style = MaterialTheme.typography.titleSmall)
                                        val subnet = item.obj("IPAM").array("Config").objects().joinToString { it.optString("Subnet") }
                                        Text(listOf(item.optString("Driver"), subnet).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (create) CreateContainer(model, state, createImage, close = { create = false })
    if (pull) InputDialog("Pull image", "Image", Icons.Rounded.Download, busy = state.pull != null, close = { pull = false }) { image -> model.pull(image) { pull = false } }
    if (newVolume) InputDialog("Create volume", "Name", Icons.Rounded.Check, close = { newVolume = false }) { name ->
        model.action("volume", "POST", "volumes", JSONObject().put("name", name)) { newVolume = false }
    }
    if (settings) ConnectionDialog(model, state.connected) { settings = false }
    selected?.let { item -> ContainerDetail(model, item, state, close = { selected = null }) }
    delete?.let { (kind, item) ->
        val name = when (kind) { "container" -> containerName(item); "image" -> imageName(item); else -> item.optString("Name") }
        AlertDialog(onDismissRequest = { delete = null }, icon = { Icon(Icons.Rounded.DeleteOutline, null) }, title = { Text("Remove $name?") },
            text = { if (kind == "volume") Text("Volume data will be deleted.") else if (kind == "container") Text("Container data will be deleted. Volumes are retained.") },
            confirmButton = { IconButton(onClick = {
                val key = if (kind == "volume") item.getString("Name") else item.getString("Id")
                val resource = when (kind) { "container" -> "containers/$key?force=true"; "image" -> "images/$key"; else -> "volumes/$key" }
                model.action(key, "DELETE", resource); delete = null
            }) { Icon(Icons.Rounded.Delete, "Confirm removal", tint = MaterialTheme.colorScheme.error) } },
            dismissButton = { ActionIcon("Cancel", Icons.Rounded.Close) { delete = null } })
    }
}

@Composable
private fun HostSummary(state: Dashboard) {
    val info = state.overview
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(if (state.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape))
                Text(if (state.connected) "Local engine" else "Offline", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(info?.optString("version")?.let { "Docker $it" }.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(info?.optInt("running")?.toString() ?: "—", "Running")
                Metric(info?.optInt("containers")?.toString() ?: "—", "Containers")
                Metric(info?.optInt("images")?.toString() ?: "—", "Images")
            }
            if (info != null) {
                val used = info.optLong("diskUsed"); val total = info.optLong("diskTotal").coerceAtLeast(1)
                LinearProgressIndicator(progress = { (used.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${bytes(used)} / ${bytes(total)} · ${info.optString("storageDriver")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium); Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp)); Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ContainerRow(item: JSONObject, busy: Boolean, open: () -> Unit, action: (String) -> Unit, remove: () -> Unit) {
    val running = item.optString("State") == "running"
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.clickable(onClick = open).padding(start = 16.dp, top = 14.dp, end = 4.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(containerName(item), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.optString("Image"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (busy) LoadingIndicator(Modifier.size(40.dp))
                else FilledTonalIconButton(onClick = { action(if (running) "stop" else "start") }) {
                    Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (running) "Stop ${containerName(item)}" else "Start ${containerName(item)}")
                }
                Box {
                    ActionIcon("More actions", Icons.Rounded.MoreVert, !busy) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Restart") }, leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) }, onClick = { menu = false; action("restart") })
                        DropdownMenuItem(text = { Text("Inspect") }, leadingIcon = { Icon(Icons.Rounded.DataObject, null) }, onClick = { menu = false; open() })
                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; remove() })
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(6.dp).background(if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                Text(item.optString("Status"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ports = item.array("Ports").objects().filter { it.has("PublicPort") }.joinToString { "${it.optInt("PublicPort")} → ${it.optInt("PrivatePort")}" }
                if (ports.isNotEmpty()) { Spacer(Modifier.weight(1f)); Text(ports, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun InputDialog(title: String, label: String, icon: ImageVector, busy: Boolean = false, close: () -> Unit, submit: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true)
    }, confirmButton = { ActionIcon(title, icon, value.isNotBlank() && !busy) { submit(value.trim()) } }, dismissButton = { ActionIcon("Cancel", Icons.Rounded.Close, action = close) })
}

@Composable
private fun ConnectionDialog(model: KelaModel, connected: Boolean, close: () -> Unit) {
    var token by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Connection") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("127.0.0.1:9473", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Text(if (connected) "Paired" else if (model.bridge.configured()) "Bridge unavailable" else "Not paired", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(token, { token = it }, label = { Text("Pairing token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        }
    }, confirmButton = { ActionIcon("Save token", Icons.Rounded.Check, token.length >= 32) { model.bridge.saveToken(token); model.refresh(true); close() } }, dismissButton = { ActionIcon("Close", Icons.Rounded.Close, action = close) })
}

@Composable
private fun CreateContainer(model: KelaModel, state: Dashboard, initialImage: String, close: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf(initialImage) }
    var ports by rememberSaveable { mutableStateOf("") }
    var environment by rememberSaveable { mutableStateOf("") }
    var mounts by rememberSaveable { mutableStateOf("") }
    var command by rememberSaveable { mutableStateOf("") }
    var network by rememberSaveable { mutableStateOf("phone-edge") }
    var restart by rememberSaveable { mutableStateOf("unless-stopped") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(true) }
    var imageMenu by remember { mutableStateOf(false) }
    var networkMenu by remember { mutableStateOf(false) }
    var restartMenu by remember { mutableStateOf(false) }
    fun lines(value: String) = JSONArray(value.lines().map { it.trim() }.filter { it.isNotEmpty() })
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text("Create container") }, navigationIcon = { ActionIcon("Close", Icons.Rounded.Close, action = close) }, actions = {
            if ("create" in state.busy) LoadingIndicator(Modifier.size(40.dp))
            else ActionIcon("Create", Icons.Rounded.Check, name.isNotBlank() && image.isNotBlank()) {
                model.create(JSONObject().put("name", name).put("image", image).put("ports", lines(ports)).put("environment", lines(environment))
                    .put("binds", lines(mounts)).put("command", command).put("network", network).put("restart", restart).put("start", start), close)
            }
        }) }) { padding ->
            Column(Modifier.padding(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Field(name, { name = it }, "Name", single = true)
                Box {
                    OutlinedTextField(image, { image = it }, label = { Text("Image") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { ActionIcon("Local images", Icons.Rounded.ExpandMore) { imageMenu = true } })
                    DropdownMenu(imageMenu, { imageMenu = false }) {
                        state.images.forEach { item -> DropdownMenuItem(text = { Text(imageName(item)) }, onClick = { image = imageName(item); imageMenu = false }) }
                    }
                }
                Field(ports, { ports = it }, "Ports", placeholder = "8081:80")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Start", Modifier.weight(1f)); Switch(start, { start = it }) }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().clickable { advanced = !advanced }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Configuration", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (advanced) "Collapse" else "Expand") }
                }
                if (advanced) {
                    Field(environment, { environment = it }, "Environment", placeholder = "KEY=value")
                    Field(mounts, { mounts = it }, "Mounts", placeholder = "volume:/data")
                    Field(command, { command = it }, "Command")
                    Box {
                        OutlinedTextField(network, {}, readOnly = true, label = { Text("Network") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { ActionIcon("Choose network", Icons.Rounded.ExpandMore) { networkMenu = true } })
                        DropdownMenu(networkMenu, { networkMenu = false }) {
                            state.networks.filter { it.optString("Name") !in listOf("host", "none") }.forEach { item -> DropdownMenuItem(text = { Text(item.optString("Name")) }, onClick = { network = item.optString("Name"); networkMenu = false }) }
                        }
                    }
                    Box {
                        OutlinedTextField(restart, {}, readOnly = true, label = { Text("Restart policy") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { ActionIcon("Choose restart policy", Icons.Rounded.ExpandMore) { restartMenu = true } })
                        DropdownMenu(restartMenu, { restartMenu = false }) {
                            listOf("no", "unless-stopped", "always", "on-failure").forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { restart = value; restartMenu = false }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String, placeholder: String = "", single: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = single, minLines = if (single) 1 else 2, maxLines = if (single) 1 else 5, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
}

@Composable
private fun ContainerDetail(model: KelaModel, item: JSONObject, state: Dashboard, close: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var logs by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val id = item.getString("Id")
    LaunchedEffect(id, tab, generation, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try {
                    if (tab == 1) logs = JSONObject(model.bridge.request("GET", "containers/$id/logs")).optString("text")
                    else detail = JSONObject(model.bridge.request("GET", "containers/$id"))
                    error = null
                } catch (e: Exception) { error = e.message }
                delay(if (tab == 1) 3000 else 5000)
            }
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text(containerName(item), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { ActionIcon("Back", Icons.AutoMirrored.Rounded.ArrowBack, action = close) }, actions = {
            ActionIcon("Copy", Icons.Rounded.ContentCopy) { clipboard.setText(AnnotatedString(if (tab == 1) logs else detail?.toString(2).orEmpty())); model.message("Copied") }
            ActionIcon("Refresh", Icons.Rounded.Refresh) { generation++ }
        }) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                PrimaryTabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Inspect") }, icon = { Icon(Icons.Rounded.DataObject, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Logs") }, icon = { Icon(Icons.AutoMirrored.Rounded.Article, null) })
                }
                if (error != null) Text(error.orEmpty(), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
                if (tab == 0) {
                    detail?.let { data ->
                        val running = data.obj("State").optBoolean("Running")
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalIconButton(onClick = { model.action(id, "POST", "containers/$id/${if (running) "stop" else "start"}"); generation++ }, enabled = id !in state.busy) { Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (running) "Stop" else "Start") }
                            FilledTonalIconButton(onClick = { model.action(id, "POST", "containers/$id/restart"); generation++ }, enabled = id !in state.busy) { Icon(Icons.Rounded.RestartAlt, "Restart") }
                            Text(data.obj("State").optString("Status"), Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelLarge)
                        }
                        SelectionContainer {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                InspectValue("Image", data.obj("Config").optString("Image"))
                                InspectValue("ID", data.optString("Id"))
                                InspectValue("Network", data.obj("HostConfig").optString("NetworkMode"))
                                InspectValue("Restart", data.obj("HostConfig").obj("RestartPolicy").optString("Name"))
                                InspectValue("Ports", data.obj("NetworkSettings").obj("Ports").toString(2))
                                InspectValue("Mounts", data.array("Mounts").objects().joinToString("\n") { "${it.optString("Source")} → ${it.optString("Destination")}${if (!it.optBoolean("RW")) " (ro)" else ""}" })
                                InspectValue("Command", data.obj("Config").array("Cmd").strings().joinToString(" "))
                                InspectValue("Environment", data.obj("Config").array("Env").strings().joinToString("\n"))
                                val failure = data.obj("State").optString("Error")
                                if (failure.isNotBlank()) InspectValue("Error", failure)
                            }
                        }
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else {
                    SelectionContainer { Text(logs.ifEmpty { "No logs" }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun InspectValue(label: String, value: String) {
    if (value.isNotBlank() && value != "{}") Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}