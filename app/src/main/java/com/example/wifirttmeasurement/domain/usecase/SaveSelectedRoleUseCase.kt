package com.example.wifirttmeasurement.domain.usecase

import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.domain.repository.AppPreferencesRepository
import javax.inject.Inject

class SaveSelectedRoleUseCase @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository,
) {
    suspend operator fun invoke(role: AppRole) {
        appPreferencesRepository.setSelectedRole(role)
    }
}
