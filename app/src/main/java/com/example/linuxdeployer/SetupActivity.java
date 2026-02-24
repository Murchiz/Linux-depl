package com.example.linuxdeployer;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class SetupActivity extends AppCompatActivity {

    private static final int REQ_PICK_FILE = 101;

    private Spinner spinnerDistro;
    private TextView txtInfo;
    private LinuxDeployer deployer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        deployer = new LinuxDeployer();
        spinnerDistro = (Spinner) findViewById(R.id.spinnerDistro);
        txtInfo = (TextView) findViewById(R.id.txtInfo);
        Button btnDownload = (Button) findViewById(R.id.btnDownload);
        Button btnPickLocal = (Button) findViewById(R.id.btnPickLocal);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Alpine", "Debian"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDistro.setAdapter(adapter);

        String arch = deployer.detectArchitecture();
        txtInfo.setText("Detected CPU architecture: " + arch + "\n"
                + "Manual mode: pick a local rootfs .tar.gz/.tar.xz from /sdcard.");

        ensurePermissions();

        btnDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String distro = spinnerDistro.getSelectedItem().toString();
                runDownloadAndDeploy(distro);
            }
        });

        btnPickLocal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "Select rootfs archive"), REQ_PICK_FILE);
            }
        });
    }

    private void ensurePermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    100);
        }
    }

    private void runDownloadAndDeploy(final String distro) {
        new AsyncTask<Void, String, Boolean>() {
            String error = null;

            @Override
            protected void onPreExecute() {
                txtInfo.setText("Preparing download...");
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String arch = deployer.detectArchitecture();
                    String url = deployer.getDefaultDistroUrl(distro, arch);
                    if (url == null) {
                        throw new IllegalStateException("No URL for distro=" + distro + " arch=" + arch);
                    }
                    publishProgress("Downloading: " + url);
                    deployer.downloadRootfs(url, LinuxDeployer.ROOTFS_ARCHIVE);
                    publishProgress("Extracting and deploying rootfs...");
                    deployer.deployRootfs(LinuxDeployer.ROOTFS_ARCHIVE);
                    return true;
                } catch (Exception e) {
                    error = e.getMessage();
                    return false;
                }
            }

            @Override
            protected void onProgressUpdate(String... values) {
                txtInfo.setText(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean ok) {
                if (ok) {
                    markSetupCompleteAndOpenMain();
                } else {
                    txtInfo.setText("Failed: " + error);
                }
            }
        }.execute();
    }

    private void deployLocalArchive(final Uri uri) {
        new AsyncTask<Void, String, Boolean>() {
            String error;

            @Override
            protected void onPreExecute() {
                txtInfo.setText("Copying local archive...");
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String copiedPath = deployer.copyRootfsFromUri(SetupActivity.this, uri);
                    publishProgress("Deploying from " + copiedPath);
                    deployer.deployRootfs(copiedPath);
                    return true;
                } catch (Exception e) {
                    error = e.getMessage();
                    return false;
                }
            }

            @Override
            protected void onProgressUpdate(String... values) {
                txtInfo.setText(values[0]);
            }

            @Override
            protected void onPostExecute(Boolean ok) {
                if (ok) {
                    markSetupCompleteAndOpenMain();
                } else {
                    txtInfo.setText("Failed: " + error);
                }
            }
        }.execute();
    }

    private void markSetupCompleteAndOpenMain() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("setup_done", true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("setup_done", false) && new File(LinuxDeployer.CHROOT_DIR).exists()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                deployLocalArchive(uri);
            } else {
                Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
