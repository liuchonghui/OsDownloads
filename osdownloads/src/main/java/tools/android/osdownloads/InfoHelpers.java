/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.android.osdownloads;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class InfoHelpers {

    private static long MAX_SIZE = 100 * 1024 * 1024;

    public static Random sRandom = new Random(SystemClock.uptimeMillis());

    /**
     * Regex used to parse content-disposition headers
     */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    private InfoHelpers() {
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header is defined here:
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html This header provides a filename for
     * content that is going to be downloaded to the file system. We only support the attachment
     * type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        } catch (IllegalStateException ex) {
            // This function is defined as returning null when it can't parse the header
        }
        return null;
    }

    /**
     * Creates a filename (where the file should be saved) from a uri.
     */
    public static DownloadFileInfo generateSaveFile(Context context, String url, String hint, String contentDisposition, String contentLocation,
                                                    String mimeType, int destination, int contentLength) throws FileNotFoundException {

        /*
         * Don't download files that we won't be able to handle
         */
        if (destination == Downloads.DESTINATION_EXTERNAL || destination == Downloads.DESTINATION_CACHE_PARTITION_PURGEABLE) {
            if (mimeType == null) {
                return new DownloadFileInfo(null, null, Downloads.STATUS_NOT_ACCEPTABLE);
            }
        }
        String filename = chooseFilename(url, hint, contentDisposition, contentLocation, destination);

        // Split filename between base and extension
        // Add an extension if filename does not have one
        String extension = null;
        int dotIndex = filename.indexOf('.');
        if (dotIndex < 0) {
            extension = chooseExtensionFromMimeType(mimeType, true);
        } else {
            extension = chooseExtensionFromFilename(mimeType, destination, filename, dotIndex);
            filename = filename.substring(0, dotIndex);
        }

        /*
         * Locate the directory where the file will be saved
         */

        File base = null;
        StatFs stat = null;
        // DRM messages should be temporarily stored internally and then passed to
        // the DRM content provider
        if (destination == Downloads.DESTINATION_CACHE_PARTITION || destination == Downloads.DESTINATION_CACHE_PARTITION_PURGEABLE
                || destination == Downloads.DESTINATION_CACHE_PARTITION_NOROAMING || Constants.MIMETYPE_DRM_MESSAGE.equalsIgnoreCase(mimeType)) {
            // Saving to internal storage.
            base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            stat = new StatFs(base.getPath());

            /*
             * Check whether there's enough space on the target filesystem to save the file. Put a
             * bit of margin (in case creating the file grows the system by a few blocks).
             */
            int blockSize = stat.getBlockSize();
            long bytesAvailable = blockSize * ((long) stat.getAvailableBlocks() - 4);
            while (bytesAvailable < contentLength) {
                // Insufficient space; try discarding purgeable files.
                if (!discardPurgeableFiles(context, contentLength - bytesAvailable)) {
                    // No files to purge, give up.

                    return new DownloadFileInfo(null, null, Downloads.STATUS_FILE_ERROR);
                } else {
                    // Recalculate available space and try again.
                    stat.restat(base.getPath());
                    bytesAvailable = blockSize * ((long) stat.getAvailableBlocks() - 4);
                }
            }
        } else if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // Saving to external storage (SD card).
            base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            String root = base.getPath();
            stat = new StatFs(root);

            /*
             * Check whether there's enough space on the target filesystem to save the file. Put a
             * bit of margin (in case creating the file grows the system by a few blocks).
             */
            if (stat.getBlockSize() * ((long) stat.getAvailableBlocks() - 4) < contentLength) {
                // Insufficient space.
                return new DownloadFileInfo(filename, null, Downloads.STATUS_FILE_ERROR);
            }

            if (!base.isDirectory() && !base.mkdirs()) {
                // Can't create download directory, e.g. because a file called "download"
                // already exists at the root level, or the SD card filesystem is read-only.
                return new DownloadFileInfo(filename, null, Downloads.STATUS_FILE_ERROR);
            }
        } else if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && checkDataData()) {
            // Saving to external storage (/data/data).
            String path = context.getFilesDir().getPath() + "/" + Environment.DIRECTORY_DOWNLOADS;
            base = new File(path);
            if (!base.isDirectory() && !base.mkdirs()) {
                // Can't create download directory, e.g. because a file called "download"
                // already exists at the root level, or data/data filesystem is read-only.
                return new DownloadFileInfo(filename, null, Downloads.STATUS_FILE_ERROR);
            }
        } else {
            // No SD card found.
            return new DownloadFileInfo(null, null, Downloads.STATUS_FILE_ERROR);
        }

        boolean recoveryDir = Constants.RECOVERY_DIRECTORY.equalsIgnoreCase(filename + extension);

        filename = base.getPath() + File.separator + filename;

        /*
         * Generate a unique filename, create the file, return it.
         */
        if (Constants.LOGV) {
            Log.v(Constants.TAG, "target file: " + filename + extension);
        }

        String fullFilename = chooseUniqueFilename(destination, filename, extension, recoveryDir);
        if (fullFilename != null) {
            return new DownloadFileInfo(fullFilename, new FileOutputStream(fullFilename), 0);
        } else {
            return new DownloadFileInfo(fullFilename, null, Downloads.STATUS_FILE_ERROR);
        }
    }

    private static boolean checkDataData() {
        StatFs stat = new StatFs("/data/data");
        if (stat.getBlockSize() * ((long) stat.getAvailableBlocks()) >= MAX_SIZE) {
            return true;
        }
        return false;
    }

    private static String chooseFilename(String url, String hint, String contentDisposition, String contentLocation, int destination) {
        String filename = null;

        // First, try to use the hint from the application, if there's one
        if (filename == null && hint != null && !hint.endsWith("/")) {
            if (Constants.LOGV) {
                Log.v(Constants.TAG, "getting filename from hint");
            }
            int index = hint.lastIndexOf('/') + 1;
            if (index > 0) {
                filename = hint.substring(index);
            } else {
                filename = hint;
            }
        }

        // If we couldn't do anything with the hint, move toward the content disposition
        if (filename == null && contentDisposition != null) {
            filename = parseContentDisposition(contentDisposition);
            if (filename != null) {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "getting filename from content-disposition");
                }
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = filename.substring(index);
                }
            }
        }

        // If we still have nothing at this point, try the content location
        if (filename == null && contentLocation != null) {
            String decodedContentLocation = Uri.decode(contentLocation);
            if (decodedContentLocation != null && !decodedContentLocation.endsWith("/") && decodedContentLocation.indexOf('?') < 0) {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "getting filename from content-location");
                }
                int index = decodedContentLocation.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = decodedContentLocation.substring(index);
                } else {
                    filename = decodedContentLocation;
                }
            }
        }

        // If all the other http-related approaches failed, use the plain uri
        if (filename == null) {
            String decodedUrl = Uri.decode(url);
            if (decodedUrl != null && !decodedUrl.endsWith("/") && decodedUrl.indexOf('?') < 0) {
                int index = decodedUrl.lastIndexOf('/') + 1;
                if (index > 0) {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "getting filename from uri");
                    }
                    filename = decodedUrl.substring(index);
                }
            }
        }

        // Finally, if couldn't get filename from URI, get a generic filename
        if (filename == null) {
            if (Constants.LOGV) {
                Log.v(Constants.TAG, "using default filename");
            }
            filename = Constants.DEFAULT_DL_FILENAME;
        }

        // filename = filename.replaceAll("[^a-zA-Z0-9\\.\\-_]+", "_");
        return filename;
    }

    private static String chooseExtensionFromMimeType(String mimeType, boolean useDefaults) {
        String extension = null;
        if (mimeType != null) {
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "adding extension from type");
                }
                extension = "." + extension;
            } else {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                }
            }
        }
        if (extension == null) {
            if (mimeType != null && mimeType.toLowerCase().startsWith("text/")) {
                if (mimeType.equalsIgnoreCase("text/html")) {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "adding default html extension");
                    }
                    extension = Constants.DEFAULT_DL_HTML_EXTENSION;
                } else if (useDefaults) {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "adding default text extension");
                    }
                    extension = Constants.DEFAULT_DL_TEXT_EXTENSION;
                }
            } else if (useDefaults) {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "adding default binary extension");
                }
                extension = Constants.DEFAULT_DL_BINARY_EXTENSION;
            }
        }
        return extension;
    }

    private static String chooseExtensionFromFilename(String mimeType, int destination, String filename, int dotIndex) {
        String extension = null;
        if (mimeType != null) {
            // Compare the last segment of the extension against the mime type.
            // If there's a mismatch, discard the entire extension.
            int lastDotIndex = filename.lastIndexOf('.');
            String typeFromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(filename.substring(lastDotIndex + 1));
            if (typeFromExt == null || !typeFromExt.equalsIgnoreCase(mimeType)) {
                extension = chooseExtensionFromMimeType(mimeType, false);
                if (extension != null) {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "substituting extension from type");
                    }
                } else {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "couldn't find extension for " + mimeType);
                    }
                }
            }
        }
        if (extension == null) {
            if (Constants.LOGV) {
                Log.v(Constants.TAG, "keeping extension");
            }
            extension = filename.substring(dotIndex);
        }
        return extension;
    }

    private static String chooseUniqueFilename(int destination, String filename, String extension, boolean recoveryDir) {
        String fullFilename = filename + extension;
        if (!new File(fullFilename).exists()
                && (!recoveryDir || (destination != Downloads.DESTINATION_CACHE_PARTITION
                && destination != Downloads.DESTINATION_CACHE_PARTITION_PURGEABLE && destination != Downloads.DESTINATION_CACHE_PARTITION_NOROAMING))) {
            return fullFilename;
        }
        filename = filename + Constants.FILENAME_SEQUENCE_SEPARATOR;
        /*
         * This number is used to generate partially randomized filenames to avoid collisions. It
         * starts at 1. The next 9 iterations increment it by 1 at a time (up to 10). The next 9
         * iterations increment it by 1 to 10 (random) at a time. The next 9 iterations increment it
         * by 1 to 100 (random) at a time. ... Up to the point where it increases by 100000000 at a
         * time. (the maximum value that can be reached is 1000000000) As soon as a number is
         * reached that generates a filename that doesn't exist, that filename is used. If the
         * filename coming in is [base].[ext], the generated filenames are [base]-[sequence].[ext].
         */
        int sequence = 1;
        for (int magnitude = 1; magnitude < 1000000000; magnitude *= 10) {
            for (int iteration = 0; iteration < 9; ++iteration) {
                fullFilename = filename + sequence + extension;
                if (!new File(fullFilename).exists()) {
                    return fullFilename;
                }
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "file with sequence number " + sequence + " exists");
                }
                sequence += sRandom.nextInt(magnitude) + 1;
            }
        }
        return null;
    }

    /**
     * Deletes purgeable files from the cache partition. This also deletes the matching database
     * entries. Files are deleted in LRU order until the total byte size is greater than
     * targetBytes.
     */
    public static final boolean discardPurgeableFiles(Context context, long targetBytes) {
        Cursor cursor = context.getContentResolver().query(
                Downloads.CONTENT_URI,
                null,
                "( " + Downloads.COLUMN_STATUS + " = '" + Downloads.STATUS_SUCCESS + "' AND " + Downloads.COLUMN_DESTINATION + " = '"
                        + Downloads.DESTINATION_CACHE_PARTITION_PURGEABLE + "' )", null, Downloads.COLUMN_LAST_MODIFICATION);
        if (cursor == null) {
            return false;
        }
        long totalFreed = 0;
        try {
            cursor.moveToFirst();
            while (!cursor.isAfterLast() && totalFreed < targetBytes) {
                File file = new File(cursor.getString(cursor.getColumnIndex(Downloads._DATA)));
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "purging " + file.getAbsolutePath() + " for " + file.length() + " bytes");
                }
                totalFreed += file.length();
                file.delete();
                long id = cursor.getLong(cursor.getColumnIndex(Downloads._ID));
                context.getContentResolver().delete(ContentUris.withAppendedId(Downloads.CONTENT_URI, id), null, null);
                cursor.moveToNext();
            }
        } finally {
            cursor.close();
        }
        if (Constants.LOGV) {
            if (totalFreed > 0) {
                Log.v(Constants.TAG, "Purged files, freed " + totalFreed + " for " + targetBytes + " requested");
            }
        }
        return totalFreed > 0;
    }

    /**
     * Returns whether the network is available
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            Log.w(Constants.TAG, "couldn't get connectivity manager");
        } else {
            NetworkInfo[] info = null;
            try {
                info = connectivity.getAllNetworkInfo();
            } catch (Exception e) {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "getAllNetworkInfo error ");
                }
            }

            if (info != null) {
                for (int i = 0; i < info.length; i++) {
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        if (Constants.LOGV) {
                            Log.v(Constants.TAG, "network is available");
                        }
                        return true;
                    }
                }
            }
        }
        if (Constants.LOGV) {
            Log.v(Constants.TAG, "network is not available");
        }
        return false;
    }

    /**
     * Returns whether the network is roaming
     */
    public static boolean isNetworkRoaming(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            Log.w(Constants.TAG, "couldn't get connectivity manager");
        } else {
            NetworkInfo info = connectivity.getActiveNetworkInfo();

            if (info != null && info.getType() == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService("phone");
                if (telephonyManager.isNetworkRoaming()) {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "network is roaming");
                    }
                    return true;
                } else {
                    if (Constants.LOGV) {
                        Log.v(Constants.TAG, "network is not roaming");
                    }
                }
            } else {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "not using mobile network");
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the filename looks legitimate
     */
    public static boolean isFilenameValid(String filename, Context mContext) {
        File dir = new File(filename).getParentFile();
        return dir.equals(mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
                || dir.equals(mContext.getFilesDir() + "/" + Environment.DIRECTORY_DOWNLOADS);
    }

}
