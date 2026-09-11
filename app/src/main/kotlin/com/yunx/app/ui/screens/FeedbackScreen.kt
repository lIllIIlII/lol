package com.yunx.app.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yunx.app.R
import com.yunx.app.ui.SnackbarController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedWechat by remember { mutableStateOf(false) }
    var savedQq by remember { mutableStateOf(false) }
    var pendingPermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingPermission?.complete(granted)
        pendingPermission = null
    }
    BackHandler { onBack() }

    suspend fun ensureStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) return true
        val deferred = CompletableDeferred<Boolean>()
        pendingPermission = deferred
        withContext(Dispatchers.Main) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return deferred.await()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("反馈联系", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.SupportAgent,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "有问题？直接找开发者",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "扫二维码加好友，把问题发过来即可",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            ContactQrCard(
                title = "微信好友",
                drawableRes = R.drawable.wechat_friend_qr,
                contentDescription = "开发者微信好友二维码",
                hint = "微信「扫一扫」或长按识别二维码加好友",
                saved = savedWechat,
                onSave = {
                    scope.launch {
                        if (!ensureStoragePermission()) {
                            SnackbarController.show("未授予存储权限，无法保存到相册")
                            return@launch
                        }
                        val ok = withContext(Dispatchers.IO) {
                            saveQrToGallery(context, R.drawable.wechat_friend_qr, "yunx_wechat_friend_qr")
                        }
                        if (ok) savedWechat = true
                        SnackbarController.show(if (ok) "已保存到相册（Pictures/XiXiAt）" else "保存失败")
                    }
                }
            )

            ContactQrCard(
                title = "QQ 好友",
                drawableRes = R.drawable.qq_friend_qr,
                contentDescription = "开发者QQ好友二维码",
                hint = "QQ「扫一扫」扫描二维码加好友",
                saved = savedQq,
                onSave = {
                    scope.launch {
                        if (!ensureStoragePermission()) {
                            SnackbarController.show("未授予存储权限，无法保存到相册")
                            return@launch
                        }
                        val ok = withContext(Dispatchers.IO) {
                            saveQrToGallery(context, R.drawable.qq_friend_qr, "yunx_qq_friend_qr")
                        }
                        if (ok) savedQq = true
                        SnackbarController.show(if (ok) "已保存到相册（Pictures/XiXiAt）" else "保存失败")
                    }
                }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "描述问题时请尽量附上：分享链接、出现界面、报错提示与操作步骤，" +
                            "开发者会尽快回复。需要详细排查时可先在「设置 → 导出日志」导出已脱敏的日志文件，" +
                            "加好友后直接发送即可。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 21.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactQrCard(
    title: String,
    drawableRes: Int,
    contentDescription: String,
    hint: String,
    saved: Boolean,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp
            ) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentScale = ContentScale.FillWidth
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (saved) "已保存到相册" else "保存到相册")
            }
        }
    }
}

private fun saveQrToGallery(context: Context, drawableRes: Int, namePrefix: String): Boolean = runCatching {
    val bitmap: Bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
        ?: return@runCatching false
    val fileName = "${namePrefix}_${System.currentTimeMillis()}.jpg"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XiXiAt")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: return@runCatching false
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null
        )
        true
    } else {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        true
    }
}.getOrDefault(false)
