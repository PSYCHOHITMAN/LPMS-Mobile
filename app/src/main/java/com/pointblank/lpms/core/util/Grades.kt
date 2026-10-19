package com.pointblank.lpms.core.util

/** School grades as READ uses them: Grade R (reception) then 1 to 12. Grade R is stored as 0. */
object Grades {
    /** Choices offered when registering or enrolling (READ's focus is Grade R to 3). */
    val choices: List<String> = listOf("R", "1", "2", "3", "4", "5", "6", "7")

    /** Accepts "R", "Grade R", "gr r", "Reception", "3", "Grade 3", " 3 ". Returns null if it is not a grade. */
    fun parse(text: String): Int? {
        val t = text.trim().lowercase().replace(Regex("^(grade|gr\\.?|class)\\s*"), "").trim()
        if (t == "r" || t == "reception" || t == "0") return 0
        val n = t.toIntOrNull() ?: return null
        return if (n in 1..12) n else null
    }

    /** "R" or "3", the form stored on users. */
    fun label(grade: Int): String = if (grade == 0) "R" else grade.toString()

    /** "Grade R" or "Grade 3", the form shown to people. */
    fun display(grade: Int): String = "Grade ${label(grade)}"

    /** Same as [display] for stored text such as "3", "Grade 3" or "R"; unknown text is returned as it is. */
    fun display(text: String): String = parse(text)?.let { display(it) } ?: text
}
