package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.domain.model.DiagnosticError
import com.example.domain.model.FileNode
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LumaFormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 B/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "Unknown"
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US)
        return sdf.format(Date(epochMs))
    }

    fun buildAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
    }

    fun launchExternalAppChooser(
        context: Context,
        fileNode: FileNode,
        onError: (DiagnosticError) -> Unit
    ) {
        try {
            val uri: Uri = if (fileNode.isSafDocument && fileNode.safUriString != null) {
                Uri.parse(fileNode.safUriString)
            } else {
                val file = File(fileNode.path)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, fileNode.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(viewIntent, "Open '${fileNode.name}' with...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError(
                DiagnosticError(
                    operationTitle = "External App Launch Error",
                    targetPathOrUrl = fileNode.path,
                    exceptionClass = e.javaClass.name,
                    errorCodeOrStatus = e.javaClass.simpleName,
                    exactReason = e.message ?: "No external application handler or FileProvider URI failure.",
                    remediationHint = "Use 'Open & Edit in Luma Files Studio' to view or edit '${fileNode.name}' directly inside Luma Files without requiring an external app."
                )
            )
        }
    }
}
