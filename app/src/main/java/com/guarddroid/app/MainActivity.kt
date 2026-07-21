package com.guarddroid.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.guarddroid.app.ui.DashboardScreen
import com.guarddroid.app.ui.ScanDetailScreen
import com.guarddroid.app.ui.theme.GuardDroidTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        val initialPackage = intent?.getStringExtra(EXTRA_PACKAGE)

        setContent {
            GuardDroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val startRoute = if (initialPackage != null) {
                        Routes.detail(initialPackage)
                    } else {
                        Routes.DASHBOARD
                    }

                    NavHost(navController = navController, startDestination = startRoute) {
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                onAppClick = { pkg -> navController.navigate(Routes.detail(pkg)) },
                            )
                        }
                        composable(Routes.DETAIL) { backStackEntry ->
                            val pkg = backStackEntry.arguments?.getString(Routes.ARG_PACKAGE).orEmpty()
                            ScanDetailScreen(
                                packageName = pkg,
                                onBack = {
                                    if (!navController.popBackStack()) {
                                        navController.navigate(Routes.DASHBOARD) {
                                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    object Routes {
        const val DASHBOARD = "dashboard"
        const val ARG_PACKAGE = "package"
        const val DETAIL = "detail/{$ARG_PACKAGE}"
        fun detail(packageName: String): String = "detail/$packageName"
    }

    companion object {
        const val EXTRA_PACKAGE = "com.guarddroid.app.extra.PACKAGE"

        /** Deep-links a notification tap to the scan-detail screen for [packageName]. */
        fun detailPendingIntent(context: Context, packageName: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("guarddroid://app/$packageName")
                putExtra(EXTRA_PACKAGE, packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            return PendingIntent.getActivity(
                context,
                packageName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
