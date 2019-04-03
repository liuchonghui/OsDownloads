/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.text.TextUtils;
import android.util.Log;

public class DownloadProvider extends ContentProvider {
    private static final String DB_NAME = "downloads.db";
    private static final String TABLE_NAME = "os_downloads";
    private static final int DOWNLOADS = 1;
    private static final int DOWNLOADS_ID = 2;
    private static final int DB_VERSION = 10;

    private static final int DB_VERSION_UPGRADE = 10;

    private static final String DOWNLOAD_LIST_TYPE = "vnd.android.cursor.dir/osdownlods";
    private static final String DOWNLOAD_TYPE = "vnd.android.cursor.item/osdownloads";
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private SQLiteOpenHelper mOpenHelper = null;

    static {
        sURIMatcher.addURI(Downloads.AUTHORITIES, "download", DOWNLOADS);
        sURIMatcher.addURI(Downloads.AUTHORITIES, "download/#", DOWNLOADS_ID);
    }

    /**
     * Creates and updated database on demand when opening it.
     */
    private final class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(final Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        /**
         * Creates database the first time we try to open it.
         */
        @Override
        public void onCreate(final SQLiteDatabase db) {
            if (Constants.LOGV) {
                Log.v(Constants.TAG, "populating new database");
            }
            createTable(db);
        }

        /**
         * Updates the database format when a content provider is used with a database that was
         * created with a different format.
         */
        @Override
        public void onUpgrade(final SQLiteDatabase db, int oldV, final int newV) {
            if (oldV < DB_VERSION_UPGRADE) {
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "downloads.db onUpgrade");
                }
                dropTable(db);
                createTable(db);
            }
        }
    }

    private void dropTable(SQLiteDatabase db) {
        try {
            db.execSQL("DROP TABLE IF EXISTS downloads");
            return;
        } catch (SQLException localSQLException) {
            if (Constants.LOGV) {
                Log.e(Constants.TAG, "couldn't drop table in downloads database");
                throw localSQLException;
            }
        }
    }

    private void createTable(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE TABLE " + TABLE_NAME + "(" + Downloads._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," + Downloads.COLUMN_URI + " TEXT, "
                    + Constants.RETRY_AFTER_X_REDIRECT_COUNT + " INTEGER, " + Downloads.COLUMN_APP_DATA + " TEXT, " + Downloads.COLUMN_NO_INTEGRITY
                    + " BOOLEAN, " + Downloads.COLUMN_FILE_NAME_HINT + " TEXT, " + Constants.OTA_UPDATE + " BOOLEAN, " + Downloads._DATA + " TEXT, "
                    + Downloads.COLUMN_MIME_TYPE + " TEXT, " + Downloads.COLUMN_DESTINATION + " INTEGER, " + Constants.NO_SYSTEM_FILES + " BOOLEAN, "
                    + Downloads.COLUMN_VISIBILITY + " INTEGER, " + Downloads.COLUMN_CONTROL + " INTEGER, " + Downloads.COLUMN_STATUS + " INTEGER, "
                    + Constants.FAILED_CONNECTIONS + " INTEGER, " + Downloads.COLUMN_LAST_MODIFICATION + " BIGINT, "
                    + Downloads.COLUMN_NOTIFICATION_PACKAGE + " TEXT, " + Downloads.COLUMN_NOTIFICATION_CLASS + " TEXT, "
                    + Downloads.COLUMN_NOTIFICATION_EXTRAS + " TEXT, " + Downloads.COLUMN_COOKIE_DATA + " TEXT, " + Downloads.COLUMN_USER_AGENT
                    + " TEXT, " + Downloads.COLUMN_REFERER + " TEXT, " + Downloads.COLUMN_TOTAL_BYTES + " INTEGER, " + Downloads.COLUMN_CURRENT_BYTES
                    + " INTEGER, " + Constants.ETAG + " TEXT, " + Constants.UID + " INTEGER, " + Downloads.COLUMN_OTHER_UID + " INTEGER, "
                    + Downloads.COLUMN_TITLE + " TEXT, " + Downloads.COLUMN_DESCRIPTION + " TEXT, " + Downloads.COLUMN_APKID + " TEXT, "
                    + Constants.MEDIA_SCANNED + " BOOLEAN);");

        } catch (SQLException ex) {
            Log.e(Constants.TAG, "couldn't create table in downloads database");
            throw ex;
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case DOWNLOADS:
                return DOWNLOAD_LIST_TYPE;
            case DOWNLOADS_ID:
                return DOWNLOAD_TYPE;
            default:
                Log.w(Constants.TAG, "calling getType on an unknown URI: " + uri);
                return null;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = getDatabase();
        Cursor ret = null;

        if (db == null) {
            return ret;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        int match = sURIMatcher.match(uri);
        switch (match) {
            case DOWNLOADS:
                qb.setTables(TABLE_NAME);
                break;
            case DOWNLOADS_ID:
                qb.setTables(TABLE_NAME);
                qb.appendWhere(Downloads._ID + "=");
                qb.appendWhere(uri.getPathSegments().get(1));
                break;
            default:
                if (Constants.LOGV) {
                    Log.v(Constants.TAG, "querying unknown URI: " + uri);
                    throw new IllegalArgumentException("Unknown URI: " + uri);
                }
        }

        ret = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (ret != null) {
            ret.setNotificationUri(getContext().getContentResolver(), uri);
        } else {
            if (Constants.LOGV) {
                Log.v(Constants.TAG, "query failed in downloads database");
            }
        }
        return ret;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = getDatabase();

        Uri ret = null;
        if (null == db) {
            return ret;
        }

        int match = sURIMatcher.match(uri);
        if (match != DOWNLOADS) {
            Log.w(Constants.TAG, "calling insert on an unknown/invalid URI: " + uri);
            return null;
        }
        ContentValues filteredValues = new ContentValues();
        copyString(Downloads.COLUMN_APKID, values, filteredValues);
        copyString(Downloads.COLUMN_URI, values, filteredValues);
        copyString(Downloads.COLUMN_APP_DATA, values, filteredValues);
        copyBoolean(Downloads.COLUMN_NO_INTEGRITY, values, filteredValues);
        copyString(Downloads.COLUMN_FILE_NAME_HINT, values, filteredValues);
        copyString(Downloads.COLUMN_MIME_TYPE, values, filteredValues);

        Integer dest = values.getAsInteger(Downloads.COLUMN_DESTINATION);
        if (dest != null) {
            filteredValues.put(Downloads.COLUMN_DESTINATION, dest);
        }
        Integer vis = values.getAsInteger(Downloads.COLUMN_VISIBILITY);
        if (vis == null) {
            if (dest == Downloads.DESTINATION_EXTERNAL) {
                filteredValues.put(Downloads.COLUMN_VISIBILITY, Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            } else {
                filteredValues.put(Downloads.COLUMN_VISIBILITY, Downloads.VISIBILITY_HIDDEN);
            }
        } else {
            filteredValues.put(Downloads.COLUMN_VISIBILITY, vis);
        }
        copyInteger(Downloads.COLUMN_CONTROL, values, filteredValues);

        filteredValues.put(Downloads.COLUMN_STATUS, Downloads.STATUS_PENDING);
        filteredValues.put(Downloads.COLUMN_LAST_MODIFICATION, System.currentTimeMillis());
        String notification_package = values.getAsString(Downloads.COLUMN_NOTIFICATION_PACKAGE);
        String notification_class = values.getAsString(Downloads.COLUMN_NOTIFICATION_CLASS);
        if (!TextUtils.isEmpty(notification_package) && !TextUtils.isEmpty(notification_class)) {
            filteredValues.put(Downloads.COLUMN_NOTIFICATION_PACKAGE, notification_package);
            filteredValues.put(Downloads.COLUMN_NOTIFICATION_CLASS, notification_class);
        }
        copyString(Downloads.COLUMN_NOTIFICATION_EXTRAS, values, filteredValues);
        copyString(Downloads.COLUMN_COOKIE_DATA, values, filteredValues);
        copyString(Downloads.COLUMN_USER_AGENT, values, filteredValues);
        copyString(Downloads.COLUMN_REFERER, values, filteredValues);
        copyInteger(Downloads.COLUMN_OTHER_UID, values, filteredValues);
        filteredValues.put(Constants.UID, Binder.getCallingUid());
        copyInteger(Constants.UID, values, filteredValues);
        copyString(Downloads.COLUMN_TITLE, values, filteredValues);

        copyString(Downloads.COLUMN_DESCRIPTION, values, filteredValues);
        copyInteger(Downloads.COLUMN_TOTAL_BYTES, values, filteredValues);

        Context context = getContext();
        context.startService(new Intent(context, DownloadService.class));

        long rowID = -1;
        try {
            rowID = db.insert(TABLE_NAME, null, filteredValues);
        } catch (Exception e) {
            if (Constants.LOGV) {
                Log.v(Constants.TAG, "insert error :" + e);
            }
        }

        if (rowID != -1) {
            context.startService(new Intent(context, DownloadService.class));
            ret = Uri.parse(Downloads.CONTENT_URI + "/" + rowID);
            context.getContentResolver().notifyChange(uri, null);
        }
        return ret;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase db = getDatabase();

        if (null == db) {
            if (Constants.LOGV) {
                Log.w(Constants.TAG, " db null ");
            }
            return count;
        }

        int match = sURIMatcher.match(uri);
        switch (match) {
            case DOWNLOADS:
            case DOWNLOADS_ID:
                String where;
                if (!TextUtils.isEmpty(selection)) {
                    if (match == DOWNLOADS) {
                        where = "( " + selection + " )";
                    } else {
                        where = "( " + selection + " ) AND";
                    }
                } else {
                    where = "";
                }

                if (match == DOWNLOADS_ID) {
                    String segment = uri.getPathSegments().get(1);
                    long rowId = Long.parseLong(segment);
                    where += " ( _id = " + rowId + " ) ";
                }

                try {
                    count = db.delete(TABLE_NAME, where, selectionArgs);
                    getContext().getContentResolver().notifyChange(uri, null);
                } catch (Exception e) {
                    count = 0;
                    if (Constants.LOGV) {
                        Log.w(Constants.TAG, " delete error : " + e);
                    }
                }
                break;
            default:
                if (Constants.LOGV) {
                    Log.w(Constants.TAG, "deleting unknown/invalid URI: " + uri);
                }
                count = 0;
                break;
        }
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        // delete check sql Helpers.validateSelection(where, sAppReadableColumnsSet);
        SQLiteDatabase db = getDatabase();
        int count = 0;
        long rowId = 0;
        boolean startService = false;

        if (null == db) {
            if (Constants.LOGV) {
                Log.w(Constants.TAG, " db null ");
            }
            return count;
        }

        Integer i = values.getAsInteger(Downloads.COLUMN_CONTROL);
        if (i != null) {
            values.put(Downloads.COLUMN_CONTROL, i);
            startService = true;
        }

        int match = sURIMatcher.match(uri);
        switch (match) {
            case DOWNLOADS:
            case DOWNLOADS_ID:
                String myWhere;
                if (where != null) {
                    if (match == DOWNLOADS) {
                        myWhere = "( " + where + " )";
                    } else {
                        myWhere = "( " + where + " ) AND ";
                    }
                } else {
                    myWhere = "";
                }
                if (match == DOWNLOADS_ID) {
                    String segment = uri.getPathSegments().get(1);
                    rowId = Long.parseLong(segment);
                    myWhere += " ( " + Downloads._ID + " = " + rowId + " ) ";
                }
                if (values.size() > 0) {
                    try {
                        count = db.update(TABLE_NAME, values, myWhere, whereArgs);
                    } catch (Exception e) {
                        if (Constants.LOGV) {
                            Log.w(Constants.TAG, " uodate error : " + e);
                        }
                    }
                }
                break;
            default:
                Log.d(Constants.TAG, "updating unknown/invalid URI: " + uri);
                throw new UnsupportedOperationException("Cannot update URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        if (startService) {
            Context context = getContext();
            context.startService(new Intent(context, DownloadService.class));
        }
        return count;
    }

    private static final void copyInteger(String key, ContentValues from, ContentValues to) {
        Integer i = from.getAsInteger(key);
        if (i != null) {
            to.put(key, i);
        }
    }

    private static final void copyBoolean(String key, ContentValues from, ContentValues to) {
        Boolean b = from.getAsBoolean(key);
        if (b != null) {
            to.put(key, b);
        }
    }

    private static final void copyString(String key, ContentValues from, ContentValues to) {
        String s = from.getAsString(key);
        if (s != null) {
            to.put(key, s);
        }
    }

    private SQLiteDatabase getDatabase() {
        SQLiteDatabase db = null;
        try {
            db = mOpenHelper.getWritableDatabase();
        } catch (Exception e) {
            onHandleException(e);
        }
        return db;
    }

    private void onHandleException(Exception e) {
        if (Constants.LOGV) {
            throw new RuntimeException(e);
        } else {
            e.printStackTrace();
        }
    }
}
