package com.punyplatipus.podouvir.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.punyplatipus.podouvir.ui.discover.DiscoverRoute
import com.punyplatipus.podouvir.ui.library.LibraryRoute
import com.punyplatipus.podouvir.ui.player.MiniPlayerSlot
import com.punyplatipus.podouvir.ui.podcast.PodcastDetailPlaceholder
import com.punyplatipus.podouvir.ui.queue.QueueScreen

@Composable
fun MainScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selected = TopLevelTab.entries.firstOrNull { tab ->
        destination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
    }

    fun navigateToTab(tab: TopLevelTab) {
        navController.navigate(tab.destination) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Column {
                MiniPlayerSlot()
                BottomBar(selected = selected, onSelect = ::navigateToTab)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LibraryDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<LibraryDestination> {
                LibraryRoute(
                    onOpenPodcast = { id -> navController.navigate(PodcastDestination(id)) },
                    onDiscover = { navigateToTab(TopLevelTab.DISCOVER) },
                )
            }
            composable<DiscoverDestination> { DiscoverRoute() }
            composable<QueueDestination> { QueueScreen() }
            composable<PodcastDestination> { entry ->
                val route = entry.toRoute<PodcastDestination>()
                PodcastDetailPlaceholder(
                    podcastId = route.podcastId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Stateless; [selected] null = no tab highlighted (e.g. on the detail screen). */
@Composable
fun BottomBar(
    selected: TopLevelTab?,
    onSelect: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        TopLevelTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}
