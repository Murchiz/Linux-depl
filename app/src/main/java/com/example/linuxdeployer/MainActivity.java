package com.example.linuxdeployer;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.io.IOException;

import jackpal.androidterm.TermExec;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;

public class MainActivity extends AppCompatActivity {

    private TextView txtIp;
    private TextView txtPort;
    private EmulatorView terminalView;

    private LinuxDeployer deployer;
    private Process suProcess;
    private TermSession termSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deployer = new LinuxDeployer();
        txtIp = (TextView) findViewById(R.id.txtIp);
        txtPort = (TextView) findViewById(R.id.txtPort);
        terminalView = (EmulatorView) findViewById(R.id.terminalView);

        txtIp.setText("IP: " + deployer.getLanIpv4Address());
        txtPort.setText("SSH: " + LinuxDeployer.SSH_PORT);

        startRootTerminal();
    }

    private void startRootTerminal() {
        try {
            suProcess = Runtime.getRuntime().exec("su");

            String startup = deployer.buildChrootStartupScript();
            suProcess.getOutputStream().write(startup.getBytes("UTF-8"));
            suProcess.getOutputStream().flush();

            termSession = new TermSession();
            termSession.setTermOut(suProcess.getOutputStream());
            termSession.setTermIn(suProcess.getInputStream());
            termSession.initializeEmulator(120, 40);

            terminalView.attachSession(termSession);
            terminalView.setTextSize(14);
            terminalView.setTypeface(Typeface.MONOSPACE);

            final FileDescriptor fd = TermExec.createSubprocess("/system/bin/sh", "-", null, null);
            if (fd != null) {
                // Keep a live pty around for keyboard behavior on old devices.
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        terminalView.requestFocus();
                    }
                }, 300);
            }

        } catch (Exception e) {
            txtIp.setText("IP: " + deployer.getLanIpv4Address() + " | terminal init failed: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (suProcess != null) {
                suProcess.getOutputStream().write("exit\n".getBytes("UTF-8"));
                suProcess.getOutputStream().flush();
            }
        } catch (IOException ignored) {
        }

        if (termSession != null) {
            termSession.finish();
        }

        deployer.unmountAll();
    }
}
