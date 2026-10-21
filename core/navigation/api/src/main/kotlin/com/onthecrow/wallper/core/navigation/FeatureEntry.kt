package com.onthecrow.wallper.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

/** A screen registered in the navigation host. Features contribute it to a Hilt `@IntoSet` multibinding. */
interface FeatureEntry<T : Destination> {
    val keyClass: KClass<T>
    val serializer: KSerializer<T>
    val content: @Composable (T, Modifier) -> Unit
}

/**
 * Hilt counterpart of `registerScreen` from NomadRates:
 *
 * ```
 * @Provides @IntoSet
 * fun provideEntry(): FeatureEntry<*> = featureEntry<WallpapersDestination> { destination, modifier -> ... }
 * ```
 */
inline fun <reified T : Destination> featureEntry(
    noinline content: @Composable (T, Modifier) -> Unit,
): FeatureEntry<T> = object : FeatureEntry<T> {
    override val keyClass: KClass<T> = T::class
    override val serializer: KSerializer<T> = serializer<T>()
    override val content: @Composable (T, Modifier) -> Unit = content
}
