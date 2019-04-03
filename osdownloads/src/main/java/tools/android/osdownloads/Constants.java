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

public class Constants {
    public static final String ACTION_HIDE = "android.intent.action.tv_DOWNLOAD_HIDE";
    public static final String ACTION_LIST = "android.intent.action.tv_DOWNLOAD_LIST";
    public static final String ACTION_OPEN = "android.intent.action.tv_DOWNLOAD_OPEN";
    public static final String ACTION_RETRY = "android.intent.action.tv_DOWNLOAD_RETRY";
    public static final String ACTION_WAKEUP = "android.intent.action.tv_DOWNLOAD_WAKEUP";
    public static final int BUFFER_SIZE = 4096;
    public static final String DEFAULT_DL_BINARY_EXTENSION = ".bin";
    public static final String DEFAULT_DL_FILENAME = "downloadfile";
    public static final String DEFAULT_DL_HTML_EXTENSION = ".html";
    public static final String DEFAULT_DL_TEXT_EXTENSION = ".txt";
    public static final String DEFAULT_USER_AGENT = "AndroidDownloadManager";
    public static final String ETAG = "etag";
    public static final String FAILED_CONNECTIONS = "numfailed";
    public static final String FILENAME_SEQUENCE_SEPARATOR = "-";
    public static final String KNOWN_SPURIOUS_FILENAME = "lost+found";
    public static final int MAX_DOWNLOADS = 1000;
    public static final int MAX_REDIRECTS = 5; // http Jump
    public static final int MAX_RETRIES = 5; // retry
    public static final int MAX_RETRY_AFTER = 24 * 60 * 60;
    public static final String MEDIA_SCANNED = "scanned";
    public static final String MIMETYPE_APK = "application/vnd.android.package-archive";
    public static final String MIMETYPE_DRM_MESSAGE = "application/vnd.oma.drm.message";
    public static final String MIMETYPE_HTML = "text/html";
    public static final int MIN_PROGRESS_STEP = 4096;
    public static final long MIN_PROGRESS_TIME = 1500L;
    public static final int MIN_RETRY_AFTER = 30;
    public static final String NO_SYSTEM_FILES = "no_system";
    public static final String OTA_UPDATE = "otaupdate";
    public static final String RECOVERY_DIRECTORY = "recovery";
    public static final String RETRY_AFTER_X_REDIRECT_COUNT = "method";
    public static final int RETRY_FIRST_DELAY = 30;
    public static final String UID = "uid";

    public static final String AUTHORITY = "tools.android.osdownloads";

    public static final String TAG = "OSDownloadManager";
    public static final boolean LOGV = BuildConfig.DEBUG;

}