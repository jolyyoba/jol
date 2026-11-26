package dev.stationcall.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DefaultStationName = "Станция 2"
private const val MaxStationNameLength = 48

class MainActivity : ComponentActivity() {
    private lateinit var repository: StationRepository
    private var station by mutableStateOf<Station?>(null)
    private var handoffError by mutableStateOf<CallHandoffResult?>(null)
    private var shortcutNeedsSetup by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = StationRepository(this)
        station = repository.load()
        readShortcutResult(intent)

        setContent {
            StationTheme {
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val showMessage: (String) -> Unit = { message ->
                    scope.launch { snackbar.showSnackbar(message) }
                }

                StationScreen(
                    station = station,
                    snackbar = snackbar,
                    canPinShortcut = StationShortcut.isSupported(this),
                    shortcutNeedsSetup = shortcutNeedsSetup,
                    onShortcutSetupOpened = { shortcutNeedsSetup = false },
                    onCall = { station?.let(::callStation) },
                    onSaveStation = { saved ->
                        repository.save(saved)
                        station = saved
                    },
                    onPinShortcut = {
                        val message = if (StationShortcut.requestPin(this)) {
                            "Подтвердите добавление ярлыка на рабочий стол"
                        } else {
                            "Не удалось добавить ярлык"
                        }
                        showMessage(message)
                    },
                    onWidgetHelp = {
                        if (!openHelp(YandexWidgetHelpUrl)) {
                            showMessage("Не удалось открыть справку")
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShortcutResult(intent)
    }

    private fun readShortcutResult(intent: Intent) {
        val result = CallHandoffResult.entries.firstOrNull {
            it.name == intent.getStringExtra(ShortcutFailureExtra)
        }
        intent.removeExtra(ShortcutFailureExtra)
        when (result) {
            CallHandoffResult.STATION_NOT_CONFIGURED -> shortcutNeedsSetup = true
            CallHandoffResult.OPENED, null -> Unit
            else -> handoffError = result
        }
    }

    private fun callStation(station: Station) {
        val result = StationCall.launch(this, station.deviceId)
        if (result != CallHandoffResult.OPENED) handoffError = result
    }

    private fun openHelp(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    @Composable
    private fun StationScreen(
        station: Station?,
        snackbar: SnackbarHostState,
        canPinShortcut: Boolean,
        shortcutNeedsSetup: Boolean,
        onShortcutSetupOpened: () -> Unit,
        onCall: () -> Unit,
        onSaveStation: (Station) -> Unit,
        onPinShortcut: () -> Unit,
        onWidgetHelp: () -> Unit,
    ) {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(shortcutNeedsSetup) {
            if (shortcutNeedsSetup) {
                showSettings = true
                onShortcutSetupOpened()
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { insets ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets),
                contentAlignment = Alignment.TopCenter,
            ) {
                val wide = maxWidth >= 680.dp || (maxWidth >= 580.dp && maxHeight < 480.dp)
                val artHeight = if (wide) 272.dp else (maxHeight * 0.40f).coerceIn(170.dp, 328.dp)
                val sideMargin = if (maxWidth < 360.dp) 20.dp else 24.dp

                Column(
                    modifier = Modifier
                        .widthIn(max = if (wide) 872.dp else 464.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = sideMargin)
                        .padding(bottom = 20.dp),
                ) {
                    HomeHeader(onSettings = { showSettings = true })
                    if (wide) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StationArtwork(artHeight, Modifier.weight(1f))
                            Column(Modifier.weight(1f)) {
                                StationTitle(station)
                                Spacer(Modifier.height(28.dp))
                                StationActions(
                                    configured = station != null,
                                    canPinShortcut = canPinShortcut,
                                    onPrimary = if (station == null) ({ showSettings = true }) else onCall,
                                    onPinShortcut = onPinShortcut,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(20.dp))
                        StationTitle(station)
                        Spacer(Modifier.height(20.dp))
                        StationArtwork(artHeight)
                        Spacer(Modifier.height(24.dp))
                        StationActions(
                            configured = station != null,
                            canPinShortcut = canPinShortcut,
                            onPrimary = if (station == null) ({ showSettings = true }) else onCall,
                            onPinShortcut = onPinShortcut,
                        )
                    }
                }
            }
        }

        if (showSettings) {
            StationSettings(
                station = station,
                onDismiss = { showSettings = false },
                onSave = { saved ->
                    onSaveStation(saved)
                    showSettings = false
                },
                onWidgetHelp = onWidgetHelp,
            )
        }

        handoffError?.let { error ->
            CallErrorDialog(
                error = error,
                onDismiss = { handoffError = null },
                onGetYandex = {
                    if (openHelp(YandexAppDownloadUrl)) handoffError = null
                },
                onWidgetHelp = {
                    handoffError = null
                    onWidgetHelp()
                },
                onSettings = {
                    handoffError = null
                    showSettings = true
                },
            )
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_station_launcher),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Звонок домой",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        FilledTonalIconButton(
            onClick = onSettings,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Настройки Станции",
            )
        }
    }
}

@Composable
private fun StationTitle(station: Station?) {
    Text(
        text = station?.name ?: DefaultStationName,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (station == null) "Настройте звонок на домашнюю колонку"
        else "Звонок на Яндекс Станцию",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun StationArtwork(height: Dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(
                RoundedCornerShape(
                    topStart = 40.dp,
                    topEnd = 40.dp,
                    bottomEnd = 82.dp,
                    bottomStart = 40.dp,
                ),
            )
            .background(colors.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = colors.onSecondaryContainer.copy(alpha = 0.075f),
                radius = size.minDimension * 0.425f,
                center = Offset(size.width * 0.46f, size.height * 0.47f),
            )
        }
        Image(
            painter = painterResource(R.drawable.station_artwork),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(height * 0.84f),
        )
    }
}

@Composable
private fun StationActions(
    configured: Boolean,
    canPinShortcut: Boolean,
    onPrimary: () -> Unit,
    onPinShortcut: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp),
            shapes = ButtonShapes(
                shape = RoundedCornerShape(40.dp),
                pressedShape = RoundedCornerShape(19.dp),
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_phone),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (configured) "Позвонить" else "Настроить звонок",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (configured) "Вызов откроется в приложении Яндекса"
            else "Понадобится идентификатор Станции",
            modifier = Modifier.padding(horizontal = 10.dp),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (configured && canPinShortcut) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onPinShortcut,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shortcut),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Добавить ярлык на экран", textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationSettings(
    station: Station?,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit,
    onWidgetHelp: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(station?.name ?: DefaultStationName) }
    var deviceId by rememberSaveable { mutableStateOf(station?.deviceId.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val invalidId = submitted && !isValidStationDeviceId(deviceId)
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        submitted = true
        if (isValidStationDeviceId(deviceId)) {
            keyboard?.hide()
            onSave(Station(name.trim().ifBlank { DefaultStationName }, deviceId.trim()))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Настройки Станции",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "В «Доме с Алисой» откройте колонку → «Помощь» → «Об устройстве» и скопируйте идентификатор.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MaxStationNameLength) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название колонки") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = deviceId,
                onValueChange = { deviceId = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Идентификатор устройства") },
                singleLine = true,
                isError = invalidId,
                supportingText = if (invalidId) {
                    { Text("Вставьте идентификатор из раздела «Об устройстве».") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text("Сохранить")
            }
            TextButton(
                onClick = onWidgetHelp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp),
            ) {
                Text("Как добавить виджет Яндекса", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CallErrorDialog(
    error: CallHandoffResult,
    onDismiss: () -> Unit,
    onGetYandex: () -> Unit,
    onWidgetHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    val title: String
    val message: String
    val action: String
    val onAction: () -> Unit
    when (error) {
        CallHandoffResult.YANDEX_UNAVAILABLE -> {
            title = "Нужно приложение Яндекса"
            message = "Установите или обновите Яндекс, войдите в аккаунт Станции и повторите вызов."
            action = "Открыть страницу"
            onAction = onGetYandex
        }
        CallHandoffResult.INVALID_DEVICE_ID, CallHandoffResult.STATION_NOT_CONFIGURED -> {
            title = "Проверьте идентификатор"
            message = "Скопируйте идентификатор Станции из «Дома с Алисой» и вставьте в настройки."
            action = "Настройки"
            onAction = onSettings
        }
        CallHandoffResult.FAILED -> {
            title = "Не удалось открыть вызов"
            message = "Попробуйте обновить приложение Яндекса. Позвонить также можно через его штатный виджет."
            action = "О виджете"
            onAction = onWidgetHelp
        }
        CallHandoffResult.OPENED -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
        confirmButton = { TextButton(onClick = onAction) { Text(action) } },
    )
}
