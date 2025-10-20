package com.pdfviewer.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private lateinit var openPdfButton: MaterialButton

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            openPdfFile(it)
        }
    }

    // Permission launcher for Android 13+
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFilePicker()
        } else {
            showPermissionDeniedDialog()
        }
    }

    // Permission launcher for older Android versions
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFilePicker()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        openPdfButton = findViewById(R.id.openPdfButton)
    }

    private fun setupListeners() {
        openPdfButton.setOnClickListener {
            checkPermissionsAndOpenPicker()
        }
    }

    private fun checkPermissionsAndOpenPicker() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - Use READ_MEDIA_IMAGES or no permission needed for file picker
                openFilePicker()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12 - Check MANAGE_EXTERNAL_STORAGE or use scoped storage
                if (Environment.isExternalStorageManager()) {
                    openFilePicker()
                } else {
                    // Use scoped storage via file picker
                    openFilePicker()
                }
            }
            else -> {
                // Android 10 and below - Check READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    openFilePicker()
                } else {
                    legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun openFilePicker() {
        try {
            filePickerLauncher.launch("application/pdf")
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_loading_pdf),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openPdfFile(uri: Uri) {
        val intent = Intent(this, PdfViewerActivity::class.java).apply {
            data = uri
        }
        startActivity(intent)
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.error_permission_denied))
            .setMessage(getString(R.string.storage_permission_required))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
}
