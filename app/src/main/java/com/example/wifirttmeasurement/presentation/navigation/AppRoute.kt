package com.example.wifirttmeasurement.presentation.navigation

import android.net.Uri

sealed interface AppRoute {
    val route: String

    data object Splash : AppRoute {
        override val route = "splash"
    }

    data object RoleSelection : AppRoute {
        override val route = "role_selection"
    }

    data object Receiver : AppRoute {
        override val route = "receiver"
    }

    data object Publisher : AppRoute {
        override val route = "publisher"
    }

    data object PublisherDetails : AppRoute {
        const val PublisherIdArgument = "publisherId"
        private const val BaseRoute = "publisher_details"

        override val route = "$BaseRoute/{$PublisherIdArgument}"

        fun createRoute(publisherId: String): String {
            return "$BaseRoute/${Uri.encode(publisherId)}"
        }
    }
}
