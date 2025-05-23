// File: DownloadFragmentYT.kt
package com.afitech.tikdownloader.ui.fragments

import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.afitech.tikdownloader.R
import com.afitech.tikdownloader.network.NetworkHelper
import com.afitech.tikdownloader.ui.components.LoadingDialogYT
import com.afitech.tikdownloader.ui.services.DownloadService
import com.afitech.tikdownloader.utils.CuanManager
import com.afitech.tikdownloader.utils.setStatusBarColor
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class DownloadFragmentYT : Fragment() {

    companion object {
        private const val ARG_VIDEO_URL = "video_url"
        fun newInstance(url: String?) = DownloadFragmentYT().apply {
            arguments = Bundle().apply {
                putString(ARG_VIDEO_URL, url)
            }
        }
    }

    private val cuanManager = CuanManager()
    private lateinit var editTextUrl: TextInputEditText
    private lateinit var btnDownload: MaterialButton
    private lateinit var radioMp4: RadioButton
    private lateinit var radioMp3: RadioButton
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var adView: AdView
    private var dialogDismissed = false
    private lateinit var localBroadcastManager: LocalBroadcastManager

    private val downloadReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DownloadService.ACTION_PROGRESS -> {
                    val p = intent.getIntExtra(DownloadService.EXTRA_PROGRESS, 0)
                    Log.d("DownloadFragmentYT", "Progress received: $p%")

                    if (!dialogDismissed && p > 0) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d("DownloadFragmentYT", "Dismissing dialog at progress $p%")
                            LoadingDialogYT.dismiss()
                        }
                        dialogDismissed = true
                    }
                    btnDownload.text = "Mengunduh...$p%"
                }
                DownloadService.ACTION_COMPLETE -> {
                    val success = intent.getBooleanExtra(DownloadService.EXTRA_SUCCESS, false)
                    Log.d("DownloadFragmentYT", "Complete received: success=$success")

                    if (!dialogDismissed) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d("DownloadFragmentYT", "Dismissing dialog on complete")
                            LoadingDialogYT.dismiss()
                        }
                        dialogDismissed = true
                    }
                    btnDownload.apply {
                        text = "Download"
                        isEnabled = true
                    }
                    val msg = if (success) "Download selesai!" else "Download gagal!"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_download_yt, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // init UI
        editTextUrl = view.findViewById(R.id.editTextUrl)
        arguments?.getString(ARG_VIDEO_URL)?.let { editTextUrl.setText(it) }
        btnDownload = view.findViewById(R.id.btnDownload)
        radioMp4 = view.findViewById(R.id.radioMp4)
        radioMp3 = view.findViewById(R.id.radioMp3)
        adView = view.findViewById(R.id.adView)
        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // Inisialisasi dan muat iklan menggunakan AdMobManager
        cuanManager.initializeAdMob(requireContext())
        cuanManager.loadAd(adView)// AdMob

        // Clipboard
        checkClipboardOnStart()
        checkClipboardForLink()

        val textCount = view.findViewById<TextView>(R.id.textCount)
        val maxCharacters = 99
        val tolerance = 1
        val maxWithTolerance = maxCharacters + tolerance

