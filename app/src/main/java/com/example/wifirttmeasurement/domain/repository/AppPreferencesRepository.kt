package com.example.wifirttmeasurement.domain.repository

import com.example.wifirttmeasurement.domain.model.AppRole
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val selectedRole: Flow<AppRole?>

    suspend fun setSelectedRole(role: AppRole)
}
