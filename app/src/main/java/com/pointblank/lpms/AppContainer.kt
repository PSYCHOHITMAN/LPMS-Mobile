package com.pointblank.lpms

import android.content.Context
import com.pointblank.lpms.data.firebase.FirebaseAuthRepository
import com.pointblank.lpms.data.net.NetworkMonitor
import com.pointblank.lpms.data.firebase.FirestoreLpmsRepository
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.LpmsRepository

/** Manual dependency container: the app runs on Firebase Auth and Cloud Firestore. */
class AppContainer(context: Context) {
    val networkMonitor = NetworkMonitor(context.applicationContext)
    val authRepository: AuthRepository = FirebaseAuthRepository(context.applicationContext)
    val lpmsRepository: LpmsRepository = FirestoreLpmsRepository(currentUser = { authRepository.currentUser.value })
}
