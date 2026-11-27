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
        if (bytes < 0L) return "—"
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
        if (epochMs <= 0L) return "—"
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US)
        return sdf.format(Date(epochMs))
    }

    fun formatCompactDate(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val now = System.currentTimeMillis()
        val diff = now - epochMs
        return when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${diff / 60_000L}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(epochMs))
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSec = ms / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
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

    fun resolveShareableUri(context: Context, fileNode: FileNode): Uri {
        return if (fileNode.isSafDocument && fileNode.safUriString != null) {
            Uri.parse(fileNode.safUriString)
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(fileNode.path)
            )
        }
    }

    fun launchExternalAppChooser(
        context: Context,
        fileNode: FileNode,
        onError: (DiagnosticError) -> Unit
    ) {
        try {
            val uri = resolveShareableUri(context, fileNode)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, fileNode.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError(
                DiagnosticError(
                    operationTitle = "Open Externally",
                    targetPathOrUrl = fileNode.path,
                    exceptionClass = e.javaClass.name,
                    errorCodeOrStatus = e.javaClass.simpleName,
                    exactReason = e.message ?: "No installed app can open this file.",
                    remediationHint = "View the file directly inside Luma Files."
                )
            )
        }
    }

    fun shareFile(
        context: Context,
        fileNode: FileNode,
        onError: (DiagnosticError) -> Unit
    ) {
        try {
            val uri = resolveShareableUri(context, fileNode)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = fileNode.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError(
                DiagnosticError(
                    operationTitle = "Share File",
                    targetPathOrUrl = fileNode.path,
                    exceptionClass = e.javaClass.name,
                    errorCodeOrStatus = e.javaClass.simpleName,
                    exactReason = e.message ?: "Unable to share this file.",
                    remediationHint = "Ensure the file exists and is readable."
                )
            )
        }
    }

    fun installApk(
        context: Context,
        fileNode: FileNode,
        onError: (DiagnosticError) -> Unit
    ) {
        try {
            val uri = resolveShareableUri(context, fileNode)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            onError(
                DiagnosticError(
                    operationTitle = "Install APK",
                    targetPathOrUrl = fileNode.path,
                    exceptionClass = e.javaClass.name,
                    errorCodeOrStatus = e.javaClass.simpleName,
                    exactReason = e.message ?: "Unable to launch package installer.",
                    remediationHint = "Allow 'Install unknown apps' for File Manager in Android settings."
                )
            )
        }
    }
}
