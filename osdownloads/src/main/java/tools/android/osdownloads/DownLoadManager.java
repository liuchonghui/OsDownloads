package tools.android.osdownloads;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;


public class DownLoadManager {

    /**
     * @param apkid       标示
     * @param url         地址
     * @param fileName    文件名
     * @param description 描述 (通知栏提示)
     * @param title       描述 (通知栏提示)
     * @param notify      boolean 通知栏 Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED 显示；Downloads.VISIBILITY_HIDDEN 不显示
     * @param mimetype    文件类型
     * @return
     */
    public static Uri download(Context context, String apkid, String url, String fileName,String title, String description, boolean notify, String mimetype) {
        if (null == context || TextUtils.isEmpty(url) || TextUtils.isEmpty(mimetype)) {
            return null;
        }
        ContentValues values = new ContentValues();
        values.put(Downloads.COLUMN_URI, url);
        values.put(Downloads.COLUMN_NOTIFICATION_PACKAGE, context.getPackageName());
        values.put(Downloads.COLUMN_DESCRIPTION, description);
        values.put(Downloads.COLUMN_VISIBILITY, notify ? Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED : Downloads.VISIBILITY_HIDDEN);
        values.put(Downloads.COLUMN_MIME_TYPE, mimetype);
        values.put(Downloads.COLUMN_FILE_NAME_HINT, TextUtils.isEmpty(fileName) ? (System.currentTimeMillis() + "") : fileName);
        if(!TextUtils.isEmpty(title)){
            values.put(Downloads.COLUMN_TITLE, title);
        }

        if (!TextUtils.isEmpty(apkid)) {
            values.put(Downloads.COLUMN_APKID, apkid);
        }
        return context.getContentResolver().insert(Downloads.CONTENT_URI, values);
    }

    /**
     * @param context
     * @param pkg         包名
     * @param url         　　 地址
     * @param fileName    文件名
     * @param description 描述 (通知栏提示)
     * @param title       描述 (通知栏提示)
     * @param notify      boolean 通知栏 Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED 显示；Downloads.VISIBILITY_HIDDEN 不显示
     * @return
     */
    public static Uri downloadApk(Context context, String pkg, String url, String fileName, String title, String description, boolean notify) {
        return download(context, pkg, url, fileName, title, description, notify, Constants.MIMETYPE_APK);
    }

    /**
     * 暂停
     *
     * @param context
     * @param uri
     */
    public static void pauseDownload(Context context, Uri uri) {

        if (null == uri || null == context) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(Downloads.COLUMN_CONTROL, Downloads.CONTROL_PAUSED);
        context.getContentResolver().update(uri, values, null, null);
    }

    /**
     * 继续
     *
     * @param context
     * @param uri
     */
    public static void resumeDownload(Context context, Uri uri) {

        if (null == uri || null == context) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(Downloads.COLUMN_CONTROL, Downloads.CONTROL_RUN);
        context.getContentResolver().update(uri, values, null, null);
    }

