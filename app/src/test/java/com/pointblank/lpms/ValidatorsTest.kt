package com.pointblank.lpms

import com.pointblank.lpms.core.util.Validators
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test fun validEmailPasses() = assertNull(Validators.validateEmail("coordinator@read.org.za"))
    @Test fun blankEmailFails() = assertNotNull(Validators.validateEmail("  "))
    @Test fun malformedEmailFails() = assertNotNull(Validators.validateEmail("not-an-email"))

    @Test fun strongPasswordPasses() = assertNull(Validators.validatePassword("password123"))
    @Test fun shortPasswordFails() = assertNotNull(Validators.validatePassword("abc123"))
    @Test fun passwordWithoutDigitFails() = assertNotNull(Validators.validatePassword("passwordonly"))
    @Test fun passwordWithoutLetterFails() = assertNotNull(Validators.validatePassword("12345678"))

    @Test fun normalNamePasses() = assertNull(Validators.validateName("Percy"))
    @Test fun blankNameFails() = assertNotNull(Validators.validateName(""))
}
