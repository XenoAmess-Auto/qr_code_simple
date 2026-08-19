package com.xenoamess.qrcodesimple

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.xenoamess.qrcodesimple.databinding.FragmentScanImageBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanImageFragment : Fragment() {

    private var _binding: FragmentScanImageBinding? = null
    private val binding get() = _binding!!
    private var currentPhotoPath: String? = null
    private var currentPhotoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                processMedia(uri)
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val photoUri = currentPhotoUri
        if (success && photoUri != null) {
            currentPhotoPath = null
            currentPhotoUri = null
            processImage(photoUri)
        } else {
            deletePendingPhoto()
            if (success) {
                Toast.makeText(requireContext(), R.string.failed_to_load_image, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPhotoPath = savedInstanceState?.getString(STATE_PHOTO_PATH)
        currentPhotoUri = savedInstanceState?.getString(STATE_PHOTO_URI)?.let(Uri::parse)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnGallery.setOnClickListener {
            pickFromGallery()
        }

        binding.btnCamera.setOnClickListener {
            takePhoto()
        }

        binding.btnFile.setOnClickListener {
            pickFromFile()
        }
    }

    private fun pickFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun pickFromFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        pickImageLauncher.launch(intent)
    }

    private fun takePhoto() {
        val context = requireContext()
        if (Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, R.string.failed_to_load_image, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath
            currentPhotoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            takePhotoLauncher.launch(currentPhotoUri!!)
        } catch (e: Exception) {
            deletePendingPhoto()
            Toast.makeText(
                context,
                context.getString(R.string.failed_to_save, e.message.orEmpty()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun processMedia(uri: Uri) {
        ScanImageProcessor.processMedia(requireContext(), uri)
    }

    private fun processImage(uri: Uri) {
        ScanImageProcessor.processImage(requireContext(), uri)
    }

    private fun deletePendingPhoto() {
        currentPhotoPath?.let { File(it).delete() }
        currentPhotoPath = null
        currentPhotoUri = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHOTO_PATH, currentPhotoPath)
        outState.putString(STATE_PHOTO_URI, currentPhotoUri?.toString())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val STATE_PHOTO_PATH = "scan_image.photo_path"
        const val STATE_PHOTO_URI = "scan_image.photo_uri"
    }
}
