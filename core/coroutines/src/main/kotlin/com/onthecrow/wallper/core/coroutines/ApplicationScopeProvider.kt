package com.onthecrow.wallper.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** App-lifetime scope for work that must outlive a screen. Replaces ad-hoc `MainScope()`. */
interface ApplicationScopeProvider {
    val applicationScope: CoroutineScope
}

internal class DefaultApplicationScopeProvider(
    dispatchersProvider: DispatchersProvider,
) : ApplicationScopeProvider {
    override val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchersProvider.default)
}
