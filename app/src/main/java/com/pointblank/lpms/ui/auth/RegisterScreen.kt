package com.pointblank.lpms.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.ErrorBanner
import com.pointblank.lpms.ui.components.GradeChips
import com.pointblank.lpms.ui.components.LpmsCard
import com.pointblank.lpms.ui.components.LpmsLogo
import com.pointblank.lpms.ui.theme.*

@Composable
private fun RoleOption(title: String, desc: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) BrandTint else Color.White),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Brand else Line),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (selected) BrandDark else Ink)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var roleName by rememberSaveable { mutableStateOf<String?>(null) }
    val schools by viewModel.schools.collectAsStateWithLifecycle()
    var schoolId by rememberSaveable { mutableStateOf("") }
    var grade by rememberSaveable { mutableStateOf("") }
    var childName by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val role = roleName?.let { Role.valueOf(it) }
    val school = schools.firstOrNull { it.id == schoolId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun clear() = viewModel.clearError()
    // Schools are added after the app starts (demo data, admin), so reload the list whenever this screen opens.
    LaunchedEffect(Unit) { viewModel.loadSchools() }
    // After a sign-up that needs approval, go back to the sign-in screen, which shows the message.
    LaunchedEffect(state.info) { if (state.info != null) onBack() }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LpmsLogo(Modifier.padding(bottom = 16.dp))
        LpmsCard {
            Text("Create account", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            state.error?.let { ErrorBanner(it); Spacer(Modifier.height(12.dp)) }
            OutlinedTextField(name, { name = it; clear() }, Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(email, { email = it; clear() }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it; clear() }, Modifier.fillMaxWidth(), label = { Text("Password") },
                supportingText = { Text("At least 8 characters, with a letter and a number") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            OutlinedTextField(confirm, { confirm = it; clear() }, Modifier.fillMaxWidth(), label = { Text("Confirm password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(Modifier.height(12.dp))
            Text("I am a...", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoleOption("Teacher", "Classroom educator", role == Role.TEACHER, Modifier.weight(1f)) { roleName = Role.TEACHER.name; clear() }
                RoleOption("Parent / Guardian", "Of a learner in the programme", role == Role.PARENT, Modifier.weight(1f)) { roleName = Role.PARENT.name; clear() }
            }
            Text(
                "Learners are enrolled by their coordinator, and coordinator and admin accounts are added by an administrator. " +
                    "An administrator approves every new account before you can sign in.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(12.dp))
            if (schools.isEmpty()) {
                Text("No schools have been added yet.", style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                Box {
                    OutlinedButton({ menuOpen = true }, Modifier.fillMaxWidth()) {
                        Text(if (school == null) "Choose your school" else "School: ${school.name}", color = Ink)
                    }
                    DropdownMenu(menuOpen, { menuOpen = false }) {
                        schools.forEach { sc -> DropdownMenuItem({ Text(sc.name) }, { schoolId = sc.id; menuOpen = false; clear() }) }
                    }
                }
            }
            if (role == Role.TEACHER) {
                Spacer(Modifier.height(12.dp))
                Text("Grade you teach", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                GradeChips(grade) { grade = it; clear() }
            }
            if (role == Role.PARENT) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(childName, { childName = it; clear() }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Your child's name") },
                    supportingText = { Text("So staff can link you to the right learner") })
            }
            Spacer(Modifier.height(16.dp))
            Button({ viewModel.register(name, email, password, confirm, role, school, grade, childName) },
                Modifier.fillMaxWidth(), enabled = !state.isLoading) {
                Text(if (state.isLoading) "Creating..." else "Register")
            }
            OutlinedButton(
                onClick = {
                    val err = viewModel.profileError(role, school, grade, childName)
                    if (err != null || role == null) viewModel.showError(err ?: "Choose Teacher or Parent / Guardian")
                    else scope.launch {
                        when (val r = requestGoogleIdToken(context)) {
                            is GoogleTokenResult.Token -> viewModel.googleSignUp(r.idToken, role, school, grade, childName)
                            is GoogleTokenResult.Error -> viewModel.showError(r.message)
                            GoogleTokenResult.Cancelled -> Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) { Text("Sign up with Google instead") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", color = Muted, style = MaterialTheme.typography.bodyMedium)
                TextButton(onBack) { Text("Sign in") }
            }
        }
    }
}
