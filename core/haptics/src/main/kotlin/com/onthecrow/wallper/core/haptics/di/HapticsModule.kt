package com.onthecrow.wallper.core.haptics.di

import com.onthecrow.wallper.core.haptics.Haptics
import com.onthecrow.wallper.core.haptics.VibratorHaptics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HapticsModule {

    @Binds
    abstract fun bindHaptics(impl: VibratorHaptics): Haptics
}
