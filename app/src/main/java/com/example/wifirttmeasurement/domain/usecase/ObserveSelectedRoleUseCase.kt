package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSelectedRoleUseCase @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository,
) {
    operator fun invoke(): Flow<AppRole?> {
        return appPreferencesRepository.selectedRole
    }
}
