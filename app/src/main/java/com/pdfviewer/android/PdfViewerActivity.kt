package com.pdfviewer.android

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnErrorListener
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator

class PdfViewerActivity : AppCompatActivity(), OnPageChangeListener, OnLoadCompleteListener,
    OnErrorListener {

    private lateinit var pdfView: PDFView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var pageIndicator: TextView
    private lateinit var loadingProgress: CircularProgressIndicator
    private lateinit var zoomInButton: FloatingActionButton
    private lateinit var zoomOutButton: FloatingActionButton

    private var currentPage = 0
    private var totalPages = 0
    private var currentZoom = 1.0f
    private val zoomStep = 0.5f
    private val minZoom = 0.5f
    private val maxZoom = 5.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        initViews()
        setupToolbar()
        setupZoomControls()
        loadPdfFromIntent()
    }

    private fun initViews() {
        pdfView = findViewById(R.id.pdfView)
        toolbar = findViewById(R.id.toolbar)
        pageIndicator = findViewById(R.id.pageIndicator)
        loadingProgress = findViewById(R.id.loadingProgress)
        zoomInButton = findViewById(R.id.zoomInButton)
        zoomOutButton = findViewById(R.id.zoomOutButton)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupZoomControls() {
        zoomInButton.setOnClickListener {
            zoomIn()
        }

        zoomOutButton.setOnClickListener {
            zoomOut()
        }
    }

    private fun loadPdfFromIntent() {
        val uri: Uri? = intent.data

        if (uri == null) {
            Toast.makeText(this, getString(R.string.error_file_not_found), Toast.LENGTH_SHORT)
                .show()
            finish()
            return
        }

        loadPdf(uri)
    }

    private fun loadPdf(uri: Uri) {
        showLoading(true)

        try {
            pdfView.fromUri(uri)
                .defaultPage(0)
                .onPageChange(this)
                .onLoad(this)
                .onError(this)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .enableAnnotationRendering(true)
                .scrollHandle(DefaultScrollHandle(this))
                .spacing(10)
                .pageFitPolicy(com.github.barteksc.pdfviewer.util.FitPolicy.WIDTH)
                .load()
        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(
                this,
                getString(R.string.error_loading_pdf),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun zoomIn() {
        if (currentZoom < maxZoom) {
            currentZoom += zoomStep
            pdfView.zoomTo(currentZoom)
            Toast.makeText(this, "Zoom: ${(currentZoom * 100).toInt()}%", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun zoomOut() {
        if (currentZoom > minZoom) {
            currentZoom -= zoomStep
            pdfView.zoomTo(currentZoom)
            Toast.makeText(this, "Zoom: ${(currentZoom * 100).toInt()}%", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        pdfView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updatePageIndicator() {
        pageIndicator.text = getString(R.string.page_indicator, currentPage + 1, totalPages)
    }

    // OnPageChangeListener
    override fun onPageChanged(page: Int, pageCount: Int) {
        currentPage = page
        totalPages = pageCount
        updatePageIndicator()
    }

    // OnLoadCompleteListener
    override fun loadComplete(nbPages: Int) {
        showLoading(false)
        totalPages = nbPages
        updatePageIndicator()

        // Get file name from URI and set as title
        intent.data?.lastPathSegment?.let { fileName ->
            supportActionBar?.title = fileName
        }
    }

    // OnErrorListener
    override fun onError(t: Throwable?) {
        showLoading(false)
        Toast.makeText(
            this,
            getString(R.string.error_loading_pdf) + ": ${t?.message}",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}
