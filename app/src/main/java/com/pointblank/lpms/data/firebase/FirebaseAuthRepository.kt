package com.pointblank.lpms.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.model.isStaff
import com.pointblank.lpms.core.model.User
import com.pointblank.lpms.core.util.Validators
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.AuthResult
import com.pointblank.lpms.data.repository.PENDING_MESSAGE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "FirebaseAuthRepo"
private const val SECONDARY_APP = "admin-create-user"

/**
 * Firebase Authentication holds the credentials (salted, hashed and stored by Google, so the app never
 * sees or stores a password). The role and profile live in Firestore at users/{uid}.
 */
class FirebaseAuthRepository(
    private val appContext: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    init {
        // Firebase keeps the session between launches; bring the profile back so the user stays signed in.
        auth.currentUser?.let { firebaseUser ->
            scope.launch {
                try {
                    val profile = loadProfile(firebaseUser)
                    if (profile.active) _currentUser.value = profile else auth.signOut()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not restore the previous session", e)
                }
            }
        }
    }

    // ---- sign up / sign in

    override suspend fun register(
        email: String, displayName: String, password: String,
        role: Role, school: String, grade: String, schoolId: String, childName: String
    ): AuthResult {
        // Only teachers and parents sign themselves up. Learners are enrolled by staff; coordinators and admins by an admin.
        if (role != Role.PARENT && role != Role.TEACHER) {
            return AuthResult.Failure("Coordinator, Admin and Learner accounts are created by an administrator")
        }
        val key = email.trim().lowercase()
        (Validators.validateName(displayName) ?: Validators.validateEmail(key) ?: Validators.validatePassword(password))
            ?.let { return AuthResult.Failure(it) }
        return try {
            val created = auth.createUserWithEmailAndPassword(key, password).await()
            val firebaseUser = created.user ?: return AuthResult.Failure("Could not create the account")
            val user = User(firebaseUser.uid, key, displayName.trim(), role, "", true, school, grade,
                approved = false, schoolId = schoolId, childName = childName.trim())
            try {
                db.collection(Col.USERS).document(user.id).set(user.toMap()).await()
            } catch (e: Exception) {
                firebaseUser.delete().await() // roll back so the email is not left half-registered
                throw e
            }
            // Not signed in yet: an administrator must approve the account first.
            auth.signOut()
            AuthResult.Pending(PENDING_MESSAGE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }
    }

    override suspend fun login(email: String, password: String): AuthResult =
        try {
            val result = auth.signInWithEmailAndPassword(email.trim().lowercase(), password).await()
            finishSignIn(result.user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }

    override suspend fun signInWithGoogle(idToken: String): AuthResult =
        try {
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            finishSignIn(result.user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }

    private suspend fun finishSignIn(firebaseUser: FirebaseUser?): AuthResult {
        if (firebaseUser == null) return AuthResult.Failure("Sign-in failed")
        val user = loadProfile(firebaseUser)
        if (!user.active) {
            auth.signOut()
            return AuthResult.Failure("This account has been suspended")
        }
        if (!user.approved) {
            auth.signOut()
            return AuthResult.Failure(PENDING_MESSAGE)
        }
        _currentUser.value = user
        return AuthResult.Success(user)
    }

    private suspend fun loadProfile(firebaseUser: FirebaseUser): User {
        val ref = db.collection(Col.USERS).document(firebaseUser.uid)
        val snapshot = ref.get().await()
        if (snapshot.exists()) return snapshot.toUser()
        // First sign-in without a profile (e.g. Google sign-in): create one as a parent, waiting for approval.
        val email = (firebaseUser.email ?: "").lowercase()
        val name = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
        val created = User(firebaseUser.uid, email, name, Role.PARENT, "", approved = false)
        ref.set(created.toMap()).await()
        return created
    }

    // ---- profile and password

    override suspend fun updateDisplayName(displayName: String): AuthResult {
        val current = _currentUser.value ?: return AuthResult.Failure("Not signed in")
        Validators.validateName(displayName)?.let { return AuthResult.Failure(it) }
        val name = displayName.trim()
        return try {
            db.collection(Col.USERS).document(current.id).update("displayName", name).await()
            val updated = current.copy(displayName = name)
            _currentUser.value = updated
            AuthResult.Success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): AuthResult {
        val current = _currentUser.value ?: return AuthResult.Failure("Not signed in")
        val firebaseUser = auth.currentUser ?: return AuthResult.Failure("Not signed in")
        if (firebaseUser.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }) {
            return AuthResult.Failure("You signed in with Google, so there is no LPMS password to change")
        }
        Validators.validatePassword(newPassword)?.let { return AuthResult.Failure(it) }
        return try {
            firebaseUser.reauthenticate(EmailAuthProvider.getCredential(current.email, oldPassword)).await()
            firebaseUser.updatePassword(newPassword).await()
            AuthResult.Success(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            AuthResult.Failure("Current password is incorrect")
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }
    }

    override fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    // ---- administration

    override suspend fun allUsers(): List<User> =
        if (_currentUser.value?.role?.isStaff != true) emptyList() else try {
            db.collection(Col.USERS).get().await().documents.map { it.toUser() }
                .sortedBy { it.displayName.lowercase() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read users", e)
            emptyList()
        }

    override suspend fun setActive(userId: String, active: Boolean): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can change account status")
        if (admin.id == userId) return AuthResult.Failure("You cannot suspend your own account")
        return updateUserField(userId, "active", active)
    }

    override suspend fun approveUser(userId: String): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN && admin?.role != Role.COORDINATOR) {
            return AuthResult.Failure("Only administrators and coordinators can approve accounts")
        }
        return updateUserField(userId, "approved", true)
    }

    override suspend fun setRole(userId: String, role: Role): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can change roles")
        if (admin.id == userId) return AuthResult.Failure("You cannot change your own role")
        return updateUserField(userId, "role", role.name)
    }

    private suspend fun updateUserField(userId: String, field: String, value: Any): AuthResult =
        try {
            val ref = db.collection(Col.USERS).document(userId)
            ref.update(field, value).await()
            val updated = ref.get().await().toUser()
            syncDirectory(updated)
            AuthResult.Success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }

    override suspend fun adminCreateUser(
        email: String, displayName: String, password: String,
        role: Role, school: String, grade: String, schoolId: String
    ): AuthResult {
        if (_currentUser.value?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can create accounts")
        val key = email.trim().lowercase()
        (Validators.validateName(displayName) ?: Validators.validateEmail(key) ?: Validators.validatePassword(password))
            ?.let { return AuthResult.Failure(it) }
        return try {
            // A second Firebase app instance creates the account, so the admin stays signed in.
            val secondary = secondaryAuth()
            val created = secondary.createUserWithEmailAndPassword(key, password).await()
            val uid = created.user?.uid ?: return AuthResult.Failure("Could not create the account")
            secondary.signOut()

            val user = User(uid, key, displayName.trim(), role, "", true, school, grade, schoolId = schoolId)
            db.collection(Col.USERS).document(uid).set(user.toMap()).await()
            syncDirectory(user)
            AuthResult.Success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }
    }

    override suspend fun updateAccount(userId: String, role: Role, school: School?, grade: String): AuthResult {
        val admin = _currentUser.value
        if (admin?.role != Role.ADMIN) return AuthResult.Failure("Only administrators can edit accounts")
        return try {
            val ref = db.collection(Col.USERS).document(userId)
            val current = ref.get().await().toUser()
            if (admin.id == userId && role != current.role) return AuthResult.Failure("You cannot change your own role")
            ref.update(
                mapOf("role" to role.name, "school" to (school?.name ?: ""), "schoolId" to (school?.id ?: ""), "grade" to grade)
            ).await()
            val updated = ref.get().await().toUser()
            syncDirectory(updated)
            AuthResult.Success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }
    }

    override suspend fun signUpWithGoogle(
        idToken: String, role: Role, school: String, grade: String, schoolId: String, childName: String
    ): AuthResult {
        if (role != Role.PARENT && role != Role.TEACHER) {
            return AuthResult.Failure("Coordinator, Admin and Learner accounts are created by an administrator")
        }
        return try {
            val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
            val firebaseUser = result.user ?: return AuthResult.Failure("Sign-in failed")
            val ref = db.collection(Col.USERS).document(firebaseUser.uid)
            // Someone who already has an account just signs in; the details chosen here only apply to a new one.
            if (ref.get().await().exists()) return finishSignIn(firebaseUser)
            val email = (firebaseUser.email ?: "").lowercase()
            val name = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")
            val user = User(firebaseUser.uid, email, name, role, "", true, school, grade,
                approved = false, schoolId = schoolId, childName = childName.trim())
            ref.set(user.toMap()).await()
            auth.signOut()
            AuthResult.Pending(PENDING_MESSAGE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failure(friendly(e))
        }
    }

    override suspend fun repairAccounts(schools: List<School>): Int {
        if (_currentUser.value?.role != Role.ADMIN) return 0
        var fixed = 0
        for (u in allUsers()) {
            var current = u
            if (u.schoolId.isBlank() && u.school.isNotBlank()) {
                val id = schools.firstOrNull { it.name == u.school }?.id
                if (id != null) {
                    try {
                        db.collection(Col.USERS).document(u.id).update("schoolId", id).await()
                        current = u.copy(schoolId = id)
                        fixed++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set the school id for ${u.email}", e)
                    }
                }
            }
            syncDirectory(current)
        }
        return fixed
    }

    /**
     * Keeps the staff directory (names only) in step with the profile, so parents can find teachers to message
     * without being able to read anyone's profile. Only approved, active teachers, coordinators and admins are listed.
     */
    private suspend fun syncDirectory(user: User) {
        try {
            val ref = db.collection(Col.DIRECTORY).document(user.id)
            val listed = user.active && user.approved &&
                (user.role == Role.TEACHER || user.role == Role.COORDINATOR || user.role == Role.ADMIN)
            if (listed) ref.set(user.toDirectoryMap()).await() else ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the staff directory", e)
        }
    }

    private fun secondaryAuth(): FirebaseAuth {
        val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == SECONDARY_APP }
        val app = existing ?: FirebaseApp.initializeApp(appContext, FirebaseApp.getInstance().options, SECONDARY_APP)
        return FirebaseAuth.getInstance(app)
    }

    // ---- errors

    private fun friendly(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "That password is too weak"
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> "Incorrect email or password"
        is FirebaseAuthUserCollisionException -> "An account with this email already exists"
        is FirebaseNetworkException -> "No internet connection. Check your network and try again"
        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again"
        else -> {
            Log.w(TAG, "Unexpected error", e)
            e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again"
        }
    }
}
