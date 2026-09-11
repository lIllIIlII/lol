package com.yunx.app.ui.login

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import com.yunx.app.ui.SnackbarController
import com.yunx.app.ui.rememberGlobalSnackbarHostState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunx.app.ui.viewmodel.XunleiAccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XunleiLoginScreen(
    viewModel: XunleiAccountViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onVerify: (url: String, deviceId: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val step = viewModel.loginStep
    val error = viewModel.loginError
    val smsSent = viewModel.smsSent
    val account by viewModel.xunleiAccount.collectAsState()

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var smsCode by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            SnackbarController.show(it)
            viewModel.consumeLoginError()
        }
    }
    LaunchedEffect(account) {
        if (account != null) onSaved()
    }

    BackHandler { onBack() }

    val snackbarHostState = rememberGlobalSnackbarHostState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("迅雷网盘登录", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (step?.needSms == true) "短信验证" else "登录迅雷网盘",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            Text(
                text = if (step?.needSms == true) {
                    if (smsSent) "账号密码登录触发安全验证，验证码已发送至 ${username}"
                    else "账号密码登录触发安全验证，请点击下方「发送验证码」"
                } else {
                    "使用迅雷账号登录，支持解析与下载分享文件"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (step == null || !step.needSms) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("手机号 / 邮箱") },
                    leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (passwordVisible) KeyboardType.Text else KeyboardType.Password),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                Button(
                    onClick = { viewModel.login(username, password) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = username.isNotBlank() && password.isNotBlank() && !isSending
                ) { Text("登录") }
            } else {
                OutlinedTextField(
                    value = smsCode,
                    onValueChange = { smsCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("短信验证码") },
                    leadingIcon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                Button(
                    onClick = {
                        isSending = true
                        viewModel.loginWithSms(
                            username, smsCode,
                            step.smsCreditKey, step.smsToken
                        )
                        isSending = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = smsCode.isNotBlank()
                ) { Text("验证并登录") }
                if (!smsSent) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.sendSms(username)
                            SnackbarController.show("验证码已发送")
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("发送验证码") }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.sendSms(username)
                            SnackbarController.show("验证码已发送")
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("重新发送验证码") }
                }
                Text(
                    text = "若始终收不到短信，请确认手机号正确，或稍后重试 / 切换网络",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                if (step.reviewUrl.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onVerify(step.reviewUrl, com.yunx.app.data.network.XunleiDeviceFingerprint.deviceSign())
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "短信收不到？应用内验证",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "应用内完成验证后，将自动重新登录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://i.xunlei.com/xluser/validate/findpwd_acc.html")
                            )
                        )
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "未设置密码，点我前往设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
