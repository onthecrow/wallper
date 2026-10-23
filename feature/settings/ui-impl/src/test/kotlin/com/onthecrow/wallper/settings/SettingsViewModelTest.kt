package com.onthecrow.wallper.settings

import android.content.Intent
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val controller = FakeLiveWallpaperController()
    private val navigator = FakeNavigator()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = SettingsViewModel(
        reducer = SettingsReducer(),
        liveWallpaperController = controller,
        navigator = navigator,
        dispatchersProvider = TestDispatchers(dispatcher),
    )

    @Test
    fun `the wallpaper is checked as soon as the screen opens`() = runTest(dispatcher) {
        controller.isSet = true
        val viewModel = createViewModel()

        assertEquals(true, viewModel.state.value.isWallpaperSet)
        assertEquals(1, controller.checks)
    }

    @Test
    fun `the wallpaper is checked again whenever the screen returns`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        assertEquals(false, viewModel.state.value.isWallpaperSet)

        // Set in the system picker, then back to the screen.
        controller.isSet = true
        viewModel.onEvent(SettingsEvent.OnResume)
        assertEquals(true, viewModel.state.value.isWallpaperSet)
        assertEquals(2, controller.checks)
    }

    @Test
    fun `a new wallpaper while the screen is open is checked again`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        controller.isSet = true
        controller.changes.emit(Unit)
        assertEquals(true, viewModel.state.value.isWallpaperSet)

        controller.isSet = false
        controller.changes.emit(Unit)
        assertEquals(false, viewModel.state.value.isWallpaperSet)
    }

    @Test
    fun `back leaves the screen`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(SettingsEvent.OnBackClick)

        assertEquals(1, navigator.backs)
    }

    private class FakeLiveWallpaperController : LiveWallpaperController {
        var isSet = false
        var checks = 0
        val changes = MutableSharedFlow<Unit>()
        override fun createSetWallpaperIntent(): Intent = error("not used in these tests")
        override fun isSupported(wallpaper: BuiltInWallpaper) = true
        override fun isWallpaperSet(): Boolean {
            checks++
            return isSet
        }
        override fun wallpaperChanges(): Flow<Unit> = changes
    }

    private class FakeNavigator : Navigator {
        var backs = 0
        override fun navigate(destination: Destination) = Unit
        override fun navigateBack() { backs++ }
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatchersProvider {
        override val main = dispatcher
        override val io = dispatcher
        override val default = dispatcher
    }
}
