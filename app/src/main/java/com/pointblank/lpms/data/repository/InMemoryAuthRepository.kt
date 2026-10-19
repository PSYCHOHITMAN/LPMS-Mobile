package com.pointblank.lpms.data.repository

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.model.User
import com.pointblank.lpms.core.util.Validators
import com.pointblank.lpms.data.security.PasswordHasher
import com.pointblank.lpms.data.seed.SeedData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

const val PENDING_MESSAGE = "Account created. An administrator needs to approve it before you can sign in."

class InMemoryAuthRepository(seedUsers: List<User> = SeedData.users()) : AuthRepository {

    private val users = ConcurrentHashMap<String, User>() // key = lower-case email
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        seedUsers.forEach { users[it.email.lowercase()] = it }
    }

    override suspend fun register(
        email: String, displayName: String, password: String,
        role: Role, school: String, grade: String, schoolId: String, childName: String
    ): AuthResult {
        // Only teachers and parents can self-register. Learners are enrolled by staff; coordinators and admins are added by an admin.
        if (role != Role.PARENT && role != Role.TEACHER) return AuthResult.Failure("Coordinator, Admin and Learner accounts are created by an administrator")
        val key = email.trim().lowercase()
        (Validators.validateName(displayName)
            ?: Validators.validateEmail(key)
            ?: Validators.validatePassword(password))?.let { return AuthResult.Failure(it) }
        if (users.containsKey(key)) return AuthResult.Failure("An account with this email already exists")

        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password) }
        val user = User(UUID.randomUUID().toString(), key, displayName.trim(), role, hash, school = school, grade = grade,
            approved = false, schoolId = schoolId, childName = childName.trim())
        users[key] = user
        return AuthResult.Pending(PENDING_MESSAGE)
    }

    override suspend fun login(email: String, password: String): AuthResult {
        val user = users[email.trim().lowercase()]
        val ok = user != null && withContext(Dispatchers.Default) { PasswordHasher.verify(password, user.passwordHash) }
        if (!ok || user == null) return AuthResult.Failure("Incorrect email or password")
        if (!user.active) return AuthResult.Failure("This account has been suspended")
        if (!user.approved) return AuthResult.Failure(PENDING_MESSAGE)
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult =
        AuthResult.Failure("Single sign-on needs the Firebase backend")

    override suspend fun updateDisplayName(displayName: String): AuthResult {
        val current = _currentUser.value ?: return AuthResult.Failure("Not signed in")
        Validators.validateName(displayName)?.let { return AuthResult.Failure(it) }
        val updated = current.copy(displayName = displayName.trim())
        users[updated.email.lowercase()] = updated
        _currentUser.value = updated
        return AuthResult.Success(updated)
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult {
        val current = _currentUser.value ?: return AuthResult.Failure("Not signed in")
        val oldOk = withContext(Dispatchers.Default) { PasswordHasher.verify(oldPassword, current.passwordHash) }
        if (!oldOk) return AuthResult.Failure("Current password is incorrect")
        Validators.validatePassword(newPassword)?.let { return AuthResult.Failure(it) }
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(newPassword) }
        val updated = current.copy(passwordHash = hash)
        users[updated.email.lowercase()] = updated
        _currentUser.value = updated
        return AuthResult.Success(updated)
    }

    override suspend fun allUsers(): List<User> = users.values.sortedBy { it.displayName.lowercase() }

    override suspend fun setActive(userId: String, active: Boolean): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can change account status")
        if (admin.id == userId) return AuthResult.Failure("You cannot suspend your own account")
        val target = users.values.firstOrNull { it.id == userId } ?: return AuthResult.Failure("User not found")
        val updated = target.copy(active = active)
        users[updated.email.lowercase()] = updated
        return AuthResult.Success(updated)
    }

    override suspend fun approveUser(userId: String): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN && admin?.role != Role.COORDINATOR) {
            return AuthResult.Failure("Only administrators and coordinators can approve accounts")
        }
        val target = users.values.firstOrNull { it.id == userId } ?: return AuthResult.Failure("User not found")
        val updated = target.copy(approved = true)
        users[updated.email.lowercase()] = updated
        return AuthResult.Success(updated)
    }

    override suspend fun setRole(userId: String, role: Role): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can change roles")
        if (admin.id == userId) return AuthResult.Failure("You cannot change your own role")
        val target = users.values.firstOrNull { it.id == userId } ?: return AuthResult.Failure("User not found")
        val updated = target.copy(role = role)
        users[updated.email.lowercase()] = updated
        return AuthResult.Success(updated)
    }

    override suspend fun adminCreateUser(
        email: String, displayName: String, password: String,
        role: Role, school: String, grade: String, schoolId: String
    ): AuthResult {
        if (_currentUser.value?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can create accounts")
        val key = email.trim().lowercase()
        (Validators.validateName(displayName) ?: Validators.validateEmail(key) ?: Validators.validatePassword(password))
            ?.let { return AuthResult.Failure(it) }
        if (users.containsKey(key)) return AuthResult.Failure("An account with this email already exists")
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password) }
        val user = User(UUID.randomUUID().toString(), key, displayName.trim(), role, hash, school = school, grade = grade,
            schoolId = schoolId)
        users[key] = user
        return AuthResult.Success(user)
    }

    override suspend fun signUpWithGoogle(
        idToken: String, role: Role, school: String, grade: String, schoolId: String, childName: String
    ): AuthResult = AuthResult.Failure("Single sign-on needs the Firebase backend")

    override suspend fun updateAccount(userId: String, role: Role, school: School?, grade: String): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can edit accounts")
        val target = users.values.firstOrNull { it.id == userId } ?: return AuthResult.Failure("User not found")
        if (admin.id == userId && role != target.role) return AuthResult.Failure("You cannot change your own role")
        val updated = target.copy(role = role, school = school?.name ?: "", schoolId = school?.id ?: "", grade = grade)
        users[updated.email.lowercase()] = updated
        return AuthResult.Success(updated)
    }

    override suspend fun repairAccounts(schools: List<School>): Int {
        if (_currentUser.value?.role != Role.ADMIN) return 0
        var fixed = 0
        users.values.filter { it.schoolId.isBlank() && it.school.isNotBlank() }.forEach { u ->
            val id = schools.firstOrNull { it.name == u.school }?.id ?: return@forEach
            users[u.email.lowercase()] = u.copy(schoolId = id)
            fixed++
        }
        return fixed
    }

    override fun logout() {
        _currentUser.value = null
    }
}
