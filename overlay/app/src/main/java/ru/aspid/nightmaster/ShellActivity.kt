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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import ru.aspid.nightmaster.data.database.NightMasterDao
import ru.aspid.nightmaster.data.preferences.SettingsRepository

class ShellActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NightMasterApplication

        setContent {
            val darkTheme by app.settingsRepository.darkThemeEnabled
                .collectAsStateWithLifecycle(initialValue = true)

            MaterialTheme(colors = if (darkTheme) darkColors() else lightColors()) {
                NightMasterShell(
                    dao = app.database.dao(),
                    settingsRepository = app.settingsRepository,
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
    settingsRepository: SettingsRepository,
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
                        Text(
                            text = currentDestination.title,
                            style = MaterialTheme.typography.caption,
                        )
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
                HomeScreen(dao = dao, onOpenLegacyChat = onOpenLegacyChat)
            }
            composable(ShellDestination.Chats.route) {
                ChatsScreen(dao = dao)
            }
            composable(ShellDestination.Models.route) {
                ModelsScreen(dao = dao)
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
    onOpenLegacyChat: () -> Unit,
) {
    val chatCount by dao.observeChatCount().collectAsStateWithLifecycle(initialValue = 0)
    val modelCount by dao.observeModelCount().collectAsStateWithLifecycle(initialValue = 0)
    val benchmarkCount by dao.observeBenchmarkCount().collectAsStateWithLifecycle(initialValue = 0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Фундамент нового приложения",
                style = MaterialTheme.typography.h5,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                text = "Compose-оболочка уже отделена от проверенного inference-экрана. " +
                    "Пока новый чат не закончен, рабочая версия v0.5 остаётся доступна без изменений.",
                style = MaterialTheme.typography.body1,
            )
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
                onClick = onOpenLegacyChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть рабочий чат v0.5")
            }
        }
        item {
            FoundationCard()
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
private fun FoundationCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Уже подключено", fontWeight = FontWeight.Bold)
            Text("• Compose и навигационная оболочка")
            Text("• Room: чаты, сообщения, модели и benchmark")
            Text("• DataStore для пользовательских настроек")
            Text("• InferenceController над рабочим llama.cpp-движком")
            Text("• Legacy-чат сохранён как страховочная рабочая точка")
        }
    }
}

@Composable
private fun ChatsScreen(dao: NightMasterDao) {
    val chats by dao.observeChats().collectAsStateWithLifecycle(initialValue = emptyList())
    PlaceholderListScreen(
        title = "Сохранённые чаты",
        emptyText = "Чатов пока нет. На следующем этапе новый чат начнёт сохранять историю сюда.",
        rows = chats.map { it.title },
    )
}

@Composable
private fun ModelsScreen(dao: NightMasterDao) {
    val models by dao.observeModels().collectAsStateWithLifecycle(initialValue = emptyList())
    PlaceholderListScreen(
        title = "Локальные модели",
        emptyText = "Каталог моделей пока пуст. Следующий этап добавит импорт без обязательного копирования GGUF.",
        rows = models.map { model ->
            buildString {
                append(model.displayName)
                model.quantization?.let { append(" · ").append(it) }
            }
        },
    )
}

@Composable
private fun PlaceholderListScreen(
    title: String,
    emptyText: String,
    rows: List<String>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }
        if (rows.isEmpty()) {
            item { Text(emptyText) }
        } else {
            items(rows) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(row, modifier = Modifier.padding(16.dp))
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
                title = "Автозагрузка выбранной модели",
                description = "Пока выключено по умолчанию, чтобы запуск приложения не занимал память без запроса.",
                checked = autoLoad,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setAutoLoadSelectedModel(enabled) }
                },
            ),
            SettingRowState(
                title = "Тёмная тема",
                description = "Переключение уже сохраняется через DataStore.",
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
