package com.lifeos.assistant.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeos.assistant.data.ModelInfo
import com.lifeos.assistant.data.ModelRepository
import com.lifeos.assistant.data.ModelType
import com.lifeos.assistant.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * ModelManager - UI component for managing AI models
 * 
 * Features:
 * - Display current loaded model
 * - List all available models
 * - Add new models from storage
 * - Delete models
 * - Show storage usage
 */
@Composable
fun ModelManager(
    modelRepository: ModelRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // State
    val models by modelRepository.models.collectAsState()
    val currentModel by modelRepository.currentModel.collectAsState()
    val isLoading by modelRepository.isLoading.collectAsState()
    val storageUsage by modelRepository.storageUsage.collectAsState()
    
    // Dialog states
    var showDeleteDialog by remember { mutableStateOf<ModelInfo?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    modelRepository.importModel(it)
                } catch (e: Exception) {
                    showErrorDialog = e.message
                }
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            text = "Model Management",
            style = MaterialTheme.typography.titleLarge,
            color = OnSurface
        )
        
        // Current Model Card
        CurrentModelCard(
            model = currentModel,
            isLoading = isLoading,
            onUnload = {
                scope.launch { modelRepository.unloadCurrentModel() }
            }
        )
        
        // Storage Usage
        StorageUsageCard(usage = storageUsage)
        
        // Add Model Button
        Button(
            onClick = { filePickerLauncher.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary
            ),
            enabled = !isLoading
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Model")
        }
        
        // Model List
        Text(
            text = "Available Models",
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface
        )
        
        if (models.isEmpty()) {
            EmptyModelsList()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(models) { model ->
                    ModelListItem(
                        model = model,
                        isCurrentModel = model.id == currentModel?.id,
                        onLoad = {
                            scope.launch { modelRepository.loadModel(model) }
                        },
                        onDelete = { showDeleteDialog = model }
                    )
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model") },
            text = { Text("Are you sure you want to delete \"${model.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            modelRepository.deleteModel(model)
                            showDeleteDialog = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Error Dialog
    showErrorDialog?.let { error ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}

/**
 * Current Model Card
 */
@Composable
private fun CurrentModelCard(
    model: ModelInfo?,
    isLoading: Boolean,
    onUnload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Current Model",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Primary
                    )
                }
            }
            
            if (model != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${model.type.displayName} • ${formatFileSize(model.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant
                        )
                        if (model.language.isNotBlank()) {
                            Text(
                                text = "Language: ${model.language}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Success
                            )
                        }
                    }
                    
                    IconButton(onClick = onUnload) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Unload model",
                            tint = Error
                        )
                    }
                }
            } else {
                Text(
                    text = "No model loaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceMuted
                )
            }
        }
    }
}

/**
 * Storage Usage Card
 */
@Composable
private fun StorageUsageCard(usage: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Storage Usage",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(usage),
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface
                    )
                }
            }
        }
    }
}

/**
 * Model List Item
 */
@Composable
private fun ModelListItem(
    model: ModelInfo,
    isCurrentModel: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isCurrentModel, onClick = onLoad),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentModel) Primary.copy(alpha = 0.2f) else Surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Model type icon
                Icon(
                    imageVector = when (model.type) {
                        ModelType.WHISPER -> Icons.Default.Mic
                        ModelType.VOSK -> Icons.Default.RecordVoiceOver
                        ModelType.TFLITE -> Icons.Default.Memory
                        ModelType.CUSTOM -> Icons.Default.Folder
                    },
                    contentDescription = null,
                    tint = if (isCurrentModel) Primary else OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentModel) Primary else OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatFileSize(model.size)} • ${model.type.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
            }
            
            // Status/Actions
            if (isCurrentModel) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Loaded",
                    tint = Success,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Empty State for Model List
 */
@Composable
private fun EmptyModelsList() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = OnSurfaceMuted,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "No models available",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceMuted
            )
            Text(
                text = "Add a model to get started",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
        }
    }
}

/**
 * Format file size to human readable string
 */
private fun formatFileSize(size: Long): String {
    return when {
        size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        size >= 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        size >= 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> "$size B"
    }
}