package com.almi.ai.ui.tryon

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.almi.ai.data.repository.MotionDirection
import com.almi.ai.data.repository.VideoGenerationStatus
import com.almi.ai.ui.components.ConnectionPill
import com.almi.ai.ui.components.DimensionCard
import java.io.File

private enum class EditTarget { NONE, PERSON, PRODUCT }

@Composable
fun FittingRoomScreen(
    viewModel: TryOnViewModel,
    language: String,
    onOpenAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var editTarget by remember { mutableStateOf(EditTarget.NONE) }

    val personPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.setPersonImage(it.toString())
            editTarget = EditTarget.NONE
        }
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.setGarmentImage(it.toString())
            editTarget = EditTarget.NONE
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingCameraUri?.let { viewModel.setPersonImage(it.toString()) }
            editTarget = EditTarget.NONE
        }
    }

    if (state.generatedImage != null) {
        ResultRoom(
            state = state,
            language = language,
            onBack = viewModel::returnToStudio,
            onMotion = viewModel::setMotion,
            onVideo = viewModel::generateVideo,
            onReset = viewModel::reset,
            onOpenAi = onOpenAi,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EclipseHeader(language)

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (language == "ar") "شوف اللوك قبل ما تلبسه" else "See the look before you wear it",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (language == "ar") "صورتك. منتجك. نتيجة واحدة واقعية." else "Your photo. Your item. One realistic result.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EclipseStage(
            state = state,
            language = language,
            selectedTarget = editTarget,
            onSelectPerson = { editTarget = if (editTarget == EditTarget.PERSON) EditTarget.NONE else EditTarget.PERSON },
            onSelectProduct = { editTarget = if (editTarget == EditTarget.PRODUCT) EditTarget.NONE else EditTarget.PRODUCT },
        )

        AnimatedVisibility(
            visible = editTarget == EditTarget.PERSON,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            PersonSource(
                language = language,
                onCamera = {
                    createCameraUri(context)?.let {
                        pendingCameraUri = it
                        cameraLauncher.launch(it)
                    }
                },
                onGallery = { personPicker.launch(arrayOf("image/*")) },
            )
        }

        AnimatedVisibility(
            visible = editTarget == EditTarget.PRODUCT,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ProductSource(
                state = state,
                language = language,
                onUrlChanged = viewModel::setProductUrl,
                onRead = viewModel::loadProduct,
                onUpload = { garmentPicker.launch(arrayOf("image/*")) },
            )
        }

        PrivacyLock(language)

        GeneratePanel(
            state = state,
            language = language,
            onGenerate = viewModel::generateImage,
            onOpenAi = onOpenAi,
        )

        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun EclipseHeader(language: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                "FITTING ROOM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ConnectionPill(if (language == "ar") "Autopilot جاهز" else "Autopilot ready")
    }
}

@Composable
private fun EclipseStage(
    state: TryOnUiState,
    language: String,
    selectedTarget: EditTarget,
    onSelectPerson: () -> Unit,
    onSelectProduct: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    DimensionCard(emphasized = selectedTarget != EditTarget.NONE) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(430.dp)
                .padding(12.dp),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width * 0.56f, size.height * 0.48f)
                val ring = size.minDimension * 0.37f
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            scheme.primary.copy(alpha = 0.16f),
                            scheme.primary.copy(alpha = 0.88f),
                            scheme.secondary.copy(alpha = 0.72f),
                            scheme.primary.copy(alpha = 0.16f),
                        ),
                        center = center,
                    ),
                    radius = ring,
                    center = center,
                    style = Stroke(width = 3f),
                )
                drawCircle(scheme.primary.copy(alpha = 0.05f), ring * 0.82f, center)
                drawOval(
                    color = scheme.primary.copy(alpha = 0.14f),
                    topLeft = Offset(size.width * 0.25f, size.height * 0.84f),
                    size = Size(size.width * 0.62f, 34f),
                )
            }

            PersonPortal(
                image = state.personImage,
                language = language,
                selected = selectedTarget == EditTarget.PERSON,
                onClick = onSelectPerson,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.69f)
                    .height(340.dp),
            )

            ProductPortal(
                image = state.effectiveGarmentImage,
                language = language,
                selected = selectedTarget == EditTarget.PRODUCT,
                onClick = onSelectProduct,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 4.dp, y = (-12).dp)
                    .width(128.dp),
            )

            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = RoundedCornerShape(999.dp),
                color = scheme.surface.copy(alpha = 0.76f),
                border = BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Text(
                    if (language == "ar") "اضغط لتعديل" else "Tap to edit",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonPortal(
    image: String?,
    language: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scale by animateFloatAsState(if (selected) 1.035f else 1f, label = "person-portal")
    Column(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (language == "ar") "صورتك" else "You", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (image != null) StatusCheck(selected)
        }
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            shadowElevation = if (selected) 18.dp else 8.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    EmptyPortal(Icons.Outlined.PhotoCamera, if (language == "ar") "ابدأ بصورتك" else "Start with your photo")
                }
            }
        }
    }
}

