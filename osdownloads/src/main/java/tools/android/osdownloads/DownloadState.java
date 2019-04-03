package tools.android.osdownloads;

import android.net.Uri;

public class DownloadState {

	/**
	 * 没有这条记录
	 */
	public static final int NUll = 0;
	
	/**
	 * 下载中 
	 */
	public static final int RUNNING = 1;

	/**
	 * 下载成功
	 */
	public static final int SUCCESS = 2;

	/**
	 * 暂停 
	 */
	public static final int PAUSE = 3;

	/**
	 * 下载失败
	 */
	public static final int FAILE = 4;

	public Uri uri;
	public long total;
	public long load;
	public String path;
	public int state;

	public int getState() {
		return state;
	}

	public DownloadState(int state) {
		this.state = state;
	}

	public DownloadState setUri(Uri mUri) {
		this.uri = mUri;
		return this;
	}

	public DownloadState setSuccessData(Uri mUri, String mPath) {
		this.uri = mUri;
		this.path = mPath;
		return this;
	}

	/**
	* @Title: setRunningData
	* @Description: init进度
	* @author:wentao
	* @param @param mUri
	* @param @param mLoad 已下载字节
	* @param @param mTotal 总字节
	* @throws
	*/
	public DownloadState setRunningData(Uri mUri, long mLoad, long mTotal) {
		this.uri = mUri;
		this.load = mLoad;
		this.total = mTotal;
		return this;
	}

	public String getPath() {
		return path;
	}

	public Uri getUri() {
		return uri;
	}

	public long getTotal() {
		return total;
	}

	public long getLoad() {
		return load;
	}

}
