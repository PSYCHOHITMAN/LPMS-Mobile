package com.pointblank.lpms.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pointblank.lpms.ui.AppViewModelProvider
import com.pointblank.lpms.ui.components.ErrorBanner
import com.pointblank.lpms.ui.components.InfoBanner
import com.pointblank.lpms.ui.components.LpmsCard
import com.pointblank.lpms.ui.components.LpmsLogo
import com.pointblank.lpms.ui.theme.Brand
import com.pointblank.lpms.ui.theme.Muted

@Composable
fun LoginScreen(
    onRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LpmsLogo(Modifier.padding(bottom = 24.dp))
        LpmsCard {
            Text("Welcome back", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            state.info?.let { InfoBanner(it); Spacer(Modifier.height(12.dp)) }
            state.error?.let { ErrorBanner(it); Spacer(Modifier.height(12.dp)) }
            OutlinedTextField(email, { email = it; viewModel.clearError() }, Modifier.fillMaxWidth(),
                label = { Text("Email") }, leadingIcon = { Icon(Icons.Filled.Email, null, tint = Brand) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(password, { password = it; viewModel.clearError() }, Modifier.fillMaxWidth(),
                label = { Text("Password") }, leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Brand) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(Modifier.height(16.dp))
            Button({ viewModel.login(email, password) }, Modifier.fillMaxWidth(), enabled = !state.isLoading) {
                Text(if (state.isLoading) "Signing in..." else "Sign in")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        when (val r = requestGoogleIdToken(context)) {
                            is GoogleTokenResult.Token -> viewModel.signInWithGoogle(r.idToken)
                            is GoogleTokenResult.Error -> viewModel.showError(r.message)
                            GoogleTokenResult.Cancelled -> Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) { Text("Continue with Google (single sign-on)") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account?", color = Muted, style = MaterialTheme.typography.bodyMedium)
                TextButton({ viewModel.clearError(); onRegister() }) { Text("Register here") }
            }
        }
    }
}