@Composable
private fun ProductPortal(
    image: String?,
    language: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "product-portal")
    Column(modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (language == "ar") "المنتج" else "Item", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (image != null) StatusCheck(selected)
        }
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
            ),
            shadowElevation = 16.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    EmptyPortal(Icons.Outlined.Image, if (language == "ar") "اختر" else "Select")
                }
            }
        }
    }
}

@Composable
private fun StatusCheck(active: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun EmptyPortal(icon: ImageVector, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
        }
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonSource(language: String, onCamera: () -> Unit, onGallery: () -> Unit) {
    DimensionCard(emphasized = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (language == "ar") "اختر مصدر صورتك" else "Choose your photo source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceButton(Icons.Outlined.PhotoCamera, if (language == "ar") "الكاميرا" else "Camera", onCamera, true, Modifier.weight(1f))
                SourceButton(Icons.Outlined.PhotoLibrary, if (language == "ar") "المعرض" else "Gallery", onGallery, false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SourceButton(icon: ImageVector, label: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier) {
    if (primary) {
        Button(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(17.dp)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(17.dp)) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProductSource(
    state: TryOnUiState,
    language: String,
    onUrlChanged: (String) -> Unit,
    onRead: () -> Unit,
    onUpload: () -> Unit,
) {
    DimensionCard(emphasized = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (language == "ar") "أدخل المنتج بطريقتك" else "Bring in your item", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = state.productUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (language == "ar") "الصق رابط المنتج" else "Paste product link") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRead,
                    enabled = !state.isLoadingProduct,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    if (state.isLoadingProduct) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Link, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ar") "استيراد" else "Import", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onUpload, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(17.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ar") "صورة" else "Image", fontWeight = FontWeight.Bold)
                }
            }
            if (state.productTitle.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(state.productTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.merchant.isNotBlank()) Text(state.merchant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.displayProductPrice.isNotBlank()) Text(state.displayProductPrice, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            ProductErrorText(state.productError, language)
        }
    }
}

@Composable
private fun PrivacyLock(language: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            Text(
                if (language == "ar") "Privacy Lock • صورك تبقى على جهازك حتى تبدأ التوليد" else "Privacy Lock • images stay local until generation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductErrorText(error: ProductError, language: String) {
    val text = when (error) {
        ProductError.EMPTY_URL -> if (language == "ar") "أدخل رابط المنتج" else "Enter a product link"
        ProductError.UNAVAILABLE -> if (language == "ar") "تعذر قراءة الرابط" else "Could not read the link"
        ProductError.IMAGE_NOT_FOUND -> if (language == "ar") "لم أجد صورة مناسبة" else "No usable image found"
        ProductError.NONE -> null
    } ?: return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun GeneratePanel(state: TryOnUiState, language: String, onGenerate: () -> Unit, onOpenAi: () -> Unit) {
    if (state.isGeneratingImage) {
        DimensionCard(emphasized = true) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                val percent = (state.imageProgress.coerceIn(0f, 1f) * 100f).toInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (language == "ar") "ALMI يصنع الإطلالة" else "ALMI is building your look", fontWeight = FontWeight.Bold)
                    Text("$percent%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                }
                LinearProgressIndicator(progress = { state.imageProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape))
            }
        }
    } else {
        EclipsePrimaryAction(
            text = when {
                state.canGenerate -> if (language == "ar") "جرّبها عليك" else "Try it on"
                state.personImage == null -> if (language == "ar") "أضف صورتك أولًا" else "Add your photo first"
                state.effectiveGarmentImage == null -> if (language == "ar") "اختر المنتج" else "Choose an item"
                else -> if (language == "ar") "جرّبها عليك" else "Try it on"
            },
            enabled = state.canGenerate,
            onClick = onGenerate,
        )
    }

    when (state.imageError) {
        GenerationError.API_KEY_MISSING -> ErrorAction(if (language == "ar") "إعداد الذكاء الاصطناعي مطلوب" else "AI setup required", onOpenAi)
        GenerationError.REQUEST_FAILED -> ErrorAction(if (language == "ar") "تعذر التوليد • غيّر الموديل أو المزود" else "Generation failed • change model/provider", onOpenAi)
        GenerationError.NONE -> Unit
    }
}

@Composable
private fun EclipsePrimaryAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.42f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = alpha), MaterialTheme.colorScheme.secondary.copy(alpha = alpha))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White)
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ErrorAction(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun ResultRoom(
    state: TryOnUiState,
    language: String,
    onBack: () -> Unit,
    onMotion: (MotionDirection) -> Unit,
    onVideo: () -> Unit,
    onReset: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val generated = state.generatedImage ?: return
    var showBefore by remember(generated) { mutableStateOf(false) }
    val displayImage = if (showBefore) state.personImage ?: generated else generated

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(if (language == "ar") "الإطلالة جاهزة" else "Your look is ready", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("ALMI RESULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ResultIcon(Icons.Outlined.Edit, onBack)
                ResultIcon(Icons.Outlined.Tune, onOpenAi)
                ResultIcon(Icons.Outlined.Refresh, onReset)
            }
        }

        DimensionCard(emphasized = true) {
            Box {
                AsyncImage(
                    model = displayImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(30.dp)),
                    contentScale = ContentScale.Crop,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    onClick = { showBefore = !showBefore },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.52f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                ) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Compare, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Text(
                            if (showBefore) { if (language == "ar") "قبل" else "Before" } else { if (language == "ar") "بعد" else "After" },
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        DimensionCard {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotionButton(MotionDirection.TURN, state.motion, if (language == "ar") "دوران" else "Turn", onMotion, Modifier.weight(1f))
                    MotionButton(MotionDirection.WALK, state.motion, if (language == "ar") "مشي" else "Walk", onMotion, Modifier.weight(1f))
                    MotionButton(MotionDirection.DETAIL, state.motion, if (language == "ar") "تفاصيل" else "Detail", onMotion, Modifier.weight(1f))
                }
                Button(
                    onClick = onVideo,
                    enabled = !state.isGeneratingVideo,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    if (state.isGeneratingVideo) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(videoStatusText(state.videoStatus, language))
                    } else {
                        Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (language == "ar") "حوّل الإطلالة إلى فيديو" else "Turn this look into video", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        state.generatedVideo?.let { VideoCard(it, language) }
        if (state.videoError) Text(if (language == "ar") "تعذر إنشاء الفيديو" else "Video generation failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ResultIcon(icon: ImageVector, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun MotionButton(direction: MotionDirection, selected: MotionDirection, label: String, onClick: (MotionDirection) -> Unit, modifier: Modifier) {
    if (direction == selected) {
        Button(onClick = { onClick(direction) }, modifier = modifier.height(48.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier.height(48.dp), shape = RoundedCornerShape(15.dp)) { Text(label) }
    }
}

private fun videoStatusText(status: VideoGenerationStatus, language: String): String = when (status) {
    VideoGenerationStatus.SUBMITTING -> if (language == "ar") "إرسال" else "Submitting"
    VideoGenerationStatus.PROCESSING -> if (language == "ar") "معالجة" else "Processing"
    VideoGenerationStatus.DOWNLOADING -> if (language == "ar") "تنزيل" else "Downloading"
    VideoGenerationStatus.IDLE -> if (language == "ar") "فيديو" else "Video"
}

@Composable
private fun VideoCard(uri: String, language: String) {
    DimensionCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (language == "ar") "الفيديو جاهز" else "Video ready", fontWeight = FontWeight.Bold)
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse(uri))
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                    }
                },
                update = { view ->
                    if (view.tag != uri) {
                        view.tag = uri
                        view.setVideoURI(Uri.parse(uri))
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)),
            )
        }
    }
}

private fun createCameraUri(context: Context): Uri? = runCatching {
    val directory = File(context.filesDir, "tryon_camera").apply { mkdirs() }
    val file = File(directory, "person_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
