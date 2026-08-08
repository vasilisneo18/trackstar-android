package com.vasilisneo.trackstar

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vasilisneo.trackstar.ui.screens.landing.LandingScreen
import com.vasilisneo.trackstar.ui.screens.login.ForgotPasswordScreen
import com.vasilisneo.trackstar.ui.screens.login.LoginScreen
import com.vasilisneo.trackstar.ui.screens.register.BodyMetricsScreen
import com.vasilisneo.trackstar.ui.screens.register.CreatePasswordScreen
import com.vasilisneo.trackstar.ui.screens.register.EmailEntryScreen
import com.vasilisneo.trackstar.ui.screens.register.FitnessProfileScreen
import com.vasilisneo.trackstar.ui.screens.register.GoalsScreen
import com.vasilisneo.trackstar.ui.screens.register.PersonalDetailsScreen
import com.vasilisneo.trackstar.ui.screens.register.RegisterViewModel
import com.vasilisneo.trackstar.ui.screens.main.MainAppScreen
import com.vasilisneo.trackstar.ui.screens.main.plan.SessionEditScreen
import com.vasilisneo.trackstar.ui.screens.main.plan.WeeklyPlanScreen
import com.vasilisneo.trackstar.ui.screens.main.PersonalInfoScreen
import com.vasilisneo.trackstar.ui.screens.main.ProfileScreen
import com.vasilisneo.trackstar.ui.screens.main.QRConnectScreen
import com.vasilisneo.trackstar.ui.screens.main.coach.AddAthleteScreen
import com.vasilisneo.trackstar.ui.screens.main.coach.AthleteDetailScreen
import com.vasilisneo.trackstar.ui.screens.main.coach.TemplateEditorScreen
import com.vasilisneo.trackstar.ui.screens.main.coach.TemplatesScreen
import com.vasilisneo.trackstar.ui.screens.main.diet.AIDietPlannerScreen
import com.vasilisneo.trackstar.ui.screens.main.SettingsScreen
import com.vasilisneo.trackstar.ui.screens.main.stats.ExerciseProgressScreen
import com.vasilisneo.trackstar.ui.screens.main.stats.HistoryScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AboutScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AppSettingsScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.AppearanceScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.CloseAccountScreen
import com.vasilisneo.trackstar.ui.screens.main.settings.NotificationsScreen
import com.vasilisneo.trackstar.ui.screens.subscription.SubscriptionScreen
import com.vasilisneo.trackstar.data.auth.TokenStore
import com.vasilisneo.trackstar.ui.theme.TrackstarTheme
import com.vasilisneo.trackstar.ui.theme.loadSavedTheme

// A trackstar://invite/{token} deep link, parsed from the launching (or new) intent. `coachName`
// is an optional query param the coach's share link carries, used to personalize the sheet.
data class PendingInvite(val token: String, val coachName: String?)

// A tapped booking push (type == "booking"), carried from the notification's data payload. kind is
// booked / withdrawn / cancelled / spot_open; the rest describe the session for the detail popup.
data class PendingBookingTap(
    val kind: String?,
    val slotId: String?,
    val date: String?,
    val time: String?,
    val sessionTitle: String?,
    val person: String?,
)

class MainActivity : ComponentActivity() {
    // Held at Activity scope so onNewIntent (link tapped while already running) can push into the
    // same Compose state the initial intent seeds. AcceptInviteSheet renders when this is set.
    private val pendingInvite = androidx.compose.runtime.mutableStateOf<PendingInvite?>(null)

    // A tapped booking notification, staged for the composition to show a popup / navigate.
    private val pendingBooking = androidx.compose.runtime.mutableStateOf<PendingBookingTap?>(null)

