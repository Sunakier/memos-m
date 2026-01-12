package org.example.memosm.ui.components.item.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class FileThumbnailMode {
    WIDE,
    NORMAL,
    COMPACT
}

enum class FileType {
    PDF,
    DOCUMENT,
    ARCHIVE,
    GENERIC;

    companion object {
        fun fromDisplayType(displayType: String): FileType {
            return when {
                displayType.contains("pdf", ignoreCase = true) -> PDF
                displayType.contains("text", ignoreCase = true) ||
                        displayType.contains("markdown", ignoreCase = true) -> DOCUMENT
                displayType.contains("zip", ignoreCase = true) ||
                        displayType.contains("archive", ignoreCase = true) ||
                        displayType.contains("tar", ignoreCase = true) -> ARCHIVE
                else -> GENERIC
            }
        }
    }

    val icon: ImageVector
        get() = when (this) {
            PDF -> Icons.Outlined.PictureAsPdf
            DOCUMENT -> Icons.Outlined.Description
            ARCHIVE -> Icons.Outlined.FolderZip
            GENERIC -> Icons.AutoMirrored.Outlined.InsertDriveFile
        }
}

@Composable
fun FileThumbnail(
    displayType: String,
    filename: String,
    mode: FileThumbnailMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileType = remember(displayType) { FileType.fromDisplayType(displayType) }

    when (mode) {
        FileThumbnailMode.WIDE -> {
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .clickable { onClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = fileType.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .clickable { onClick() }
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = fileType.icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (mode == FileThumbnailMode.NORMAL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = filename,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
