package net.runelite.client.plugins.runemod;

import lombok.SneakyThrows;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

//https://runemod.net/app_download/runemod_master.zip"

// This class downloads a file from a URL.
class RuneMod_Launcher implements Runnable {
    volatile boolean run = true;
    private boolean firstrun = true;
    private RuneMod_statusUI runeMod_statusUI;
    public String app_root = System.getProperty("user.home") + "\\.runemod\\";

    RuneMod_Launcher(RuneMod_statusUI statusUI) {
        runeMod_statusUI = statusUI;
    }

    @SneakyThrows
    @Override
    public void run() {
        if (run) {
            if (firstrun) {
                onStart();
                firstrun = false;
            }
        }
    }

    @SneakyThrows
    public void onStart() {
        runeMod_statusUI.dialog.setTitle("RuneMod Launcher");
        if(Files.exists(Paths.get(app_root+"WindowsNoEditor\\"+"RuneMod.exe"))) {
            runeMod_statusUI.SetMessage("Launching RuneMod.exe...");
            LaunchApp(app_root+"WindowsNoEditor\\"+"RuneMod.exe");
        } else {
            Files.createDirectories(Paths.get(app_root));
            String zipFilePath = app_root+"WindowsNoEditor.zip";
            downloadZip("https://runemod.net/app_download/WindowsNoEditor.zip", zipFilePath);
            UnzipFile(zipFilePath);
            try {
                Files.delete(Paths.get(zipFilePath));
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(Files.exists(Paths.get(app_root+"WindowsNoEditor\\"+"RuneMod.exe"))) {
                runeMod_statusUI.SetMessage("Launching RuneMod.exe...");
                LaunchApp(app_root+"WindowsNoEditor\\"+"RuneMod.exe");
            } else  {
                runeMod_statusUI.SetMessage("Launch failed: Runemod.exe could not be found, ask for help in RuneMod discord");
            }

        }
    }

    public void downloadZip(String URL, String filePath) {
        System.out.println(app_root);
        runeMod_statusUI.SetMessage("Starting RuneMod download...");
        try {

            URL url = new URL(URL);



            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            long fileSize = conn.getContentLengthLong();



            BufferedInputStream bis = new BufferedInputStream(url.openStream());
            FileOutputStream fis = new FileOutputStream(filePath);

            byte[] buffer = new byte[8192];
            int count = 0;

            int counter = 0;
            while ((count = bis.read(buffer, 0, buffer.length)) != -1) {
                counter++;
                //System.out.println(counter);
                runeMod_statusUI.SetMessage("Downloaded: "+(((int)fis.getChannel().size()/100000)/10.0f)+" / " + (((int)fileSize/100000)/10.0f) + "mb");
                //runeMod_status.message.setText("Progress"+counter);
                fis.write(buffer, 0, count);
            }

            fis.close();
            bis.close();

        } catch (IOException e) {
            runeMod_statusUI.SetMessage("RuneMod Download failed");
            e.printStackTrace();
        }
    }

    public void UnzipFile(String filePath) throws IOException {
        runeMod_statusUI.SetMessage("Unzipping...");
        try (InputStream is = Files.newInputStream(Paths.get(filePath));
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry = zis.getNextEntry();
            int noBytesDecompressed = -1;
            while (entry != null) {
                Path path = Paths.get(app_root).resolve(entry.getName()).normalize();
                if (!path.startsWith(app_root)) {
                    throw new IOException("Invalid ZIP");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(path);
                } else {
                    try (OutputStream os = Files.newOutputStream(path)) {
                        byte[] buffer0 = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer0)) > 0) {
                            noBytesDecompressed = noBytesDecompressed+len;
                            runeMod_statusUI.SetMessage("UnZipped "+ noBytesDecompressed/1000000 + "mb");
                            os.write(buffer0, 0, len);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
        runeMod_statusUI.SetMessage("UnZipping finished ");
    }

    public void LaunchApp(String filePath) throws IOException {
        runeMod_statusUI.SetMessage("Launching...");
        Runtime runtime = Runtime.getRuntime();     //getting Runtime object
        try
        {
            runeMod_statusUI.SetMessage("Executing RuneMod.exe...");
            runtime.exec(filePath);        //opens "sample.txt" in notepad
            runeMod_statusUI.dialog.dispose();
        }
        catch (IOException e)
        {
            runeMod_statusUI.SetMessage("Failed to execute RuneMod.exe...");
            e.printStackTrace();
        }
    }

}
