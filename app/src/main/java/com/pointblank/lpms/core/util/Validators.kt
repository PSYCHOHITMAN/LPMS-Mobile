package com.pointblank.lpms.core.util

/** Each function returns an error message, or null when the input is valid. */
object Validators {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !EMAIL.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.length < 8 -> "Password must be at least 8 characters"
        password.none { it.isLetter() } -> "Password must contain a letter"
        password.none { it.isDigit() } -> "Password must contain a number"
        else -> null
    }

    fun validateName(name: String): String? = when {
        name.isBlank() -> "Name is required"
        name.trim().length < 2 -> "Name is too short"
        name.trim().length > 60 -> "Name is too long"
        else -> null
    }

    fun validateGrade(grade: String): String? =
        if (Grades.parse(grade) == null) "Choose a grade (R, 1, 2, 3 ...)" else null

    fun validateChildName(name: String): String? = when {
        name.isBlank() -> "Enter your child's name"
        name.trim().length < 2 -> "The child's name is too short"
        else -> null
    }
}
