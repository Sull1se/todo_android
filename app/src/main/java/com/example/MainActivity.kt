package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppDatabase
import com.example.data.TodoRepository
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.example.ui.DetailExitAction
import com.example.ui.HomeScreen
import com.example.ui.TaskDetailScreen
import com.example.ui.TodoViewModel
import com.example.ui.TodoViewModelFactory
import com.example.ui.theme.MyApplicationTheme

import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(this)
        val repository = TodoRepository(database.todoDao(), database)
        val sharedPrefs = getSharedPreferences("todo_prefs", Context.MODE_PRIVATE)
        val factory = TodoViewModelFactory(repository, sharedPrefs)

        setContent {
            TodoApp(factory)
        }
    }
}

const val DetailEnterTransitionMillis = 120
const val DetailExitTransitionMillis = 120

@Composable
fun TodoApp(
    factory: TodoViewModelFactory,
    navController: NavHostController = rememberNavController()
) {
    val viewModel: TodoViewModel = viewModel(factory = factory)
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    DisposableEffect(isDarkTheme) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (isDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
        )
        onDispose { }
    }

    MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(navController = navController, startDestination = "home") {
                composable(
                    route = "home",
                    exitTransition = { fadeOut(tween(durationMillis = DetailEnterTransitionMillis)) },
                    popEnterTransition = { fadeIn(tween(durationMillis = DetailExitTransitionMillis)) },
                    popExitTransition = null
                ) { homeEntry ->
                    HomeScreen(
                        viewModel = viewModel,
                        onTaskClick = { taskId ->
                            if (navController.currentBackStackEntry == homeEntry &&
                                homeEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                            ) {
                                navController.navigate("detail/$taskId")
                            }
                        }
                    )
                }
                composable(
                    route = "detail/{taskId}",
                    arguments = listOf(navArgument("taskId") { type = NavType.IntType }),
                    enterTransition = { fadeIn(tween(durationMillis = DetailEnterTransitionMillis)) },
                    popEnterTransition = null,
                    popExitTransition = { fadeOut(tween(durationMillis = DetailExitTransitionMillis)) }
                ) { backStackEntry ->
                    val taskIdArg = backStackEntry.arguments?.getInt("taskId") ?: -1
                    val taskId = if (taskIdArg == -1) null else taskIdArg

                    TaskDetailScreen(
                        taskId = taskId,
                        viewModel = viewModel,
                        backStackEntry = backStackEntry,
                        onExitRequest = { action ->
                            performDetailExit(
                                navController = navController,
                                callerEntry = backStackEntry,
                                viewModel = viewModel,
                                action = action
                            )
                        }
                    )
                }
            }
        }
    }
}

fun performDetailExit(
    navController: NavController,
    callerEntry: NavBackStackEntry,
    viewModel: TodoViewModel,
    action: DetailExitAction
) {
    if (navController.currentBackStackEntry != callerEntry) return
    if (!callerEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

    when (action) {
        is DetailExitAction.Save -> {
            viewModel.updateTaskDetail(action.taskId, action.title, action.content, action.isFlagged)
        }
        is DetailExitAction.Delete -> {
            viewModel.deleteTask(action.taskId)
        }
        DetailExitAction.Cancel -> {}
    }

    val popped = navController.popBackStack("home", inclusive = false)
    if (!popped) {
        Log.w("TodoApp", "popBackStack to home failed for entry ${callerEntry.id}")
    }
}