    /**
     * 删除
     *
     * @param context
     * @param uri
     */
    public static void deleteDownload(Context context, Uri uri) {
        if (null == uri || null == context) {
            return;
        }
        Cursor cursor = null;
        String fileName;
        cursor = context.getContentResolver().query(uri, new String[]{Downloads._DATA}, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                fileName = cursor.getString(0);
                if (!TextUtils.isEmpty(fileName)) {
                    File f = new File(fileName);
                    if (f.exists()) {
                        f.delete();
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        context.getContentResolver().delete(uri, null, null);
    }

    /**
     * 安装
     *
     * @param context
     * @param fileName
     */
    public void install(Context context, String fileName) {

        if (null == context || TextUtils.isEmpty(fileName)) {
            return;
        }

        File file = new File(fileName);
        if (!file.exists()) {
            return;
        }

        Uri path = Uri.parse(fileName);
        if (path.getScheme() == null) {
            path = Uri.fromFile(new File(fileName));
        }
        Intent activityIntent = new Intent(Intent.ACTION_VIEW);
        activityIntent.setDataAndType(path, Constants.MIMETYPE_APK);
        activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(activityIntent);
        } catch (ActivityNotFoundException ex) {
        }
    }

    /**
     * 根据包名和url 查询任务
     *
     * @param apkid 包名
     * @param url   地址
     * @return
     */
    public DownloadState queryState(Context context, String apkid, String url) {
        Cursor cursor = context.getContentResolver().query(
                Downloads.CONTENT_URI,
                new String[]{Downloads._ID, Downloads.COLUMN_CURRENT_BYTES, Downloads.COLUMN_TOTAL_BYTES, Downloads._DATA,
                        Downloads.COLUMN_CONTROL, Downloads.COLUMN_STATUS,},
                " " + Downloads.COLUMN_APKID + "=? and " + Downloads.COLUMN_URI + "=? ", new String[]{apkid, url}, null);
        return getState(context, cursor);
    }

    /**
     * 根据 url 查询 下载任务
     *
     * @param url 地址
     * @return
     */
    public static DownloadState queryStateByUrl(Context context, String url) {
        Cursor cursor = context.getContentResolver().query(
                Downloads.CONTENT_URI,
                new String[]{Downloads._ID, Downloads.COLUMN_CURRENT_BYTES, Downloads.COLUMN_TOTAL_BYTES, Downloads._DATA,
                        Downloads.COLUMN_CONTROL, Downloads.COLUMN_STATUS,}, " " + Downloads.COLUMN_URI + "=? ", new String[]{url}, null);
        return getState(context, cursor);
    }

    private static DownloadState getState(Context context, Cursor cursor) {
        if (null == cursor) {
            return new DownloadState(DownloadState.NUll);
        }

        if (cursor.moveToFirst()) {
            int status = cursor.getInt(cursor.getColumnIndex(Downloads.COLUMN_STATUS));
            int _id = cursor.getInt(cursor.getColumnIndex(Downloads._ID));
            int total = cursor.getInt(cursor.getColumnIndex(Downloads.COLUMN_TOTAL_BYTES));
            int load = cursor.getInt(cursor.getColumnIndex(Downloads.COLUMN_CURRENT_BYTES));
            Uri uri = ContentUris.withAppendedId(Downloads.CONTENT_URI, _id);
            String filePath = cursor.getString(cursor.getColumnIndex(Downloads._DATA));
            int control = cursor.getInt(cursor.getColumnIndex(Downloads.COLUMN_CONTROL));

            cursor.close();

            // check file
            if (TextUtils.isEmpty(filePath)) {
                deleteDownload(context, uri);
                return new DownloadState(DownloadState.NUll);
            }

            File file = new File(filePath);
            if (!file.exists()) {
                deleteDownload(context, uri);
                return new DownloadState(DownloadState.NUll);
            }

            if (Downloads.isStatusSuccess(status)) {
                return new DownloadState(DownloadState.SUCCESS).setSuccessData(uri, filePath);
            } else if (Downloads.isStatusError(status)) {
                return new DownloadState(DownloadState.FAILE).setUri(uri);
            } else if (Downloads.isStatusSuspended(status) || control == Downloads.CONTROL_PAUSED) {
                return new DownloadState(DownloadState.PAUSE).setRunningData(uri, load, total);
            } else {
                return new DownloadState(DownloadState.RUNNING).setRunningData(uri, load, total);
            }
        } else {
            cursor.close();
            return new DownloadState(DownloadState.NUll);
        }
    }

    /**
     * 安装完之后删除下载任务
     *
     * @param apkid 包名
     */
    public static void deleteHasInstalled(Context context, String apkid) {

        if (null == context || TextUtils.isEmpty(apkid)) {
            return;
        }
        String where = Downloads.COLUMN_APKID + "=\"" + apkid + "\"";
        Cursor cursor = null;
        String fileName;
        cursor = context.getContentResolver().query(Downloads.CONTENT_URI, new String[]{Downloads._DATA}, where, null, null);
        if (cursor == null) {
            return;
        }

        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                fileName = cursor.getString(0);
                if (!TextUtils.isEmpty(fileName)) {
                    File f = new File(fileName);
                    if (f.exists()) {
                        f.delete();
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        context.getContentResolver().delete(Downloads.CONTENT_URI, where, null);
    }
}
