package ru.datamosh.radio

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            AppLog.d("APP", "Notification permission granted=$it")
        }
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            AppLog.d("APP", "Storage permission granted=$it")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.startSession()
        AppLog.d("APP", "MainActivity created, smallestWidth=${resources.configuration.smallestScreenWidthDp}dp")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        setContent { RadioRoot() }
    }

    override fun onStart() {
        super.onStart()
        AppLog.d("APP", "onStart")
        RadioRepo.acquire()
    }

    override fun onStop() {
        AppLog.d("APP", "onStop")
        RadioRepo.release()
        super.onStop()
    }

    override fun onDestroy() {
        AppLog.d("APP", "onDestroy, finishing=$isFinishing")
        AppLog.endSession()
        super.onDestroy()
    }
}
