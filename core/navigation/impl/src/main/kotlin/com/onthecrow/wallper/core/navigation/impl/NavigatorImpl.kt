package com.onthecrow.wallper.core.navigation.impl

import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NavigatorImpl @Inject constructor() : Navigator {

    private val navigationCommands = Channel<NavigationCommand>(Channel.BUFFERED)
    val commands = navigationCommands.receiveAsFlow()

    override fun navigate(destination: Destination) {
        navigationCommands.trySend(NavigationCommand.To(destination = destination))
    }

    override fun navigateBack() {
        navigationCommands.trySend(NavigationCommand.Back)
    }
}
