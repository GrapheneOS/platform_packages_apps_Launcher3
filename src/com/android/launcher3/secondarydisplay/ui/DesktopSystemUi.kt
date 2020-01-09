package com.android.launcher3.secondarydisplay.ui

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.model.data.AppInfo

// --- ViewModel ---
class DesktopViewModel {
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
    var isStartMenuOpen by mutableStateOf(false)
    var onAppClick: ((AppInfo) -> Unit)? = null
    var onIconLoad: ((AppInfo) -> Bitmap)? = null
}

// --- Bridge for Java ---
// Java cannot call Composable functions directly easily.
fun setDesktopContent(composeView: ComposeView, viewModel: DesktopViewModel, legacyView: View) {
    composeView.setContent {
        DesktopSystemUi(viewModel, legacyView)
    }
}

@Composable
fun DesktopSystemUi(
    viewModel: DesktopViewModel,
    legacyWorkspaceView: View
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) { // Black background for desktop
            
            // 1. The Desktop Workspace (Legacy View wrapped)
            AndroidView(
                factory = { legacyWorkspaceView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 64.dp)
            )

            // 2. Start Menu
            AnimatedVisibility(
                visible = viewModel.isStartMenuOpen,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 70.dp, start = 12.dp),
                enter = slideInVertically(initialOffsetY = { 100 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { 100 }) + fadeOut()
            ) {
                StartMenu(viewModel)
            }

            // 3. Taskbar
            DesktopTaskbar(
                modifier = Modifier.align(Alignment.BottomCenter),
                isStartOpen = viewModel.isStartMenuOpen,
                onStartToggle = { viewModel.isStartMenuOpen = !viewModel.isStartMenuOpen }
            )
        }
    }
}

@Composable
fun DesktopTaskbar(
    modifier: Modifier = Modifier,
    isStartOpen: Boolean,
    onStartToggle: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(64.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = onStartToggle,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isStartOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Start")
                }
            }
            // Clock
            Text(
                text = java.text.SimpleDateFormat("HH:mm").format(java.util.Date()),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun StartMenu(viewModel: DesktopViewModel) {
    Card(
        modifier = Modifier.width(360.dp).height(500.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                placeholder = { Text("Search apps...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 64.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.apps) { app ->
                    AppIconItem(app, viewModel)
                }
            }
        }
    }
}

@Composable
fun AppIconItem(app: AppInfo, viewModel: DesktopViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { 
                viewModel.onAppClick?.invoke(app)
                // Close menu on launch
                viewModel.isStartMenuOpen = false 
            }
            .padding(8.dp)
    ) {
        val bitmap = remember(app) { viewModel.onIconLoad?.invoke(app) }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(Modifier.size(48.dp).background(Color.Gray))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.title.toString(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
