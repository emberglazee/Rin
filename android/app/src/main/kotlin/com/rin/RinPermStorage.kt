package com.rin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import com.rin.permission.StoragePermissionHelper

object RinPermStorage {
    
    fun requestStoragePermission(activity: Activity, onResult: (Boolean) -> Unit) {
        if (checkAndUpdatePermissionStatus(activity)) {
            onResult(true)
            return
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                    onResult(false)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivity(intent)
                    onResult(false)
                }
            } else {
                StoragePermissionHelper.setStoragePermissionGranted(activity, true)
                onResult(true)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_CODE_STORAGE
            )
            onResult(false)
        } else {
            StoragePermissionHelper.setStoragePermissionGranted(activity, true)
            onResult(true)
        }
    }
    
    fun checkAndUpdatePermissionStatus(context: Context): Boolean {
        val hasPermission = StoragePermissionHelper.hasSystemStoragePermission(context)
        if (hasPermission) {
            StoragePermissionHelper.setStoragePermissionGranted(context, true)
        }
        return hasPermission
    }
    
    const val REQUEST_CODE_STORAGE = 1001
}
