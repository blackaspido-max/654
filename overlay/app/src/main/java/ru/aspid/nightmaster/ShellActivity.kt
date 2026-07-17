package ru.aspid.nightmaster

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.aspid.nightmaster.data.database.NightMasterDao
import ru.aspid.nightmaster.data.models.ModelCatalogRepository
import ru.aspid.nightmaster.data.preferences.SettingsRepository
import ru.aspid.nightmaster.feature.models.ModelManagerScreen

class ShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NightMasterApplication

        lifecycleScope.launch(Dispatchers.IO) {
            app.modelCatalogRepository.migrateLegacyModels()
        }

        setContent {
            val darkTheme by app.settingsRepository.darkThemeEnabled
                .collectAsStateWithLifecycle(initialValue = true)

            MaterialTheme(colors = if (darkTheme) darkColors() else lightColors()) {
                NightMasterShell(
                    dao = app.database.dao(),
                    modelCatalogRepository = app.modelCatalogRepository,
                    settingsRepository = app.settingsRepository,
                    onOpenChat = {
                        startActivity(Intent(this, ModelChatActivity::class.java))
                    },
                    onOpenLegacyChat = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                )
            }
        }
    }
}

private enum class ShellDestination(
    val route: String,
    val title: String,
    val compactLabel: String,
) {
    Home("home", "Главная", "Г"),
    Chats("chats", "Чаты", "Ч"),
    Models("models", "Модели", "М"),
    Settings("settings", "Настройки", "Н"),
}

@Composable
private fun NightMasterShell(
    dao: NightMasterDao,
    modelCatalogRepository: ModelCatalogRepository,
    settingsRepository: SettingsRepository,
    onOpenChat: () -> Unit,
    onOpenLegacyChat: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ShellDestination.Home.route
    val currentDestination = ShellDestination.entries.firstOrNull { it.route == currentRoute }
        ?: ShellDestination.Home

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ночной мастер")
                        Text(currentDestination.title, style = MaterialTheme.typography.caption)
                    }
                },
            )
        },
        bottomBar = {
            BottomNavigation {
                ShellDestination.entries.forEach { destination ->
                    BottomNavigationItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(destination.compactLabel) },
                        label = { Text(destination.title) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ShellDestination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ShellDestination.Home.route) {
                HomeScreen(
                    dao = dao,
                    modelCatalogRepository = modelCatalogRepository,
                    onOpenChat = onOpenChat,
                    onOpenLegacyChat = onOpenLegacyChat,
                    onOpenModels = { navController.navigate(ShellDestination.Models.route) },
                )
            }
            composable(ShellDestination.Chats.route) {
                ChatsScreen(dao = dao)
            }
            composable(ShellDestination.Models.route) {
                ModelManagerScreen(
                    repository = modelCatalogRepository,
                    onOpenChat = onOpenChat,
                )
            }
            composable(ShellDestination.Settings.route) {
                SettingsScreen(settingsRepository = settingsRepository)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    dao: NightMasterDao,
    modelCatalogRepository: ModelCatalogRepository,
    onOpenChat: () -> Unit,
    onOpenLegacyChat: () -> Unit,
    onOpenModels: () -> Unit,
) {
    val chatCount by dao.observeChatCount().collectAsStateWithLifecycle(initialValue = 0)
    val modelCount by dao.observeModelCount().collectAsStateWithLifecycle(initialValue = 0)
    val benchmarkCount by dao.observeBenchmarkCount().collectAsStateWithLifecycle(initialValue = 0)
    val selectedModel by modelCatalogRepository.selectedModel
        .collectAsStateWithLifecycle(initialValue = null)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Локальный мастер готов",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Активная модель", fontWeight = FontWeight.Bold)
                    Text(selectedModel?.displayName ?: "Модель ещё не выбрана")
                    selectedModel?.let { model ->
                        val details = listOfNotNull(model.family, model.quantization)
                        if (details.isNotEmpty()) {
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.caption)
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard("Чаты", chatCount, Modifier.weight(1f))
                StatCard("Модели", modelCount, Modifier.weight(1f))
                StatCard("Тесты", benchmarkCount, Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = if (selectedModel == null) onOpenModels else onOpenChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selectedModel == null) "Добавить модель" else "Открыть чат 0.7")
            }
        }
        item {
            OutlinedButton(
                onClick = onOpenLegacyChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Резервный чат v0.5")
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("Model Manager 0.7", fontWeight = FontWeight.Bold)
                    Text("• GGUF подключается без обязательного копирования")
                    Text("• выбранная модель хранится в локальном каталоге")
                    Text("• старые внутренние модели подхватываются автоматически")
                    Text("• рабочий чат загружает модель только при открытии")
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value.toString(), style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun ChatsScreen(dao: NightMasterDao) {
    val chats by dao.observeChats().collectAsStateWithLifecycle(initialValue = emptyList())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Сохранённые чаты", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        if (chats.isEmpty()) {
            item { Text("Чатов пока нет. История появится после внедрения нового экрана чата.") }
        } else {
            items(chats, key = { it.id }) { chat ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(chat.title, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val autoLoad by settingsRepository.autoLoadSelectedModel
        .collectAsStateWithLifecycle(initialValue = false)
    val darkTheme by settingsRepository.darkThemeEnabled
        .collectAsStateWithLifecycle(initialValue = true)

    val settings = remember(autoLoad, darkTheme) {
        listOf(
            SettingRowState(
                title = "Автозагрузка при открытии чата",
                description = "Модель не занимает память на главном экране и загружается только при входе в чат.",
                checked = autoLoad,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setAutoLoadSelectedModel(enabled) }
                },
            ),
            SettingRowState(
                title = "Тёмная тема",
                description = "Переключение сохраняется локально через DataStore.",
                checked = darkTheme,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setDarkThemeEnabled(enabled) }
                },
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        settings.forEachIndexed { index, setting ->
            SettingRow(setting)
            if (index != settings.lastIndex) Divider()
        }
    }
}

private data class SettingRowState(
    val title: String,
    val description: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

@Composable
private fun SettingRow(setting: SettingRowState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(setting.title, fontWeight = FontWeight.Medium)
            Text(setting.description, style = MaterialTheme.typography.body2)
        }
        Switch(
            checked = setting.checked,
            onCheckedChange = setting.onCheckedChange,
        )
    }
}
