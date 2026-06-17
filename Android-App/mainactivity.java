package com.example.ecovision;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// 🔥 NETWORK
import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST = 100;
    private static final int PERMISSION_REQUEST = 101;

    private File photoFile;
    private ProgressBar loading;
    private ImageView imagePreview;
    private TextView tvResult;
    private Button btnCapture;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        loading = findViewById(R.id.loading);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagePreview = findViewById(R.id.imagePreview);
        tvResult = findViewById(R.id.tvResult);
        btnCapture = findViewById(R.id.btnCapture);
        loading = findViewById(R.id.loading);
        // 🔊 Text to Speech
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.US);
            }
        });

        // 📷 Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST);
        }

        btnCapture.setOnClickListener(v -> openCamera());
    }

    // 📸 OPEN CAMERA
    private void openCamera() {
        try {
            photoFile = createImageFile();

            Uri photoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", photoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

// Try forcing back camera
            intent.putExtra("android.intent.extras.CAMERA_FACING", 0);
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", false);
            intent.putExtra("android.intent.extra.LENS_FACING", 1);
            intent.putExtra("android.intent.extras.LENS_FACING_BACK", 1);

// Some devices need this too
            intent.putExtra("android.intent.extra.CAMERA_MODE", 0);

            if (intent.resolveActivity(getPackageManager()) != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(intent, CAMERA_REQUEST);
            } else {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show();
        }
    }

    // 📂 CREATE IMAGE FILE
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "IMG_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        return File.createTempFile(fileName, ".jpg", storageDir);
    }

    // 📸 RESULT AFTER CAPTURE
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            try {
                // Clear old image
                imagePreview.setImageDrawable(null);

                // Show new image
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                        this.getContentResolver(),
                        FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile)
                );

                imagePreview.setImageBitmap(bitmap);

                // 🔥 SEND TO SERVER
                loading.setVisibility(View.VISIBLE);   // 👈 SHOW LOADER
                tvResult.setText("Detecting...");

                sendImageToServer(photoFile);

                // Allow next capture
                photoFile = null;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 🌐 SEND IMAGE TO SERVER
    private void sendImageToServer(File file) {

        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                        "image",
                        file.getName(),
                        RequestBody.create(file, MediaType.parse("image/*"))
                )
                .build();

        Request request = new Request.Builder()
                .url("http://192.168.1.10:5000/detect")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace(); // 👈 VERY IMPORTANT

                runOnUiThread(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                final String result = response.body().string(); // 👈 IMPORTANT

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading.setVisibility(View.GONE);
                        tvResult.setText(result);
                        speakResult(result);
                    }
                });
            }
        });
    }

    // 🔊 SPEAK RESULT
    private void speakResult(String text) {
        if (tts != null && !text.isEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1");
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    // 🔐 PERMISSION RESULT
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission required!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
