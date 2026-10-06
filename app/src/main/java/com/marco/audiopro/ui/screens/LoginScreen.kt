package com.marco.audiopro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.audiopro.container
import com.marco.audiopro.data.subsonic.SubsonicApi
import com.marco.audiopro.ui.theme.Hifi
import com.marco.audiopro.ui.theme.Mono
import com.marco.audiopro.ui.viz.Visualizer
import com.marco.audiopro.ui.viz.VizMode
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Visualizer(VizMode.SPECTRUM, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(20.dp))
        Text("MARCO", style = Mono.copy(color = Hifi.Amber, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp))
        Text("AUDIO PRO", style = Mono.copy(color = Hifi.Cream, fontSize = 14.sp, letterSpacing = 8.sp))
        Spacer(Modifier.height(8.dp))
        Text("Conecta tu servidor Navidrome", color = Hifi.TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            url, { url = it }, label = { Text("Servidor") }, placeholder = { Text("https://musica.midominio.com") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(user, { user = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Hifi.LedRed, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    val acc = SubsonicApi.login(url, user, pass)
                    runCatching { c.api.ping(acc) }
                        .onSuccess { c.settings.saveAccount(acc) }
                        .onFailure { error = "No se pudo conectar: ${it.message}" }
                    busy = false
                }
            },
            enabled = !busy && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Hifi.Amber, contentColor = androidx.compose.ui.graphics.Color.Black),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.Black)
            else Text("CONECTAR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Para escuchar fuera de casa, tu servidor debe ser accesible desde internet " +
                "(dominio con HTTPS, reverse proxy o una VPN como Tailscale).",
            color = Hifi.TextFaint, fontSize = 12.sp,
        )
    }
}
