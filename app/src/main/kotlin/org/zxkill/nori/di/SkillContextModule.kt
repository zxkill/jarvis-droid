package org.zxkill.nori.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SkillContextModule {
    @Provides
    @Singleton
    fun provideSkillContext(skillContextImpl: SkillContextImpl): SkillContextInternal =
        skillContextImpl
}
