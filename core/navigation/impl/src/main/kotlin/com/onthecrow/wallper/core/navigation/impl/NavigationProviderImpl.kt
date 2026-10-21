package com.onthecrow.wallper.core.navigation.impl

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.FeatureEntry
import com.onthecrow.wallper.core.navigation.NavigationProvider
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import javax.inject.Inject

/**
 * Navigation3 host. Unlike NomadRates it uses the official per-entry ViewModelStore decorator instead of
 * a hand-written scope controller: Hilt needs an owner with a default factory to create `@HiltViewModel`s.
 */
internal class NavigationProviderImpl @Inject constructor(
    private val navigator: NavigatorImpl,
    private val entries: Set<@JvmSuppressWildcards FeatureEntry<*>>,
    @param:StartDestination private val startDestination: Destination,
) : NavigationProvider {

    @Composable
    override fun Navigation(modifier: Modifier) {
        val entryMap = remember(entries) { entries.associateBy { it.keyClass } }
        val config = remember(entries) {
            SavedStateConfiguration {
                serializersModule = SerializersModule {
                    polymorphic(NavKey::class) {
                        entries.forEach { entry ->
                            @Suppress("UNCHECKED_CAST")
                            val typedEntry = entry as FeatureEntry<NavKey>
                            subclass(typedEntry.keyClass, typedEntry.serializer)
                        }
                    }
                }
            }
        }
        val backStack = rememberNavBackStack(config, startDestination)

        LaunchedEffect(navigator) {
            navigator.commands.collect { command ->
                when (command) {
                    is NavigationCommand.To -> backStack.add(command.destination)
                    is NavigationCommand.Back -> if (backStack.size > 1) backStack.removeLastOrNull()
                }
            }
        }

        NavDisplay(
            modifier = modifier,
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
            },
            popTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
            },
            entryProvider = { key ->
                NavEntry(key) {
                    @Suppress("UNCHECKED_CAST")
                    val entry = entryMap[key::class] as? FeatureEntry<NavKey>
                        ?: error("No screen registered for ${key::class.simpleName}")
                    entry.content(key, Modifier.fillMaxSize())
                }
            },
        )
    }
}
