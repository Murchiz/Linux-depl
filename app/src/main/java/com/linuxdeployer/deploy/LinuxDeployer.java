package com.linuxdeployer.deploy;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class LinuxDeployer {
    private static final String TAG = "LinuxDeployer";
    private Context context;
    private String installPath;

    public LinuxDeployer(Context context) {
        this.context = context;
        this.installPath = context.getFilesDir().getAbsolutePath() + "/linux";
    }

    public String getInstallPath() {
        return installPath;
    }

    public boolean isInstalled() {
        return new File(installPath + "/bin/sh").exists();
    }

    public boolean runRootCommand(String command) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            Log.e(TAG, "Error running su command: " + command, e);
            return false;
        }
    }

    public void install(String sourcePath, boolean isLocal) throws Exception {
        new File(installPath).mkdirs();

        String tarCmd;
        if (isLocal) {
            tarCmd = "su -c 'tar -C " + installPath + " -xzf " + sourcePath + "'";
        } else {
            // If sourcePath is a URL, this logic should be handled by a downloader first
            throw new Exception("Download not implemented in install() directly");
        }

        Process p = Runtime.getRuntime().exec("su");
        DataOutputStream os = new DataOutputStream(p.getOutputStream());
        os.writeBytes(tarCmd + "\n");
        os.writeBytes("exit\n");
        os.flush();
        p.waitFor();
    }

    public void mount() {
        String[] commands = {
            "mount -t proc proc " + installPath + "/proc",
            "mount -t sysfs sysfs " + installPath + "/sys",
            "mount -o bind /dev " + installPath + "/dev",
            "mount -t devpts devpts " + installPath + "/dev/pts"
        };
        for (String cmd : commands) {
            runRootCommand(cmd);
        }
    }

    public void unmount() {
        String[] commands = {
            "umount " + installPath + "/dev/pts",
            "umount " + installPath + "/dev",
            "umount " + installPath + "/sys",
            "umount " + installPath + "/proc"
        };
        for (String cmd : commands) {
            runRootCommand(cmd);
        }
    }

    public String getChrootCommand() {
        return "su -c 'PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin " +
               "chroot " + installPath + " /bin/sh'";
    }

    public void startSshd() {
        // Simple script to start dropbear or openssh inside chroot
        String startScript = "su -c 'chroot " + installPath + " /usr/sbin/dropbear -p 2222 -R || chroot " + installPath + " /usr/sbin/sshd -p 2222'";
        runRootCommand(startScript);
    }
}
