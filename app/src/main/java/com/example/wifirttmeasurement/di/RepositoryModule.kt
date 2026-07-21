package com.example.wifirttmeasurement.di

import com.example.wifirttmeasurement.data.repository.AppPreferencesRepositoryImpl
import com.example.wifirttmeasurement.data.repository.CsvExportRepositoryImpl
import com.example.wifirttmeasurement.data.repository.DeviceCapabilityRepositoryImpl
import com.example.wifirttmeasurement.data.repository.LogRepositoryImpl
import com.example.wifirttmeasurement.data.repository.PublisherRepositoryImpl
import com.example.wifirttmeasurement.data.repository.ReceiverRepositoryImpl
import com.example.wifirttmeasurement.domain.repository.AppPreferencesRepository
import com.example.wifirttmeasurement.domain.repository.CsvExportRepository
import com.example.wifirttmeasurement.domain.repository.DeviceCapabilityRepository
import com.example.wifirttmeasurement.domain.repository.LogRepository
import com.example.wifirttmeasurement.domain.repository.PublisherRepository
import com.example.wifirttmeasurement.domain.repository.ReceiverRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        implementation: AppPreferencesRepositoryImpl,
    ): AppPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindDeviceCapabilityRepository(
        implementation: DeviceCapabilityRepositoryImpl,
    ): DeviceCapabilityRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(
        implementation: LogRepositoryImpl,
    ): LogRepository

    @Binds
    @Singleton
    abstract fun bindPublisherRepository(
        implementation: PublisherRepositoryImpl,
    ): PublisherRepository

    @Binds
    @Singleton
    abstract fun bindCsvExportRepository(
        implementation: CsvExportRepositoryImpl,
    ): CsvExportRepository

    @Binds
    @Singleton
    abstract fun bindReceiverRepository(
        implementation: ReceiverRepositoryImpl,
    ): ReceiverRepository
}
