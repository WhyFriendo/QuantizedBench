package com.example.executorchllamademo;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModelDownloader {
    private static final String TAG = "ModelDownloader";
    private static final ExecutorService executor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static boolean partialFilesCleaned;

    /** Remove files abandoned by a previous process before any downloads start in this one. */
    public static synchronized void cleanAbandonedDownloads(File directory) {
        if (partialFilesCleaned) return;
        partialFilesCleaned = true;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".part") && !file.delete()) {
                Log.w(TAG, "Could not remove abandoned download: " + file);
            }
        }
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess();
        void onError(String errorMsg);
    }

    public static void downloadFile(String fileUrl, String destinationPath, DownloadCallback callback) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            callback.onError("Invalid URL");
            return;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            File temporaryFile = null;

            try {
                URL url = new URL(fileUrl);
                boolean redirect = false;
                int redirectCount = 0;
                
                do {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    connection.connect();
                    
                    int status = connection.getResponseCode();
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                        status == HttpURLConnection.HTTP_MOVED_PERM || 
                        status == HttpURLConnection.HTTP_SEE_OTHER) {
                        
                        redirect = true;
                        String newUrl = connection.getHeaderField("Location");
                        url = new URL(newUrl);
                        connection.disconnect();
                        redirectCount++;
                    } else {
                        redirect = false;
                    }
                } while (redirect && redirectCount < 5);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());
                }

                long fileLength = connection.getContentLengthLong();
                
                File outputFile = new File(destinationPath);
                File parentDir = outputFile.getParentFile();
                if (parentDir == null || (!parentDir.isDirectory() && !parentDir.mkdirs())) {
                    throw new Exception("Cannot create download directory");
                }

                // The final name must never point at a download that is still in progress.
                temporaryFile = File.createTempFile(outputFile.getName() + ".", ".part", parentDir);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                int lastProgress = -1;

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(temporaryFile)) {
                    while ((count = input.read(data)) != -1) {
                        output.write(data, 0, count);
                        total += count;
                        if (fileLength > 0) {
                            int progress = (int) Math.min(100L, total * 100 / fileLength);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                mainHandler.post(() -> callback.onProgress(progress));
                            }
                        }
                    }
                }

                if (total == 0 || (fileLength >= 0 && total != fileLength)) {
                    throw new Exception("Incomplete download: received " + total
                            + " of " + fileLength + " bytes");
                }

                Files.move(temporaryFile.toPath(), outputFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                temporaryFile = null;
                mainHandler.post(callback::onSuccess);

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            } finally {
                if (temporaryFile != null && temporaryFile.exists() && !temporaryFile.delete()) {
                    Log.w(TAG, "Could not delete partial download: " + temporaryFile);
                }
                if (connection != null) connection.disconnect();
            }
        });
    }

    /**
     * Fetches the remote file size in bytes via a HEAD request, following redirects. Returns -1
     * if the size could not be determined.
     */
    public static long fetchRemoteFileSize(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return -1;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(fileUrl);
            boolean redirect = false;
            int redirectCount = 0;

            do {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.connect();

                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER) {

                    redirect = true;
                    String newUrl = connection.getHeaderField("Location");
                    url = new URL(newUrl);
                    connection.disconnect();
                    redirectCount++;
                } else {
                    redirect = false;
                }
            } while (redirect && redirectCount < 5);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return -1;
            }

            return connection.getContentLengthLong();
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch remote file size for " + fileUrl, e);
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
