package tools.android.osdownloads;

import java.util.HashMap;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.RemoteViews;

class DownloadNotification {

	Context mContext;
	public NotificationManager mNotificationMgr;
	HashMap<Long, Notification> mNotifications;

	static final String WHERE_RUNNING = "(" + Downloads.COLUMN_STATUS + " == '192') AND (" + Downloads.COLUMN_VISIBILITY + " IS NULL OR "
	        + Downloads.COLUMN_VISIBILITY + " == '" + Downloads.VISIBILITY_VISIBLE + "' OR " + Downloads.COLUMN_VISIBILITY + " == '"
	        + Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED + "')";
	static final String WHERE_RUNNING_PAUSE = "(" + Downloads.COLUMN_STATUS + " == '" + Downloads.STATUS_RUNNING_PAUSED + "') AND ("
	        + Downloads.COLUMN_VISIBILITY + " IS NULL OR " + Downloads.COLUMN_VISIBILITY + " == '" + Downloads.VISIBILITY_VISIBLE + "' OR "
	        + Downloads.COLUMN_VISIBILITY + " == '" + Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED + "')";
	static final String WHERE_COMPLETED = Downloads.COLUMN_STATUS + " >= '200' AND " + Downloads.COLUMN_VISIBILITY + " == '"
	        + Downloads.VISIBILITY_VISIBLE_NOTIFY_COMPLETED + "'";

	/**
	 * Constructor
	 * 
	 * @param ctx
	 *            The context to use to obtain access to the Notification Service
	 */
	DownloadNotification(Context ctx) {
		mContext = ctx;
		mNotificationMgr = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotifications = new HashMap<Long, Notification>();
	}

	/*
	 * Update the notification ui.
	 */
	public void updateNotification() {
		updateActiveNotification();
	}

	private void updateActiveNotification() {
		// Columns match projection in query above
		Cursor cursor = null;
		try {
			cursor = mContext.getContentResolver().query(
			        Downloads.CONTENT_URI,
			        new String[] { Downloads._ID, Downloads.COLUMN_FILE_NAME_HINT, Downloads.COLUMN_TITLE, Downloads.COLUMN_DESCRIPTION,
			                Downloads.COLUMN_NOTIFICATION_PACKAGE, Downloads.COLUMN_NOTIFICATION_CLASS, Downloads.COLUMN_CURRENT_BYTES,
			                Downloads.COLUMN_TOTAL_BYTES, Downloads.COLUMN_STATUS }, WHERE_RUNNING_PAUSE, null, Downloads._ID);
			if (cursor != null) {
				for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
					long id = cursor.getLong(cursor.getColumnIndex(Downloads._ID));
					if (mNotificationMgr != null) {
						mNotificationMgr.cancel((int) id);
					}
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		// Active downloads
		Cursor c = null;
		try {
			c = mContext.getContentResolver().query(
			        Downloads.CONTENT_URI,
			        new String[] { Downloads._ID, Downloads.COLUMN_FILE_NAME_HINT, Downloads.COLUMN_TITLE, Downloads.COLUMN_DESCRIPTION, Downloads.COLUMN_NOTIFICATION_PACKAGE,
			                Downloads.COLUMN_NOTIFICATION_CLASS, Downloads.COLUMN_CURRENT_BYTES, Downloads.COLUMN_TOTAL_BYTES, Downloads.COLUMN_STATUS },
			        WHERE_RUNNING, null, Downloads._ID);

			if (c == null) {
				return;
			}

			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				int max = c.getInt(cursor.getColumnIndex(Downloads.COLUMN_TOTAL_BYTES));
				int progress = c.getInt(cursor.getColumnIndex(Downloads.COLUMN_CURRENT_BYTES));
				long id = c.getLong(cursor.getColumnIndex(Downloads._ID));
				String title = c.getString(cursor.getColumnIndex(Downloads.COLUMN_TITLE));
				String fileName = c.getString(cursor.getColumnIndex(Downloads.COLUMN_FILE_NAME_HINT));

				if(TextUtils.isEmpty(title)){
					title = fileName;
				}

				if (TextUtils.isEmpty(title)) {
					title = mContext.getResources().getString(R.string.download_unknown_filename);
				}

				String description = c.getString(c.getColumnIndex(Downloads.COLUMN_DESCRIPTION));

				Notification n = null;

				if(mNotifications.containsKey(id)){
					n = mNotifications.get(id);
					n.contentView.setProgressBar(R.id.progress_bar, max,progress,false);
					n.contentView.setTextViewText(R.id.progress_text, getDownloadingText(max, progress));
				}else {
					NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext,String.valueOf(id));
					builder.setChannelId(String.valueOf(id));
					builder.setSmallIcon(R.mipmap.logo);

					RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.notification_download_running);
					remoteViews.setImageViewResource(R.id.icon, R.mipmap.logo);
					remoteViews.setTextViewText(R.id.progress_title, title);
					if(!TextUtils.isEmpty(description)){
						remoteViews.setTextViewText(R.id.progress_description, description);
					}
					remoteViews.setTextViewText(R.id.progress_text, getDownloadingText(max, progress));
					remoteViews.setProgressBar(R.id.progress_bar, max,progress,false);

					builder.setCustomContentView(remoteViews);

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						NotificationChannel channel = new NotificationChannel(String.valueOf(id), "download", NotificationManager.IMPORTANCE_HIGH);
						mNotificationMgr.createNotificationChannel(channel);
					}

					n = builder.build();
					mNotifications.put(id,n);
				}
				mNotificationMgr.notify((int) id, n);
			}
        } catch (Exception e) {
			e.printStackTrace();
        }finally {
			if (c != null) {
				c.close();
			}
		}
	}

	/*
	 * Helper function to build the downloading text.
	 */
	private String getDownloadingText(long totalBytes, long currentBytes) {
		if (totalBytes <= 0) {
			return "";
		}
		long progress = currentBytes * 100 / totalBytes;
		StringBuilder sb = new StringBuilder();
		sb.append(progress);
		sb.append('%');
		return sb.toString();
	}

	public void clearNotifications() {
		if(null == mNotificationMgr || null == mNotifications || mNotifications.isEmpty()){
			return;
		}

		for(long id : mNotifications.keySet()){
			mNotificationMgr.cancel((int) id);
		}
	}
}
