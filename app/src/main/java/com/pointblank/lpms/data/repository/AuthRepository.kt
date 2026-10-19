package com.pointblank.lpms.data.repository

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.model.User
import kotlinx.coroutines.flow.StateFlow

sealed interface AuthResult {
    data class Success(val user: User) : AuthResult
    data class Failure(val message: String) : AuthResult
    /** Account created but not usable until an administrator approves it. */
    data class Pending(val message: String) : AuthResult
}

/**
 * Swap the implementation for the chosen provider (Firebase Auth, Supabase Auth, Azure AD B2C)
 * without touching the UI layer.
 */
interface AuthRepository {
    val currentUser: StateFlow<User?>
    suspend fun register(
        email: String, displayName: String, password: String,
        role: Role = Role.PARENT, school: String = "", grade: String = "",
        schoolId: String = "", childName: String = ""
    ): AuthResult
    suspend fun login(email: String, password: String): AuthResult
    /** Single sign-on: exchanges a Google ID token for an LPMS session. */
    suspend fun signInWithGoogle(idToken: String): AuthResult

    /**
     * Google sign-up with the details chosen on the register screen (role, school, grade or child's name).
     * An existing account just signs in; a new one is created waiting for approval.
     */
    suspend fun signUpWithGoogle(
        idToken: String, role: Role, school: String, grade: String, schoolId: String, childName: String
    ): AuthResult
    suspend fun updateDisplayName(displayName: String): AuthResult
    suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult
    suspend fun allUsers(): List<User>
    suspend fun setActive(userId: String, active: Boolean): AuthResult
    suspend fun setRole(userId: String, role: Role): AuthResult
    suspend fun approveUser(userId: String): AuthResult

    /** Admin-only: creates another person's account without signing the admin out. */
    suspend fun adminCreateUser(
        email: String, displayName: String, password: String,
        role: Role, school: String = "", grade: String = "", schoolId: String = ""
    ): AuthResult

    /**
     * Admin-only: sets someone's role, school and grade (for example a person who signed in with Google and
     * was created without them). A teacher's school and grade decide which learners and sessions they see.
     */
    suspend fun updateAccount(userId: String, role: Role, school: School?, grade: String): AuthResult

    /**
     * Admin-only repair for accounts made by older versions: fills in the school id (matched by school name) and
     * keeps the staff directory used for messaging in step. Returns how many accounts were changed.
     */
    suspend fun repairAccounts(schools: List<School>): Int
    fun logout()
}
