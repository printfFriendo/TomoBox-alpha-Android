package com.example.tomobox

// Importações necessárias para interface (Compose), Notificações, Áudio e Rede
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

// Modelo de dados para as mensagens do chat
data class Message(val text: String, val isUser: Boolean, val id: String = UUID.randomUUID().toString())

class MainActivity : ComponentActivity() {

    // VARIÁVEIS DE ÁUDIO GLOBAIS: Declaradas aqui para o Android não apagá-las da memória (Garbage Collection)
    private var bgmPlayer: MediaPlayer? = null // Música de fundo "pensando"
    private var voicePlayer: MediaPlayer? = null // Voz da Fubuki

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o canal de notificações assim que o app abre
        createNotificationChannel(this)

        setContent {
            // Define as cores baseadas no tema do sistema (Dark Mode ou Light Mode)
            val darkTheme = isSystemInDarkTheme()
            val backgroundColor = if (darkTheme) Color(0xFF0D1117) else Color(0xFFF0F4F8)
            val primaryColor = if (darkTheme) Color(0xFF64B5F6) else Color(0xFF2196F3)

            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme(background = backgroundColor, primary = primaryColor)
                else lightColorScheme(background = backgroundColor, primary = primaryColor)
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Chama a tela principal passando as funções de controle de áudio da MainActivity
                    ChatScreen(
                        onStartBGM = { startBGM() },
                        onStopBGM = { stopBGM() },
                        onPlayVoice = { path -> playVoice(path) }
                    )
                }
            }
        }
    }

    // Gerencia a música de carregamento
    private fun startBGM() {
        try {
            if (bgmPlayer == null) {
                bgmPlayer = MediaPlayer.create(this, R.raw.loading_music)
                bgmPlayer?.setVolume(0.05f, 0.05f) // Volume baixo para não atrapalhar
                bgmPlayer?.isLooping = true
            }
            bgmPlayer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopBGM() {
        bgmPlayer?.stop()
        bgmPlayer?.release()
        bgmPlayer = null
    }

    // Função que toca a resposta de voz. Ela é "blindada" porque está na raiz da classe.
    private fun playVoice(path: String) {
        try {
            voicePlayer?.stop()
            voicePlayer?.release()
            voicePlayer = MediaPlayer().apply {
                setDataSource(path)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    release()
                    voicePlayer = null
                }
                prepareAsync()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // Ciclo de vida: Pausa a música de fundo se você sair do app
    override fun onStop() {
        super.onStop()
        bgmPlayer?.pause()
    }

    // Volta a música se o app for reaberto e ainda estiver processando
    override fun onRestart() {
        super.onRestart()
        bgmPlayer?.start()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onStartBGM: () -> Unit, onStopBGM: () -> Unit, onPlayVoice: (String) -> Unit) {
    // Estados Reativos: Quando mudam, a tela se atualiza sozinha
    val messages = remember { mutableStateListOf<Message>() }
    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var userAvatarPath by remember { mutableStateOf(getSavedAvatarPath(context)) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope() // Para rodar tarefas de rede sem travar a tela

    // Seletor de fotos da galeria
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val savedPath = saveAvatarUri(context, uri)
            if (savedPath != null) userAvatarPath = savedPath
        }
    }

    // Solicita permissão de notificação para Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Easter Egg: Clique no título para ver os créditos
                        Text("Tomobox", fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showCreditsDialog = true })
                        Text("IA Assistente • Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        // Fundo com gradiente vertical suave
        val chatBgBrush = if (isSystemInDarkTheme()) Brush.verticalGradient(listOf(Color(0xFF0D1117), Color(0xFF161B22)))
        else Brush.verticalGradient(listOf(Color(0xFFE3E8EE), Color(0xFFF4F7FA)))

        Column(modifier = Modifier.fillMaxSize().background(chatBgBrush).padding(innerPadding)) {
            // Lista de mensagens que cresce de baixo para cima
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message, userAvatarPath) { galleryLauncher.launch("image/*") }
                }
            }

            // Barra inferior de digitação
            Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (isProcessing) "IA trabalhando..." else "Escreva algo...") },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isProcessing) {
                                isProcessing = true
                                val currentText = inputText
                                inputText = ""
                                messages.add(Message(currentText, true))
                                messages.add(Message("...", false)) // Adiciona indicador de digitação
                                val loadingIndex = messages.size - 1

                                onStartBGM() // Inicia música de fundo

                                coroutineScope.launch {
                                    val response = fetchGroqResponse(currentText)
                                    val voiceUrl = fetchVoiceUrl(response)

                                    if (voiceUrl.startsWith("ERRO")) {
                                        onStopBGM()
                                        messages[loadingIndex] = Message(response, false)
                                        isProcessing = false
                                    } else {
                                        // Baixa o áudio gerado
                                        val (localFilePath, _) = downloadAudioFile(context, voiceUrl)

                                        onStopBGM()
                                        messages[loadingIndex] = Message(response, false)

                                        if (localFilePath != null) {
                                            // Toca a voz e envia a notificação silenciosa
                                            onPlayVoice(localFilePath)
                                            sendNotification(context, "Resposta pronta! 🦊", response)
                                        }
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) { Icon(Icons.Filled.Send, "Enviar") }
                }
            }
        }
    }

    // Janela flutuante dos Créditos (Easter Egg)
    if (showCreditsDialog) {
        Dialog(onDismissRequest = { showCreditsDialog = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text("Easter Egg! 🦊", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                        IconButton(onClick = { showCreditsDialog = false }, modifier = Modifier.align(Alignment.CenterEnd)) {
                            Icon(Icons.Filled.Close, "Fechar")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Tomobox Alpha v0.1", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Idealizado e desenvolvido por Diogo Neves Ribeiro.", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// Design dos balões de chat (Usuário vs IA)
@Composable
fun ChatBubble(message: Message, userAvatarPath: String?, onAvatarClick: () -> Unit) {
    val isUser = message.isUser
    val darkTheme = isSystemInDarkTheme()
    val userBrush = if (darkTheme) Brush.horizontalGradient(listOf(Color(0xFF1976D2), Color(0xFF0D47A1))) else Brush.horizontalGradient(listOf(Color(0xFF42A5F5), Color(0xFF1E88E5)))
    val botBgColor = if (darkTheme) Color(0xFF263238) else Color(0xFFFFFFFF)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!isUser) {
            Image(painterResource(R.drawable.fubuki_avatar), null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(8.dp))
        }
        Box(modifier = Modifier.widthIn(max = 260.dp).background(if (isUser) userBrush else Brush.horizontalGradient(listOf(botBgColor, botBgColor)), RoundedCornerShape(12.dp)).padding(12.dp)) {
            if (message.text == "...") TypingIndicator(if (darkTheme) Color.White else Color(0xFF212121))
            else Text(message.text, color = if (isUser) Color.White else if (darkTheme) Color.White else Color(0xFF212121))
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            if (userAvatarPath != null) AsyncImage(File(userAvatarPath), null, Modifier.size(40.dp).clip(CircleShape).clickable { onAvatarClick() }, contentScale = ContentScale.Crop)
            else Icon(Icons.Default.Person, null, Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { onAvatarClick() }.padding(8.dp), tint = Color.White)
        }
    }
}

// Animação das bolinhas de "digitando"
@Composable
fun TypingIndicator(color: Color) {
    val alpha by rememberInfiniteTransition().animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
    Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = alpha))) }
    }
}

