package com.afitech.sosmedtoolkit.ui.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.afitech.sosmedtoolkit.R
import com.afitech.sosmedtoolkit.data.StorySaver
import com.afitech.sosmedtoolkit.data.database.AppDatabase
import com.afitech.sosmedtoolkit.data.database.DownloadHistoryDao
import com.afitech.sosmedtoolkit.databinding.FragmentWhatsappStoryBinding
import com.afitech.sosmedtoolkit.ui.adapters.StoryPagerAdapter
import com.afitech.sosmedtoolkit.utils.setStatusBarColor
import com.google.android.material.tabs.TabLayoutMediator

class WhatsappStoryFragment : Fragment() {

    private lateinit var binding: FragmentWhatsappStoryBinding
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var downloadHistoryDao: DownloadHistoryDao

    private val requestStorageAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == -1) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)

            saveUri(uri.toString())

            Log.d("WhatsappStoryFragment", "URI disimpan: $uri")
            Toast.makeText(requireContext(), "Akses folder berhasil disimpan", Toast.LENGTH_SHORT).show()

            // Reload view pager agar media muncul
            setupViewPagerWithTabs()

        } else {
            Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inisialisasi database dan DAO
        val db = AppDatabase.getDatabase(requireContext())
        downloadHistoryDao = db.downloadHistoryDao()

        // Assign DAO ke StorySaver agar adapter bisa akses DAO ini
        StorySaver.downloadHistoryDao = downloadHistoryDao
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWhatsappStoryBinding.inflate(inflater, container, false)
        sharedPreferences = requireContext().getSharedPreferences("TikDownloaderPrefs", Context.MODE_PRIVATE)

        if (!hasStoragePermission() || getSavedUri().isEmpty()) {
            requestStoragePermission()
        } else {
            setupViewPagerWithTabs()
        }

        return binding.root
    }

    private fun setupViewPagerWithTabs() {
        val pagerAdapter = StoryPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Gambar"
                1 -> "Video"
                else -> "Lainnya"
            }

            val textColor = ContextCompat.getColor(tab.view.context, android.R.color.white)

            val textView = TextView(tab.view.context).apply {
                text = tab.text
                setTextColor(textColor)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            tab.customView = textView
        }.attach()
    }

    private fun hasStoragePermission(): Boolean {
        return requireContext().contentResolver.persistedUriPermissions.any()
    }

    private fun getSavedUri(): String {
        return sharedPreferences.getString("savedUri", "") ?: ""
    }

    private fun saveUri(uri: String) {
        sharedPreferences.edit().putString("savedUri", uri).apply()
    }

    private fun requestStoragePermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        requestStorageAccessLauncher.launch(intent)
    }

    override fun onResume() {
        super.onResume()
        setStatusBarColor(R.color.colorPrimary, isLightStatusBar = false)
    }
}