// URL validation
        val youtube_regex = Regex("^(https://(www\\.)?youtube\\.com/(watch\\?v=\\S+|shorts/\\S+|clip/\\S+)|https://youtu\\.be/[^\\s]+)$")

        // Fungsi untuk mendeteksi platform dengan URL yang valid
        fun detectPlatformPrecise(url: String): String {
            return when {
                youtube_regex.matches(url) -> "youtube"
                else -> "invalid"
            }
        }

        editTextUrl.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val url = s?.toString()?.trim() ?: ""
                val currentLength = url.length

                // Update jumlah karakter yang terinput
                textCount.text = "$currentLength/$maxCharacters"

                // Warna merah jika melebihi batas normal
                if (currentLength > maxCharacters) {
                    textCount.setTextColor(ContextCompat.getColor(requireActivity(), android.R.color.holo_red_dark))
                } else {
                    textCount.setTextColor(ContextCompat.getColor(requireActivity(), android.R.color.darker_gray))
                }

                // Potong otomatis jika melebihi batas toleransi
                if (currentLength > maxWithTolerance) {
                    val trimmed = url.substring(0, maxWithTolerance)
                    editTextUrl.setText(trimmed)
                    editTextUrl.setSelection(trimmed.length)
                }

                // 🌐 Validasi URL presisi
                val platform = detectPlatformPrecise(url)
                if (url.isEmpty()) {
                    editTextUrl.error = null
                } else if (platform == "invalid") {
                    editTextUrl.error = "Link tidak valid atau formatnya salah (pastikan lengkap)"
                } else {
                    editTextUrl.error = null
                }
                setDownloadButtonEnabled(platform != "invalid")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Register receiver
        localBroadcastManager = LocalBroadcastManager.getInstance(requireContext())
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_PROGRESS)
            addAction(DownloadService.ACTION_COMPLETE)
        }
        localBroadcastManager.registerReceiver(downloadReceiver, filter)

        // Download button
        btnDownload.setOnClickListener {
            val videoUrl = editTextUrl.text?.toString()?.trim().orEmpty()

            if (videoUrl.isEmpty()) {
                showToast("Masukkan URL terlebih dahulu!")
                return@setOnClickListener
            }

            // Validasi URL menggunakan regex
            val youtubeRegex = Regex("^(https://(www\\.)?youtube\\.com/(watch\\?v=\\S+|shorts/\\S+|clip/\\S+)|https://youtu\\.be/\\S+)$")
            if (!youtubeRegex.matches(videoUrl)) {
                showToast("Link YouTube tidak valid atau format salah.")
                return@setOnClickListener
            }

            if (!NetworkHelper.isInternetAvailable(requireContext())) {
                showToast("Tidak ada koneksi internet!")
                return@setOnClickListener
            }

            val format = when {
                radioMp3.isChecked -> "mp3"
                radioMp4.isChecked -> "mp4"
                else -> {
                    showToast("Pilih format terlebih dahulu!")
                    return@setOnClickListener
                }
            }

            // Mengubah status tombol menjadi tidak bisa diklik dan menunjukkan progress
            btnDownload.apply {
                isEnabled = false
                text = "Menunggu..."
            }

            // Menampilkan loading dialog
            dialogDismissed = false
            LoadingDialogYT.show(requireContext())

            // Mulai proses download dengan service
            Intent(requireContext(), DownloadService::class.java).apply {
                putExtra("video_url", videoUrl)
                putExtra("format", format)
            }.also(requireContext()::startService)

            // Ketika download selesai, aktifkan tombol kembali
            // Pastikan DownloadService atau callback selesai menandai download selesai
            val doneCallback: (Boolean) -> Unit = { success ->
                // Update UI sesuai dengan status sukses atau gagal
                Handler(Looper.getMainLooper()).post {
                    btnDownload.apply {
                        isEnabled = true // Aktifkan tombol kembali
                        text = if (success) "Download Selesai" else "" +
                                "' a Lagi"
                    }

                    // Sembunyikan loading dialog setelah selesai
                    LoadingDialogYT.dismiss()
                }
            }

            // Menyambungkan callback dengan logika selesai di dalam service
            DownloadService.setDoneCallback(doneCallback)
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        localBroadcastManager.unregisterReceiver(downloadReceiver)
        clipboardManager.removePrimaryClipChangedListener {}
        cuanManager.destroyAd(adView)
    }

    private fun setDownloadButtonEnabled(enabled: Boolean) {
        btnDownload.isEnabled = enabled         // Tetap dipakai biar aman walau LinearLayout
        btnDownload.isClickable = enabled
        btnDownload.isFocusable = enabled
        btnDownload.alpha = if (enabled) 1f else 0.5f  // Visual efek: buram saat nonaktif
    }

    private fun checkClipboardOnStart() {
        clipboardManager.primaryClip?.let { clip ->
            if (clip.itemCount > 0) {
                val txt = clip.getItemAt(0).text.toString()
                if (txt.isNotEmpty() && isYoutubeLink(txt)) editTextUrl.setText(txt)
            }
        }
    }

    private fun checkClipboardForLink() {
        clipboardManager.addPrimaryClipChangedListener {
            clipboardManager.primaryClip?.let { clip ->
                if (clip.itemCount > 0) {
                    val txt = clip.getItemAt(0).text.toString()
                    if (txt.isNotEmpty() && isYoutubeLink(txt)) editTextUrl.setText(txt)
                }
            }
        }
    }

    private fun isYoutubeLink(text: String): Boolean {
        val YOUTUBE_REGEX = Regex("^(https://(www\\.)?youtube\\.com/(watch\\?v=\\S+|shorts/\\S+|clip/\\S+)|https://youtu\\.be/[^\\s]+)$")
        return YOUTUBE_REGEX.containsMatchIn(text)
    }

    private fun showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume()

        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)

    }
}