// Configuração do Canal de Notificação (Obrigatório para Android Moderno)
fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel("tomobox_channel_silent", "Tomobox Respostas", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null) // Garante que a notificação não roube o áudio da voz
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

// Função que dispara a notificação no topo do celular
fun sendNotification(context: Context, title: String, message: String) {
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

    val notification = NotificationCompat.Builder(context, "tomobox_channel_silent")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setSilent(true) // Notificação apenas visual para não interromper a voz
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(1, notification)
}

// Funções para salvar e recuperar o caminho da foto de perfil no celular
fun saveAvatarUri(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "user_avatar.jpg")
        inputStream?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        context.getSharedPreferences("TomoboxPrefs", Context.MODE_PRIVATE).edit().putString("avatar_path", file.absolutePath).apply()
        file.absolutePath
    } catch (e: Exception) { null }
}

fun getSavedAvatarPath(context: Context): String? = context.getSharedPreferences("TomoboxPrefs", Context.MODE_PRIVATE).getString("avatar_path", null)

// COMUNICAÇÃO COM O GROQ (Cérebro da IA)
suspend fun fetchGroqResponse(prompt: String): String = withContext(Dispatchers.IO) {
    val url = "https://api.groq.com/openai/v1/chat/completions"
    val json = JSONObject().apply {
        put("model", "llama-3.1-8b-instant")
        put("messages", org.json.JSONArray().put(JSONObject().apply {
            put("role", "system")
            put("content", "Você é uma assistente virtual prestativa e direta. Responda de forma natural.")
        }).put(JSONObject().apply { put("role", "user"); put("content", prompt) }))
    }.toString()

    val request = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Bearer DIGITE_AQUI_SUA_CHAVE_API_GROQ")
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

    try {
        val res = OkHttpClient().newCall(request).execute().body?.string()
        JSONObject(res).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    } catch (e: Exception) { "Erro na conexão com a IA." }
}

// BUSCA O LINK DA VOZ NO HUGGING FACE
suspend fun fetchVoiceUrl(text: String): String = withContext(Dispatchers.IO) {
    val json = JSONObject().apply { put("text", text) }.toString()
    val request = Request.Builder()
        .url("https://diofriendo-tomobox-voice-api.hf.space/api/gerar_voz")
        // Se sua API do Hugging Face exigir chave, adicione abaixo:
        // .addHeader("Authorization", "Bearer DIGITE_AQUI_SUA_CHAVE_API_HUGGINFACE")
        .post(json.toRequestBody("application/json".toMediaType()))
        .build()

    try {
        val res = OkHttpClient.Builder().readTimeout(180, TimeUnit.SECONDS).build().newCall(request).execute().body?.string()
        JSONObject(res).getString("url")
    } catch (e: Exception) { "ERRO" }
}

// FAZ O DOWNLOAD DO ARQUIVO .WAV PARA O CACHE DO CELULAR
suspend fun downloadAudioFile(context: Context, url: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        val res = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
        val file = File(context.cacheDir, "voice.wav")
        res.body?.byteStream()?.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        Pair(file.absolutePath, null)
    } catch (e: Exception) { Pair(null, "Erro no download") }
}