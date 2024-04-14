package com.yagubbilalzade.bitkitanima

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.opengl.Visibility
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.squareup.picasso.Picasso
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {

    var mImageUri: Uri? = null
    var pick_image_request = 1
    val PERMISSION_CODE = 1000
    private lateinit var cameraActivityResultLauncher: ActivityResultLauncher<Uri?>
    private var selectedMediaFile: File? = null
    private var scientificNameWithoutAuthor: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main2)
        } else {
            setContentView(R.layout.activity_main)
        }


        val btn_cFile = findViewById<Button>(R.id.uploadFile)
        val btn_camTake = findViewById<Button>(R.id.cameraTake)
        val img_plant = findViewById<ImageView>(R.id.imageUser)
        val result_button = findViewById<Button>(R.id.resultBtn)
        val textUser = findViewById<TextView>(R.id.textUser)
        val progressJson = findViewById<ProgressBar>(R.id.progressJson)


        val pickMedia =
            registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) {
                    mImageUri = uri
                    Log.d("PhotoPicker", "Selected URI: $uri")
                    Picasso.with(this).load(mImageUri).into(img_plant)
                    selectedMediaFile = uriToTempFile(mImageUri!!)
                    pick_image_request = 2
                } else {
                    Log.d("PhotoPicker", "No media selected")
                }
            }

        btn_cFile.setOnClickListener(View.OnClickListener {

            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })

        cameraActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    Picasso.with(this).load(mImageUri).into(img_plant)
                    if (mImageUri != null) {
                        selectedMediaFile = uriToTempFile(mImageUri!!)
                    }
                }
            }

        btn_camTake.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED || checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_DENIED
                ) {

                    val permission = arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )


                    requestPermissions(permission, PERMISSION_CODE)
                } else {


                    openCamera()
                }
            } else {


                openCamera()
            }
        }

        result_button.setOnClickListener {
            selectedMediaFile?.let { sendImageToPlantNet(it, textUser, progressJson) }
                ?: Log.d("Plantnet", "File is empty")

        }


    }


    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "New Picture")
        values.put(MediaStore.Images.Media.DESCRIPTION, "From the Camera")
        mImageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri)
        cameraActivityResultLauncher.launch(mImageUri)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_CODE -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    openCamera()
                } else {

                }
            }
        }
    }


    private fun uriToTempFile(uri: Uri): File? {
        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val tempFile = File.createTempFile("selected_media", null, cacheDir)
            tempFile.deleteOnExit()
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            outputStream.close()
            inputStream.close()
            return tempFile
        }
        return null
    }

    private fun sendImageToPlantNet(mediaFile: File, textView: TextView, progressBar: ProgressBar) {

        progressBar.visibility = View.VISIBLE
        textView.text = "Zəhmət olmasa gözləyin"
        val apiUrl =
            "https://my-api.plantnet.org/v2/identify/all?include-related-images=false&no-reject=false&lang=en&api-key=2b106f94UgfovuJnuPRVbDPk8u"

        val client = OkHttpClient()

        val request = Request.Builder().url(apiUrl).header("accept", "application/json")
            .header("Content-Type", "multipart/form-data").post(
                MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
                        "images",
                        mediaFile.name,
                        mediaFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    ).build()
            ).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val jsonString = response.body?.string()
                if (response.isSuccessful && jsonString != null) {

                    Log.d("JSON Response", "$jsonString")

                    val jsonObject = JSONObject(jsonString)


                    val species = jsonObject.optJSONArray("results")?.optJSONObject(0)
                        ?.optJSONObject("species")
                    scientificNameWithoutAuthor =
                        species?.optString("scientificNameWithoutAuthor", "")


                    Log.d("JSON Response", "$scientificNameWithoutAuthor")

                    runOnUiThread {
                        textView.text = scientificNameWithoutAuthor ?: "Scientific name not found"
                        progressBar.visibility = View.GONE
                    }


                } else {

                    Log.d("API Error. Status code", "${response.code}")
                    progressBar.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()

                Log.d("API Error. Status code", "${e.message}")
                progressBar.visibility = View.GONE
            }
        })
    }


}