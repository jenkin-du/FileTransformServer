package com.uestc.net.callback;

import java.io.IOException;

/**
 * <pre>
 *     author : jenkin
 *     e-mail : jekin-du@foxmail.com
 *     time   : 2019/03/11
 *     desc   : 文件传输监听器
 *     version: 1.0
 * </pre>
 */
public interface FileTransportListener {


	/**
	 * 传输进度
	 *
	 * @param progress
	 *            传输进度
	 * @param totalSize
	 *            总大小
	 */
	void onProgress(double progress, long totalSize);

	/**
	 * 下载完成
	 *
	 * @throws IOException
	 */
	void onComplete(boolean isSuccess, String tempPath) throws IOException;

	/**
	 * 
	 * @param exception
	 */
	void onExceptionCaught(String exception);

}
