package com.example.linuxdeployer;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

public class LinuxDeployer {

    public static final String ROOT_DIR = "/data/local/linuxdeployer";
    public static final String CHROOT_DIR = ROOT_DIR + "/rootfs";
    public static final String ROOTFS_ARCHIVE = ROOT_DIR + "/rootfs.tar.gz";
    public static final int SSH_PORT = 2222;

    public String detectArchitecture() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            String abi = Build.SUPPORTED_ABIS[0];
            if (abi.contains("arm64") || abi.contains("aarch64")) {
                return "aarch64";
            }
            if (abi.contains("armeabi") || abi.contains("arm")) {
                return "armv7l";
            }
            if (abi.contains("x86_64")) {
                return "x86_64";
            }
            if (abi.contains("x86")) {
                return "x86";
            }
        }
        return runCommandForSingleLine("uname -m");
    }

    public String getDefaultDistroUrl(String distro, String arch) {
        if ("Alpine".equalsIgnoreCase(distro)) {
            if ("aarch64".equals(arch)) {
                return "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/alpine-minirootfs-latest-aarch64.tar.gz";
            }
            return "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/armv7/alpine-minirootfs-latest-armv7.tar.gz";
        }

        if ("Debian".equalsIgnoreCase(distro)) {
            if ("aarch64".equals(arch)) {
                return "https://github.com/debuerreotype/docker-debian-artifacts/raw/dist-arm64v8/bookworm/rootfs.tar.xz";
            }
            return "https://github.com/debuerreotype/docker-debian-artifacts/raw/dist-arm32v7/bookworm/rootfs.tar.xz";
        }

        return null;
    }

    public void downloadRootfs(String url, String outputPath) throws IOException {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");
            conn.connect();

            if (conn.getResponseCode() >= 300) {
                throw new IOException("Download failed HTTP " + conn.getResponseCode());
            }

            in = conn.getInputStream();
            File outFile = new File(outputPath);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            out = new FileOutputStream(outFile);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            if (in != null) in.close();
            if (out != null) out.close();
            if (conn != null) conn.disconnect();
        }
    }

    public String copyRootfsFromUri(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Cannot open selected URI");
        }

        File outFile = new File(ROOTFS_ARCHIVE);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        FileOutputStream fos = new FileOutputStream(outFile);
        byte[] buf = new byte[8192];
        int read;
        while ((read = inputStream.read(buf)) != -1) {
            fos.write(buf, 0, read);
        }
        fos.flush();
        fos.close();
        inputStream.close();
        return outFile.getAbsolutePath();
    }

    public void deployRootfs(String archivePath) throws IOException, InterruptedException {
        String script = "mkdir -p " + ROOT_DIR + "\n"
                + "mkdir -p " + CHROOT_DIR + "\n"
                + "rm -rf " + CHROOT_DIR + "/*\n"
                + "tar -xpf " + archivePath + " -C " + CHROOT_DIR + "\n"
                + "mkdir -p " + CHROOT_DIR + "/dev/pts " + CHROOT_DIR + "/proc " + CHROOT_DIR + "/sys\n"
                + "echo 'nameserver 8.8.8.8' > " + CHROOT_DIR + "/etc/resolv.conf\n"
                + "exit\n";
        runSuScript(script);
    }

    public String buildChrootStartupScript() {
        return "mount -t proc proc " + CHROOT_DIR + "/proc\n"
                + "mount -t sysfs sys " + CHROOT_DIR + "/sys\n"
                + "mount -o bind /dev " + CHROOT_DIR + "/dev\n"
                + "mount -t devpts devpts " + CHROOT_DIR + "/dev/pts\n"
                + "chroot " + CHROOT_DIR + " /bin/sh -c '\n"
                + "if command -v dropbear >/dev/null 2>&1; then mkdir -p /var/run; dropbear -R -E -p " + SSH_PORT + "; fi\n"
                + "if command -v sshd >/dev/null 2>&1; then mkdir -p /var/run/sshd; /usr/sbin/sshd -p " + SSH_PORT + "; fi\n"
                + "export TERM=xterm\n"
                + "exec /bin/sh\n"
                + "'\n";
    }

    public void unmountAll() {
        String script = "umount " + CHROOT_DIR + "/dev/pts 2>/dev/null\n"
                + "umount " + CHROOT_DIR + "/dev 2>/dev/null\n"
                + "umount " + CHROOT_DIR + "/proc 2>/dev/null\n"
                + "umount " + CHROOT_DIR + "/sys 2>/dev/null\n"
                + "exit\n";
        try {
            runSuScript(script);
        } catch (Exception ignored) {
        }
    }

    public String getLanIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (java.net.InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }

    private void runSuScript(String script) throws IOException, InterruptedException {
        Process su = Runtime.getRuntime().exec("su");
        DataOutputStream dos = new DataOutputStream(su.getOutputStream());
        dos.writeBytes(script);
        dos.flush();
        int exit = su.waitFor();
        dos.close();
        if (exit != 0) {
            throw new IOException("su script failed with exit=" + exit);
        }
    }

    private String runCommandForSingleLine(String command) {
        BufferedReader br = null;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            return line == null ? "unknown" : line.trim();
        } catch (Exception e) {
            return "unknown";
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
