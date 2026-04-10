package com.internshipuncle.data.repository

import com.internshipuncle.core.model.RepositoryStatus
import com.internshipuncle.core.network.AppConfig
import com.internshipuncle.data.dto.ProfileDto
import com.internshipuncle.data.mapper.toDomainModel
import com.internshipuncle.data.mapper.toUpsertDto
import com.internshipuncle.data.remote.SupabaseTables
import com.internshipuncle.domain.model.OnboardingProfileInput
import com.internshipuncle.domain.model.UserProfile
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

data class AuthSession(
    val isRestoring: Boolean = true,
    val isConfigured: Boolean = true,
    val isLoggedIn: Boolean = false,
    val email: String? = null,
    val profile: UserProfile? = null
) {
    val needsOnboarding: Boolean
        get() = isLoggedIn && profile?.isComplete != true
}

interface AuthRepository {
    fun session(): Flow<AuthSession>
    suspend fun signIn(email: String, password: String): RepositoryStatus
    suspend fun signUp(email: String, password: String): RepositoryStatus
    suspend fun submitOnboarding(input: OnboardingProfileInput): RepositoryStatus
    suspend fun signOut(): RepositoryStatus
}

class SupabaseAuthRepository @Inject constructor(
    private val appConfig: AppConfig,
    private val auth: Auth,
    private val postgrest: Postgrest,
    private val dashboardRefreshBus: DashboardRefreshBus
) : AuthRepository {
    private val profileRefreshSignal = MutableStateFlow(0)

    override fun session(): Flow<AuthSession> {
        if (!appConfig.isSupabaseConfigured) {
            return flowOf(
                AuthSession(
                    isRestoring = false,
                    isConfigured = false,
                    isLoggedIn = false
                )
            )
        }

        return combine(auth.sessionStatus, profileRefreshSignal) { status, _ -> status }
            .map { status ->
                when (status) {
                    is SessionStatus.Initializing -> {
                        AuthSession(
                            isRestoring = true,
                            isConfigured = true,
                            isLoggedIn = false
                        )
                    }

                    is SessionStatus.Authenticated -> {
                        val email = status.session.user?.email
                        val userId = status.session.user?.id
                        val profile = if (userId != null) {
                            loadProfile(userId)
                        } else {
                            email?.let(::fallbackProfile)
                        }
                        AuthSession(
                            isRestoring = false,
                            isConfigured = true,
                            isLoggedIn = true,
                            email = email,
                            profile = profile ?: email?.let(::fallbackProfile)
                        )
                    }

                    is SessionStatus.NotAuthenticated,
                    is SessionStatus.RefreshFailure -> {
                        AuthSession(
                            isRestoring = false,
                            isConfigured = true,
                            isLoggedIn = false
                        )
                    }
                }
            }
            .catch {
                emit(
                    AuthSession(
                        isRestoring = false,
                        isConfigured = appConfig.isSupabaseConfigured,
                        isLoggedIn = false
                    )
                )
            }
    }

    override suspend fun signIn(
        email: String,
        password: String
    ): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            RepositoryStatus.Success
        } catch (error: Exception) {
            RepositoryStatus.Failure(
                message = error.message ?: "Unable to sign in with Supabase Auth.",
                cause = error
            )
        }
    }

    override suspend fun signUp(
        email: String,
        password: String
    ): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        return try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            RepositoryStatus.Success
        } catch (error: Exception) {
            RepositoryStatus.Failure(
                message = error.message ?: "Unable to create a Supabase Auth user.",
                cause = error
            )
        }
    }

    override suspend fun submitOnboarding(
        input: OnboardingProfileInput
    ): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        val user = auth.currentUserOrNull()
            ?: return RepositoryStatus.Failure(message = "No signed-in user found for onboarding.")

        return try {
            postgrest.from(SupabaseTables.PROFILES).upsert(
                value = input.toUpsertDto(
                    userId = user.id,
                    email = user.email
                )
            ) {
                onConflict = "id"
            }
            profileRefreshSignal.update { it + 1 }
            dashboardRefreshBus.refresh()
            RepositoryStatus.Success
        } catch (error: Exception) {
            when {
                error.isProfilesSchemaMissing() -> RepositoryStatus.BackendNotReady
                else -> RepositoryStatus.Failure(
                    message = error.message
                        ?: "Unable to save onboarding profile to Supabase.",
                    cause = error
                )
            }
        }
    }

    override suspend fun signOut(): RepositoryStatus {
        if (!appConfig.isSupabaseConfigured) {
            return RepositoryStatus.NotConfigured
        }

        return try {
            auth.signOut()
            RepositoryStatus.Success
        } catch (error: Exception) {
            RepositoryStatus.Failure(
                message = error.message ?: "Unable to sign out.",
                cause = error
            )
        }
    }

    private suspend fun loadProfile(
        userId: String
    ): UserProfile? {
        return try {
            postgrest.from(SupabaseTables.PROFILES)
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<ProfileDto>()
                .firstOrNull()
                ?.toDomainModel()
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackProfile(
        email: String
    ): UserProfile {
        return UserProfile(
            email = email,
            name = email.substringBefore("@"),
            college = "",
            degree = "",
            graduationYear = null,
            targetRoles = emptyList()
        )
    }

    private fun Exception.isProfilesSchemaMissing(): Boolean {
        val message = message.orEmpty()
        return message.contains("profiles", ignoreCase = true) &&
            (
                message.contains("does not exist", ignoreCase = true) ||
                    message.contains("schema cache", ignoreCase = true) ||
                    message.contains("Could not find the table", ignoreCase = true) ||
                    message.contains("PGRST", ignoreCase = true)
                )
    }
}
