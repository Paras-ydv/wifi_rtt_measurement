package com.example.wifirttmeasurement.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.presentation.ui.publisher.PublisherScreen
import com.example.wifirttmeasurement.presentation.ui.receiver.PublisherDetailsScreen
import com.example.wifirttmeasurement.presentation.ui.receiver.ReceiverScreen
import com.example.wifirttmeasurement.presentation.ui.role.RoleSelectionScreen
import com.example.wifirttmeasurement.presentation.ui.splash.SplashScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppRoute.Splash.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoute.Splash.route) {
            SplashScreen(
                onContinue = {
                    navController.navigate(AppRoute.RoleSelection.route) {
                        popUpTo(AppRoute.Splash.route) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(AppRoute.RoleSelection.route) {
            RoleSelectionScreen(
                onNavigateToRole = { role ->
                    when (role) {
                        AppRole.Receiver -> navController.navigateSingleTop(AppRoute.Receiver.route)
                        AppRole.Publisher -> navController.navigateSingleTop(AppRoute.Publisher.route)
                    }
                },
            )
        }

        composable(AppRoute.Receiver.route) {
            ReceiverScreen(
                onPublisherSelected = { publisherId ->
                    navController.navigateSingleTop(AppRoute.PublisherDetails.createRoute(publisherId))
                },
            )
        }

        composable(AppRoute.Publisher.route) {
            PublisherScreen()
        }

        composable(
            route = AppRoute.PublisherDetails.route,
            arguments = listOf(
                navArgument(AppRoute.PublisherDetails.PublisherIdArgument) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val publisherId = backStackEntry.arguments
                ?.getString(AppRoute.PublisherDetails.PublisherIdArgument)
                .orEmpty()

            PublisherDetailsScreen(
                publisherId = publisherId,
                onNavigateBack = navController::navigateUp,
            )
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}
