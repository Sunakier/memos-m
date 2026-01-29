package org.example.memosm.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.example.memosm.R


@Composable
fun getVisibilityLabel(visibility: String): String {
    return when (visibility.uppercase()) {
        "PUBLIC" -> stringResource(R.string.memo_visibility_public)
        "PROTECTED" -> stringResource(R.string.memo_visibility_protected)
        "PRIVATE" -> stringResource(R.string.memo_visibility_private)
        else -> visibility
    }
}


fun getVisibilityIcon(visibility: String, outlined: Boolean = true): ImageVector {
    return when (visibility.uppercase()) {
        "PUBLIC" -> if (outlined) Icons.Outlined.Public else Icons.Filled.Public
        "PROTECTED" -> if (outlined) Icons.Outlined.Lock else Icons.Filled.Lock
        "PRIVATE" -> if (outlined) Icons.Outlined.Lock else Icons.Filled.Lock
        else -> if (outlined) Icons.Outlined.Error else Icons.Filled.Error
    }
}


/**
 * Helper to find the Activity from a Context.
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}