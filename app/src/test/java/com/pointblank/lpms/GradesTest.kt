package com.pointblank.lpms

import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GradesTest {
    @Test fun gradeRIsZeroInEveryWrittenForm() {
        listOf("R", "r", "Grade R", "grade r", "Gr R", "gr.r", "Reception", "0", "  R  ").forEach {
            assertEquals("'$it'", 0, Grades.parse(it))
        }
    }

    @Test fun numberedGradesParse() {
        assertEquals(3, Grades.parse("3"))
        assertEquals(3, Grades.parse("Grade 3"))
        assertEquals(3, Grades.parse(" grade 3 "))
        assertEquals(7, Grades.parse("Gr 7"))
        assertEquals(12, Grades.parse("12"))
    }

    @Test fun nonsenseIsNotAGrade() {
        listOf("", "  ", "abc", "13", "-1", "Grade", "3x", "R2").forEach { assertNull("'$it'", Grades.parse(it)) }
    }

    @Test fun labelsAndDisplayNames() {
        assertEquals("R", Grades.label(0))
        assertEquals("3", Grades.label(3))
        assertEquals("Grade R", Grades.display(0))
        assertEquals("Grade 3", Grades.display(3))
        assertEquals("Grade R", Grades.display("R"))
        assertEquals("Grade 3", Grades.display("3"))
        assertEquals("Grade 3", Grades.display("Grade 3"))
        assertEquals("whatever", Grades.display("whatever"))
    }

    @Test fun choicesAreRThenOneToSevenAndAllParse() {
        assertEquals(listOf("R", "1", "2", "3", "4", "5", "6", "7"), Grades.choices)
        Grades.choices.forEach { assertNotNull(Grades.parse(it)) }
    }

    @Test fun validatorsMatchTheParser() {
        assertNull(Validators.validateGrade("R"))
        assertNull(Validators.validateGrade("3"))
        assertNotNull(Validators.validateGrade("x"))
        assertNotNull(Validators.validateGrade(""))
        assertNull(Validators.validateChildName("Thabo"))
        assertNotNull(Validators.validateChildName(" "))
    }
}