    // Android 13+ requires runtime consent to post notifications; harmless no-op below that.
    private val requestNotificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleInviteIntent(intent)
        handleBookingIntent(intent)
        maybeRequestNotificationPermission()
        // Apply the saved Appearance theme before the first frame so there's no midnight→theme flash.
        loadSavedTheme(this)
        // Auto-login: if a session token is already persisted, open straight into the main
        // app (mirrors MasterCoordinator.start() on iOS), otherwise start at Landing.
        val startDestination = if (TokenStore(this).isLoggedIn) "main" else "landing"
        setContent {
            TrackstarTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Mirrors AuthCoordinator on iOS: Landing is the real entry point
                    // (.resetStack root), Login/Create-Account are pushed from it with a
                    // back button.
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        // NavHost has no slide animation by default — this replicates
                        // iOS/UIKit's push/pop: new screen slides in from the right over
                        // the current one (which parallax-shifts left slightly), and pop
                        // reverses it.
                        enterTransition = { slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) },
                        exitTransition = { slideOutHorizontally(targetOffsetX = { fullWidth -> -fullWidth / 3 }) },
                        popEnterTransition = { slideInHorizontally(initialOffsetX = { fullWidth -> -fullWidth / 3 }) },
                        popExitTransition = { slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) },
                    ) {
                        composable("landing") {
                            LandingScreen(
                                onCreateAccount = { navController.navigate("register") },
                                onLogin = { navController.navigate("login") },
                                onQuickLoginSuccess = {
                                    navController.navigate("main") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "login?email={email}",
                            arguments = listOf(navArgument("email") { type = NavType.StringType; defaultValue = "" })
                        ) { backStackEntry ->
                            val initialEmail = backStackEntry.arguments?.getString("email") ?: ""
                            LoginScreen(
                                showBackButton = true,
                                onBackClick = { navController.popBackStack() },
                                initialEmail = initialEmail,
                                onForgotPassword = { navController.navigate("forgot_password") },
                                onLoginSuccess = {
                                    navController.navigate("main") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("forgot_password") {
                            ForgotPasswordScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable(
                            "main",
                            // Hold still while Profile zooms in/out on top instead of
                            // parallax-sliding like the auth push flow does.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            MainAppScreen(
                                onProfileClick = { navController.navigate("profile") },
                                onScheduleWorkout = { navController.navigate("weekly_plan") },
                                onOpenBookSession = { navController.navigate("book_session") },
                                onOpenHistory = { navController.navigate("history") },
                                onOpenProgress = { navController.navigate("progress") },
                                onOpenAthlete = { athleteId -> navController.navigate("athlete/${Uri.encode(athleteId)}") },
                                onOpenAddAthlete = { navController.navigate("add_athlete") },
                                onOpenTemplates = { navController.navigate("templates") },
                                onOpenAvailability = { navController.navigate("coach_availability") },
                                onOpenAiDietPlanner = { navController.navigate("ai_diet_planner") },
                                onOpenSubscription = { navController.navigate("subscription") },
                            )
                        }
                        composable(
                            "templates",
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            TemplatesScreen(
                                onClose = { navController.popBackStack() },
                                onOpenTemplate = { id, name ->
                                    navController.navigate("template_editor/${Uri.encode(id)}/${Uri.encode(name)}")
                                },
                            )
                        }
                        composable(
                            route = "template_editor/{templateId}/{templateName}",
                            arguments = listOf(
                                navArgument("templateId") { type = NavType.StringType },
                                navArgument("templateName") { type = NavType.StringType },
                            ),
                            // Horizontal push over the Templates modal, like the athlete detail.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) { backStackEntry ->
                            TemplateEditorScreen(
                                templateId = backStackEntry.arguments?.getString("templateId") ?: "",
                                templateName = backStackEntry.arguments?.getString("templateName") ?: "",
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            "add_athlete",
                            // Slide-up modal like iOS's AddAthleteSheet.
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            AddAthleteScreen(
                                onClose = { navController.popBackStack() },
                                onAthleteAdded = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "athlete/{athleteId}",
                            arguments = listOf(navArgument("athleteId") { type = NavType.StringType }),
                            // Horizontal push like weekly_plan (iOS pushes AthleteDetailView), not a modal.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) { backStackEntry ->
                            AthleteDetailScreen(
                                athleteId = backStackEntry.arguments?.getString("athleteId") ?: "",
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            "weekly_plan",
                            // iOS pushes this via UIKit (WorkoutCoordinator.showWeeklyPlan) —
                            // a horizontal push, not a modal cover — so this uses the NavHost's
                            // default push transitions (same as "profile" et al.) rather than
                            // the vertical slide used for subscription/active_session.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            WeeklyPlanScreen(
                                onBackClick = { navController.popBackStack() },
                                onUpgrade = { navController.navigate("subscription") },
                                onOpenSession = { weekIdentifier, day, sessionId ->
                                    val sessionSegment = sessionId ?: "new"
                                    navController.navigate(
                                        "session_edit/${Uri.encode(weekIdentifier)}/${Uri.encode(day)}/${Uri.encode(sessionSegment)}"
                                    )
                                },
                            )
                        }
                        composable(
                            route = "session_edit/{weekIdentifier}/{day}/{sessionId}",
                            arguments = listOf(
                                navArgument("weekIdentifier") { type = NavType.StringType },
                                navArgument("day") { type = NavType.StringType },
                                navArgument("sessionId") { type = NavType.StringType },
                            ),
                            // Full-screen modal that slides up to present and down to dismiss, like
                            // iOS's fullScreenCover for SessionEditView (not a horizontal push).
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) { backStackEntry ->
                            val weekIdentifier = backStackEntry.arguments?.getString("weekIdentifier") ?: ""
                            val day = backStackEntry.arguments?.getString("day") ?: ""
                            val sessionSegment = backStackEntry.arguments?.getString("sessionId") ?: "new"
                            SessionEditScreen(
                                weekIdentifier = weekIdentifier,
                                day = day,
                                sessionId = sessionSegment.takeUnless { it == "new" },
                                onClose = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() },
                            )
                        }
                        composable(
                            "profile",
                            // iOS presents Profile with a zoom transition (from the avatar),
                            // not a horizontal push — approximate it with a fade + slight
                            // scale so it grows in over the stationary main screen.
                            enterTransition = { fadeIn() + scaleIn(initialScale = 0.92f) },
                            popExitTransition = { fadeOut() + scaleOut(targetScale = 0.92f) },
                            // Hold still when a child (Subscription modal, Personal Info,
                            // Settings) is pushed over it, instead of parallax-sliding left.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            ProfileScreen(
                                onBackClick = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate("landing") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                },
                                onPersonalInfo = { navController.navigate("personal_info") },
                                onSettings = { navController.navigate("settings") },
                                onUpgrade = { navController.navigate("subscription") },
                                onQrCode = { navController.navigate("qr") },
                                onMyCoach = { navController.navigate("my_coach") },
                            )
                        }
                        composable(
                            "my_coach",
                            // Pushed from Profile like the other detail screens (Settings, Personal
                            // Info) — horizontal push, holds still under further pushes.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            com.vasilisneo.trackstar.ui.screens.main.coach.MyCoachScreen(
                                onBack = { navController.popBackStack() },
                                onShowQr = { navController.navigate("qr") },
                            )
                        }
                        composable(
                            // Athlete books a session with their linked coach. Modal slide-up.
                            "book_session",
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            com.vasilisneo.trackstar.ui.screens.main.coach.BookSessionScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            // Coach manages the availability slots athletes can book. Modal slide-up.
                            "coach_availability",
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            com.vasilisneo.trackstar.ui.screens.main.coach.CoachAvailabilityScreen(
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable(
                            "qr",
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            // Encode the signed-in user's real email (persisted at login) so a
                            // coach scanning this "My QR" resolves a real athlete, not a placeholder.
                            val store = remember { TokenStore(this@MainActivity) }
                            val myName = listOfNotNull(
                                store.firstName?.ifBlank { null }, store.lastName?.ifBlank { null }
                            ).joinToString(" ")
                            QRConnectScreen(
                                qrString = store.email ?: "",
                                displayName = myName,
                                onBackClick = { navController.popBackStack() },
                                // Athlete scans a coach's invite QR (a trackstar://invite link) —
                                // stage it and pop back so AcceptInviteSheet shows over the tabs.
                                onScan = { code ->
                                    val invite = runCatching { parseInviteUri(Uri.parse(code)) }.getOrNull()
                                    if (invite != null) {
                                        pendingInvite.value = invite
                                        navController.popBackStack()
                                    }
                                },
                            )
                        }
                        composable(
                            "subscription",
                            // iOS presents this as a fullScreenCover — slide up from the
                            // bottom like a modal rather than the horizontal auth push.
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            SubscriptionScreen(onDismiss = { navController.popBackStack() })
                        }
                        composable(
                            // Full-screen modal like the AI workout planner (which lives inside the
                            // weekly-plan flow). Its ViewModel is owned here and disposed on exit.
                            "ai_diet_planner",
                            enterTransition = { slideInVertically(initialOffsetY = { it }) },
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                            popExitTransition = { slideOutVertically(targetOffsetY = { it }) },
                        ) {
                            val vm = remember { com.vasilisneo.trackstar.ui.screens.main.diet.AIDietPlannerViewModel() }
                            AIDietPlannerScreen(
                                viewModel = vm,
                                onClose = { vm.dispose(); navController.popBackStack() },
                                onApplied = {
                                    vm.dispose()
                                    com.vasilisneo.trackstar.ui.screens.main.diet.DietRefreshSignal.bump()
                                    navController.popBackStack()
                                },
                            )
                        }
                        composable(
                            // iOS pushes History (horizontal) and hides the tab bar; full-screen route here.
                            "history",
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            HistoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            "progress",
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            ExerciseProgressScreen(onBack = { navController.popBackStack() })
                        }
                        composable("personal_info") {
                            PersonalInfoScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                onOpenDetail = { route -> navController.navigate("settings_$route") }
                            )
                        }
                        composable("settings_notifications") {
                            NotificationsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable(
                            "settings_appearance",
                            // Hold still while the Subscription modal slides up over it.
                            exitTransition = { ExitTransition.None },
                            popEnterTransition = { EnterTransition.None },
                        ) {
                            AppearanceScreen(
                                onBackClick = { navController.popBackStack() },
                                onUpgrade = { navController.navigate("subscription") }
                            )
                        }
                        composable("settings_app_settings") {
                            AppSettingsScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_about") {
                            AboutScreen(onBackClick = { navController.popBackStack() })
                        }
                        composable("settings_close_account") {
                            CloseAccountScreen(
                                onDismiss = { navController.popBackStack() },
                                onClosed = {
                                    // Account deleted — full wipe including cached credentials
                                    // so "Continue as" won't offer the dead account.
                                    TokenStore(this@MainActivity).clearAll()
                                    navController.navigate("landing") {
                                        popUpTo("landing") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Registration flow — nested graph so every step shares one
                        // RegisterViewModel instance (same shape as iOS's shared
                        // RegisterViewModel passed down the whole NavigationStack).
                        navigation(startDestination = "email_entry", route = "register") {
                            composable("email_entry") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                EmailEntryScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onNewEmail = { navController.navigate("create_password") },
                                    onExistingEmail = { email ->
                                        navController.navigate("login?email=${Uri.encode(email)}")
                                    }
                                )
                            }
                            composable("create_password") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                CreatePasswordScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("personal_details") }
                                )
                            }
                            composable("personal_details") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                PersonalDetailsScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("body_metrics") }
                                )
                            }
                            composable("body_metrics") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                BodyMetricsScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("fitness_profile") }
                                )
                            }
                            composable("fitness_profile") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                FitnessProfileScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = { navController.navigate("goals") }
                                )
                            }
                            composable("goals") { backStackEntry ->
                                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("register") }
                                val registerViewModel: RegisterViewModel = viewModel(parentEntry)
                                GoalsScreen(
                                    viewModel = registerViewModel,
                                    onBackClick = { navController.popBackStack() },
                                    onContinue = {
                                        registerViewModel.register {
                                            navController.navigate("main") {
                                                popUpTo("landing") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Invite deep link (trackstar://invite/{token}) — renders as a modal sheet
                    // over whatever screen is showing. Only ever set when the athlete is logged in
                    // (gated in handleInviteIntent).
                    pendingInvite.value?.let { invite ->
                        com.vasilisneo.trackstar.ui.screens.main.coach.AcceptInviteSheet(
                            token = invite.token,
                            coachName = invite.coachName,
                            onDismiss = { pendingInvite.value = null },
                        )
                    }

                    // Tapped booking notification: cancelled -> detail popup (the slot is gone, so
                    // there's nothing to navigate to); spot_open -> athlete's booking screen;
                    // booked/withdrawn -> coach's availability screen.
                    pendingBooking.value?.let { tap ->
                        when (tap.kind) {
                            "cancelled" -> CancelledSessionDialog(tap) { pendingBooking.value = null }
                            "spot_open", "updated" -> androidx.compose.runtime.LaunchedEffect(tap) {
                                navController.navigate("book_session"); pendingBooking.value = null
                            }
                            "booked", "withdrawn" -> androidx.compose.runtime.LaunchedEffect(tap) {
                                navController.navigate("coach_availability"); pendingBooking.value = null
                            }
                            else -> androidx.compose.runtime.LaunchedEffect(tap) { pendingBooking.value = null }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInviteIntent(intent)
        handleBookingIntent(intent)
    }

    // Reads a tapped booking notification's data extras (see TrackstarMessagingService) and stages it
    // for the composition. Ignored unless logged in — the routing targets live in the main graph.
    private fun handleBookingIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra("type") != "booking") return
        if (!TokenStore(this).isLoggedIn) return
        pendingBooking.value = PendingBookingTap(
            kind = intent.getStringExtra("kind"),
            slotId = intent.getStringExtra("slotId"),
            date = intent.getStringExtra("date"),
            time = intent.getStringExtra("time"),
            sessionTitle = intent.getStringExtra("sessionTitle"),
            person = intent.getStringExtra("person"),
        )
    }

    // Parses trackstar://invite/{token}[?coachName=...] from an intent and stages it for the
    // AcceptInviteSheet. Ignored unless the athlete is logged in — an unauthenticated deep link
    // has no session to link a coach to, so we drop it rather than routing through auth.
    private fun handleInviteIntent(intent: android.content.Intent?) {
        val invite = intent?.data?.let(::parseInviteUri) ?: return
        if (!TokenStore(this).isLoggedIn) return
        pendingInvite.value = invite
    }

    // Ask for POST_NOTIFICATIONS on Android 13+ (Tiramisu) if not already granted. Pre-13 devices
    // grant notifications at install, so nothing to do.
    private fun maybeRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

// Parses a trackstar://invite/{token}[?coachName=...] URI into a PendingInvite, or null if it isn't
// a valid invite link. Shared by the deep-link intent handler and the in-app QR scanner.
fun parseInviteUri(data: Uri): PendingInvite? {
    if (!data.scheme.equals("trackstar", ignoreCase = true)) return null
    if (!data.host.equals("invite", ignoreCase = true)) return null
    val token = data.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
    return PendingInvite(token, data.getQueryParameter("coachName"))
}

// Shown when an athlete taps a "Session cancelled" push. The slot no longer exists server-side, so
// the details come entirely from the notification payload.
@androidx.compose.runtime.Composable
private fun CancelledSessionDialog(tap: PendingBookingTap, onDismiss: () -> Unit) {
    val whenText = buildString {
        tap.date?.let { append(prettyBookingDate(it)) }
        tap.time?.let { if (isNotEmpty()) append(" at "); append(it) }
    }
    val detail = buildString {
        tap.sessionTitle?.takeIf { it.isNotBlank() }?.let { append("\"").append(it).append("\"\n") }
        if (whenText.isNotBlank()) append(whenText)
        tap.person?.takeIf { it.isNotBlank() }?.let { append("\nwith ").append(it) }
    }.ifBlank { "Your session has been cancelled." }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A26),
        title = { androidx.compose.material3.Text("Session cancelled", color = androidx.compose.ui.graphics.Color.White) },
        text = {
            androidx.compose.material3.Text(
                detail,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("OK")
            }
        },
    )
}

// "2026-08-11" -> "Tue, 11 Aug"; falls back to the raw string if it doesn't parse.
private fun prettyBookingDate(iso: String): String = runCatching {
    java.time.LocalDate.parse(iso)
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM"))
}.getOrDefault(iso)
