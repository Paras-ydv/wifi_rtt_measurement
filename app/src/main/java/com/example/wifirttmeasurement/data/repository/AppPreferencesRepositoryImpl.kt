package com.example.wifirttmeasurement.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.example.wifirttmeasurement.data.datastore.DataStoreKeys
import com.example.wifirttmeasurement.domain.model.AppRole
import com.example.wifirttmeasurement.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesRepository {
    override val selectedRole: Flow<AppRole?> = dataStore.data.map { preferences ->
        preferences[DataStoreKeys.SelectedRole]?.let { roleName ->
            AppRole.entries.firstOrNull { role -> role.name == roleName }
        }
    }

    override suspend fun setSelectedRole(role: AppRole) {
        dataStore.edit { preferences ->
            preferences[DataStoreKeys.SelectedRole] = role.name
        }
    }
}
