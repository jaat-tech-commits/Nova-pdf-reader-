package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.data.service.TtsService
import com.example.ui.navigation.NavRoutes
import com.example.ui.navigation.NovaBottomNavigationBar
import com.example.ui.navigation.NovaNavigationRail
import com.example.ui.screens.ai.AiChatScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.reader.ReaderScreen
import com.example.ui.screens.scanner.DocumentScannerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.study.StudyModeScreen
import com.example.ui.screens.tools.PdfToolsScreen
import com.example.ui.screens.vault.PrivateVaultScreen
import com.example.ui.theme.NovaPdfTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var ttsService: TtsService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ttsService = TtsService(applicationContext)

        val app = application as NovaPdfApplication
        val repository = app.repository

        // Handle incoming PDF intent if opened from file manager
        val intentPdfUri: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("nova_pdf_prefs", Context.MODE_PRIVATE) }
            var appThemeName by remember { mutableStateOf(prefs.getString("theme", "System") ?: "System") }
            var hasCompletedOnboarding by remember { mutableStateOf(prefs.getBoolean("onboarding_complete", false)) }
            var selectedAiModel by remember { mutableStateOf(prefs.getString("gemini_model", "gemini-3.8-flash") ?: "gemini-3.8-flash") }
            var apiKeyConfigured by remember {
                mutableStateOf(
                    prefs.getString("gemini_api_key", "").orEmpty().isNotBlank() ||
                        try { BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" } catch (_: Exception) { false }
                )
            }

            fun navigateToMainTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            val coroutineScope = rememberCoroutineScope()
            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Initialize sample documents once on first start
            LaunchedEffect(Unit) {
                repository.initializeDefaultDocumentsIfEmpty()
                if (intentPdfUri != null) {
                    val imported = repository.importDocument(intentPdfUri, "Opened_Document.pdf")
                    navController.navigate(NavRoutes.readerRoute(imported.id, 1))
                }
            }

            NovaPdfTheme(appThemeName = appThemeName) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isExpandedScreen = maxWidth >= 600.dp

                    // Determine if bottom bar or rail should be visible
                    val isMainTabRoute = currentRoute in listOf(
                        NavRoutes.HOME,
                        NavRoutes.LIBRARY,
                        NavRoutes.AI,
                        NavRoutes.TOOLS,
                        NavRoutes.SETTINGS
                    )

                    val startDestination = if (hasCompletedOnboarding) NavRoutes.HOME else NavRoutes.ONBOARDING

                    if (isExpandedScreen && isMainTabRoute) {
                        // Desktop/Tablet Navigation Rail layout
                        Row(modifier = Modifier.fillMaxSize()) {
                            NovaNavigationRail(
                                currentRoute = currentRoute,
                                onNavigate = { route -> navigateToMainTab(route) }
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                NovaNavHost(
                                    navController = navController,
                                    repository = repository,
                                    ttsService = ttsService,
                                    startDestination = startDestination,
                                    appThemeName = appThemeName,
                                    onThemeChange = { newTheme ->
                                        appThemeName = newTheme
                                        prefs.edit().putString("theme", newTheme).apply()
                                    },
                                    currentAiModel = selectedAiModel,
                                    onAiModelChange = { model ->
                                        selectedAiModel = model
                                        prefs.edit().putString("gemini_model", model).apply()
                                    },
                                    apiKeyConfigured = apiKeyConfigured,
                                    onApiKeyChange = { key ->
                                        prefs.edit().putString("gemini_api_key", key.trim()).apply()
                                        apiKeyConfigured = key.trim().isNotBlank()
                                    },
                                    currentAiModel = selectedAiModel,
                                    onAiModelChange = { model ->
                                        selectedAiModel = model
                                        prefs.edit().putString("gemini_model", model).apply()
                                    },
                                    apiKeyConfigured = apiKeyConfigured,
                                    onApiKeyChange = { key ->
                                        prefs.edit().putString("gemini_api_key", key.trim()).apply()
                                        apiKeyConfigured = key.trim().isNotBlank()
                                    },
                                    onFinishOnboarding = {
                                        hasCompletedOnboarding = true
                                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                                        navController.navigate(NavRoutes.HOME) {
                                            popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        // Mobile Bottom Navigation layout
                        Scaffold(
                            bottomBar = {
                                if (isMainTabRoute) {
                                    NovaBottomNavigationBar(
                                        currentRoute = currentRoute,
                                        onNavigate = { route -> navController.navigateToTab(route) }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                NovaNavHost(
                                    navController = navController,
                                    repository = repository,
                                    ttsService = ttsService,
                                    startDestination = startDestination,
                                    appThemeName = appThemeName,
                                    onThemeChange = { newTheme ->
                                        appThemeName = newTheme
                                        prefs.edit().putString("theme", newTheme).apply()
                                    },
                                    onFinishOnboarding = {
                                        hasCompletedOnboarding = true
                                        prefs.edit().putBoolean("onboarding_complete", true).apply()
                                        navController.navigate(NavRoutes.HOME) {
                                            popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsService.shutdown()
    }
}

@Composable
fun NovaNavHost(
    navController: androidx.navigation.NavHostController,
    repository: com.example.data.repository.DocumentRepository,
    ttsService: TtsService,
    startDestination: String,
    appThemeName: String,
    onThemeChange: (String) -> Unit,
    onFinishOnboarding: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(onFinish = onFinishOnboarding)
        }

        composable(NavRoutes.HOME) {
            HomeScreen(
                repository = repository,
                onOpenDocument = { docId, page ->
                    navController.navigate(NavRoutes.readerRoute(docId, page))
                },
                onNavigateToLibrary = { navController.navigate(NavRoutes.LIBRARY) },
                onNavigateToAi = { docId ->
                    navController.navigate(NavRoutes.aiRoute(docId))
                },
                onNavigateToScanner = { navController.navigate(NavRoutes.SCANNER) },
                onNavigateToTools = { navController.navigate(NavRoutes.TOOLS) },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToStudy = { docId ->
                    navController.navigate(NavRoutes.studyRoute(docId))
                }
            )
        }

        composable(NavRoutes.LIBRARY) {
            LibraryScreen(
                repository = repository,
                onOpenDocument = { docId, page ->
                    navController.navigate(NavRoutes.readerRoute(docId, page))
                },
                onNavigateToStudy = { docId ->
                    navController.navigate(NavRoutes.studyRoute(docId))
                },
                onNavigateToAi = { docId ->
                    navController.navigate(NavRoutes.aiRoute(docId))
                }
            )
        }

        composable(
            route = NavRoutes.AI,
            arguments = listOf(navArgument("docId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val aiDocId = backStackEntry.arguments?.getLong("docId")?.takeIf { it > 0L }
            AiChatScreen(
                initialDocId = aiDocId,
                initialPrompt = null,
                repository = repository,
                onBack = null,
                onOpenPageReference = { docId, page ->
                    navController.navigate(NavRoutes.readerRoute(docId, page))
                }
            )
        }

        composable(NavRoutes.TOOLS) {
            PdfToolsScreen(
                repository = repository,
                onBack = null,
                onOpenDocument = { docId, page ->
                    navController.navigate(NavRoutes.readerRoute(docId, page))
                }
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                currentTheme = appThemeName,
                onThemeChange = onThemeChange,
                onNavigateToVault = { navController.navigate(NavRoutes.VAULT) }
            )
        }

        composable(
            route = NavRoutes.READER,
            arguments = listOf(
                navArgument("docId") { type = NavType.LongType },
                navArgument("page") {
                    type = NavType.IntType
                    defaultValue = 1
                }
            )
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 1L
            val page = backStackEntry.arguments?.getInt("page") ?: 1
            ReaderScreen(
                docId = docId,
                initialPage = page,
                repository = repository,
                ttsService = ttsService,
                onBack = { navController.popBackStack() },
                onNavigateToAi = { targetDocId, prompt ->
                    navController.navigate(NavRoutes.aiRoute(targetDocId))
                },
                onNavigateToStudy = { targetDocId ->
                    navController.navigate(NavRoutes.studyRoute(targetDocId))
                }
            )
        }

        composable(
            route = NavRoutes.STUDY,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: 1L
            StudyModeScreen(
                docId = docId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenPageReference = { targetDocId, page ->
                    navController.navigate(NavRoutes.readerRoute(targetDocId, page))
                }
            )
        }

        composable(NavRoutes.SCANNER) {
            DocumentScannerScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenCreatedDocument = { docId ->
                    navController.navigate(NavRoutes.readerRoute(docId, 1))
                }
            )
        }

        composable(NavRoutes.VAULT) {
            PrivateVaultScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenDocument = { docId, page ->
                    navController.navigate(NavRoutes.readerRoute(docId, page))
                }
            )
        }
    }
}
