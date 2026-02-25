package com.linuxdeployer;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.linuxdeployer.arch.ArchUtils;
import com.linuxdeployer.deploy.LinuxDeployer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class SetupActivity extends Activity {

    private LinuxDeployer deployer;
    private TextView tvStatus;
    private ProgressBar progressBar;
    private Button btnAlpine, btnManual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        deployer = new LinuxDeployer(this);
        if (deployer.isInstalled()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        tvStatus = (TextView) findViewById(R.id.tv_status);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        btnAlpine = (Button) findViewById(R.id.btn_alpine);
        btnManual = (Button) findViewById(R.id.btn_manual);

        btnAlpine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArchUtils.Architecture arch = ArchUtils.getDeviceArchitecture();
                String url = ArchUtils.getAlpineUrl(arch);
                if (url != null) {
                    new DownloadAndInstallTask().execute(url);
                } else {
                    Toast.makeText(SetupActivity.this, "Unsupported Architecture", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = new File(Environment.getExternalStorageDirectory(), "rootfs.tar.gz");
                if (file.exists()) {
                    new InstallTask().execute(file.getAbsolutePath());
                } else {
                    Toast.makeText(SetupActivity.this, "File not found at /sdcard/rootfs.tar.gz", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private class DownloadAndInstallTask extends AsyncTask<String, String, Boolean> {
        @Override
        protected void onPreExecute() {
            btnAlpine.setEnabled(false);
            btnManual.setEnabled(false);
            tvStatus.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            tvStatus.setText("Starting download...");
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String downloadUrl = params[0];
            String destPath = getCacheDir() + "/rootfs.tar.gz";
            try {
                URL url = new URL(downloadUrl);
                URLConnection connection = url.openConnection();
                connection.connect();

                int fileLength = connection.getContentLength();
                InputStream input = new BufferedInputStream(url.openStream(), 8192);
                FileOutputStream output = new FileOutputStream(destPath);

                byte data[] = new byte[1024];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    publishProgress("Downloading: " + (int)((total * 100) / fileLength) + "%");
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();

                publishProgress("Extracting (this may take a while)...");
                deployer.install(destPath, true);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            tvStatus.setText(values[0]);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                startActivity(new Intent(SetupActivity.this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(SetupActivity.this, "Installation failed", Toast.LENGTH_SHORT).show();
                btnAlpine.setEnabled(true);
                btnManual.setEnabled(true);
            }
        }
    }

    private class InstallTask extends AsyncTask<String, String, Boolean> {
        @Override
        protected void onPreExecute() {
            tvStatus.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            tvStatus.setText("Installing...");
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                deployer.install(params[0], true);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                startActivity(new Intent(SetupActivity.this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(SetupActivity.this, "Installation failed", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
