package com.onthecrow.wallper.core.navigation.impl

import com.onthecrow.wallper.core.navigation.Destination

internal sealed interface NavigationCommand {
    data class To(val destination: Destination) : NavigationCommand
    data object Back : NavigationCommand
}
