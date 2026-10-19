package com.pointblank.lpms.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pointblank.lpms.LpmsApplication
import com.pointblank.lpms.ui.auth.AuthViewModel
import com.pointblank.lpms.ui.home.HomeViewModel
import com.pointblank.lpms.ui.screens.ScreensViewModel
import com.pointblank.lpms.ui.messages.MessagesViewModel
import com.pointblank.lpms.ui.settings.SettingsViewModel
import com.pointblank.lpms.ui.teacher.MyLearnersViewModel
import com.pointblank.lpms.ui.teacher.TeacherAttendanceViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { AuthViewModel(lpmsApplication().container.authRepository, lpmsApplication().container.lpmsRepository) }
        initializer { HomeViewModel(lpmsApplication().container.lpmsRepository, lpmsApplication().container.authRepository) }
        initializer { MyLearnersViewModel(lpmsApplication().container.lpmsRepository, lpmsApplication().container.authRepository) }
        initializer { TeacherAttendanceViewModel(lpmsApplication().container.lpmsRepository, lpmsApplication().container.authRepository) }
        initializer { ScreensViewModel(lpmsApplication().container.lpmsRepository, lpmsApplication().container.authRepository) }
        initializer { MessagesViewModel(lpmsApplication().container.lpmsRepository, lpmsApplication().container.authRepository) }
        initializer { SettingsViewModel(lpmsApplication().container.authRepository) }
    }
}

fun CreationExtras.lpmsApplication(): LpmsApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LpmsApplication
