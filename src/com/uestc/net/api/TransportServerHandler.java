package com.uestc.net.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uestc.net.callback.FileTransportListener;
import com.uestc.net.protocol.Message;
import com.uestc.net.util.MD5Util;
import com.uestc.net.util.SharedPreferenceUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * 服务器使用
 */
public class TransportServerHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(TransportServerHandler.class);


	public TransportServerHandler() {

	}

	// 文件传输监听器
	private FileTransportListener fileLisenter = new FileTransportListener() {

		@Override
		public void onProgress(double progress, long totalSize) {

		}

		@Override
		public void onExceptionCaught(String exception) {

		}

		@Override
		public void onComplete(boolean isSuccess, String tempPath) throws IOException {

		}

	};

	public void handleMessage(ChannelHandlerContext ctx, Message msg) {

		String action = msg.getAction();
		LOGGER.debug("action:" + action);
		if (action != null) {
			switch (action) {
			// 下载请求
			case Message.Action.FILE_DOWNLOAD_REQUEST:
				handleFileDownloadRequest(ctx, msg);
				break;

			// 处理下载结果
			case Message.Action.FILE_DOWNLOAD_RESULT:
				handleFileDownloadResult(ctx, msg);
				break;

			// 处理上传请求
			case Message.Action.FILE_UPLOAD_REQUEST:
				handleFileUploadRequest(ctx, msg);
				break;

			// 上传完成
			case Message.Action.FILE_UPLOAD_RESPONSE:
				try {
					handleFileUploadResponse(ctx, msg);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			// 处理重新上传请求
			case Message.Action.FILE_RE_UPLOAD_REQUEST:
				handleReUploadRequest(ctx, msg);
				break;
			}
		}
	}

	/**
	 * 处理重新上传请求
	 * 
	 * @param ctx
	 * @param msg
	 * @throws IOException
	 */
	private void handleReUploadRequest(ChannelHandlerContext ctx, Message msg) {

		String key = MD5Util.getTempFileKey(msg);
		String tempPath = SharedPreferenceUtil.get(key);
		File tempFile = new File(tempPath);
		// 删除临时文件记录
		SharedPreferenceUtil.remove(key);
		// 删除临时文件
		tempFile.delete();

		// 首次上传做准备
		System.out.println("first handleFileUploadRequest:" + Message.Action.FILE_RE_UPLOAD_REQUEST);
		try {
			handleUploadRequest(ctx, msg);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 处理上传结果
	 * 
	 * @param ctx
	 * @param msg
	 * @throws IOException
	 */
	private void handleFileUploadResponse(ChannelHandlerContext ctx, Message msg) throws IOException {

		// 上传成功的消息
		msg.setAction(Message.Action.FILE_UPLOAD_RESULT);
		msg.setResponse(Message.Response.SUCCESS);
		msg.getFile().setFileOffset(msg.getFile().getFileLength());
		msg.setHasFileData(false);
		
		response(msg, ctx.channel());

		String tempFileKey = MD5Util.getTempFileKey(msg);
		String tempPath = SharedPreferenceUtil.get(tempFileKey);
		File tempFile = new File(tempPath);
		
		tempFile.renameTo(new File("G:\\"+msg.getFile().getFileName()));
		// 删除临时文件记录
		SharedPreferenceUtil.remove(tempFileKey);

		String fileName = msg.getFile().getFileName();
		System.out.println("fileName:" + fileName);

		// 删除临时文件
		deleteTempFile(tempFile.getAbsolutePath());

		System.out.println("文件" + fileName + "上传成功！！！");
	}

	/**
	 * 处理上传请求
	 * 
	 * @param ctx
	 * @param msg
	 * @throws IOException
	 */
	private void handleFileUploadRequest(ChannelHandlerContext ctx, Message msg) {

		String path = SharedPreferenceUtil.get(MD5Util.getTempFileKey(msg));
		LOGGER.debug("handleFileUploadRequest tempPath " + path);
		File file = null;
		if (path != null) {
			file = new File(path);
		}
		// 获取已上传的内容，
		if (path != null && file.exists()) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(file, "rw");
				FileLock lock = raf.getChannel().tryLock();
				// 没有被其他线程加锁，可以写
				if (lock != null && lock.isValid()) {
					lock.release();
					raf.close();

					msg.setAction(Message.Action.FILE_UPLOAD_RESPONSE);
					msg.setResponse(Message.Response.FILE_READY);
					msg.getFile().setFileOffset(file.length());
					msg.setHasFileData(false);

					System.out.println("handleFileUploadRequest:" + Message.Action.FILE_UPLOAD_RESPONSE);

					// 回应
					response(msg, ctx.channel());
				} else {
					LOGGER.error("file do not get lock");
					// 该文件被其他线程加锁，不可写
					raf.close();

					msg.setAction(Message.Action.FILE_UPLOAD_RESPONSE);
					msg.setResponse(Message.Response.FILE_LOCKED);
					msg.setHasFileData(false);
					// 回应
					response(msg, ctx.channel());
				}
			} catch (Exception e) {
				LOGGER.debug(e.getMessage());
				try {
					if (raf != null) {
						raf.close();
					}
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				LOGGER.debug("file is locked");
				msg.setAction(Message.Action.FILE_UPLOAD_RESPONSE);
				msg.setResponse(Message.Response.FILE_LOCKED);
				msg.setHasFileData(false);
				// 回应
				response(msg, ctx.channel());
			}
		} else {
			// 首次上传做准备
			System.out.println("first handleFileUploadRequest:" + Message.Action.FILE_UPLOAD_RESPONSE);
			try {
				handleUploadRequest(ctx, msg);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 首次上传做准备
	 * 
	 * @param ctx
	 * @param msg
	 * @throws IOException
	 */
	private void handleUploadRequest(ChannelHandlerContext ctx, Message msg) throws IOException {

		LOGGER.debug("Receive upload request:" + msg);
		LOGGER.debug("收到客户端上传请求! 消息来自:" + ctx.channel().remoteAddress().toString());

		String fileName = msg.getFile().getFileName();
		LOGGER.debug("fileName : " + fileName);

		msg.setAction(Message.Action.FILE_UPLOAD_RESPONSE);
		msg.setResponse(Message.Response.FILE_READY);
		msg.getFile().setFileOffset(0);
		msg.setHasFileData(false);
		// 回应
		response(msg, ctx.channel());
	}

	/**
	 * 处理下载结果
	 * 
	 * @param ctx
	 * @param msg
	 * @throws IOException
	 */
	private void handleFileDownloadResult(ChannelHandlerContext ctx, Message msg) {

		String response = msg.getResponse();
		if (response.equals(Message.Response.SUCCESS)) {

			System.out.println("文件" + msg.getFile().getFileName() + "下载成功！！！！！！");
			ctx.close();
		} else if (response.equals(Message.Response.FILE_MD5_WRONG)) {
			// 重新传输
			msg.setAction(Message.Action.FILE_DOWNLOAD_REQUEST);
			msg.setResponse("");
			msg.getFile().setFileOffset(0);

			handleMessage(ctx, msg);
		}
	}

	/**
	 * 处理下载请求
	 * 
	 * @param ctx
	 * @param msg
	 */
	private void handleFileDownloadRequest(ChannelHandlerContext ctx, Message msg) {

		msg.setAction(Message.Action.FILE_DOWNLOAD_RESPONSE);
		msg.setResponse(Message.Response.FILE_READY);
		msg.setHasFileData(true);
		msg.getFile().setFilePath("G:/20190226-5.zip");
		response(msg, ctx.channel());
		
	}

	/**
	 * 响应客户端
	 *
	 * @param msg
	 *            收到的消息
	 * @param channel
	 *            发送通道
	 */
	public void response(Message msg, Channel channel) {

		// 将msg发送出去
		channel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture channelFuture) {
				if (channelFuture.isSuccess()) {
					LOGGER.error("TransportServerHandler writeAndFlush success ");
				} else {
					LOGGER.error("TransportServerHandler writeAndFlush failure");
					LOGGER.error(channelFuture.cause().getMessage());
				}
			}
		});
	}

	private void deleteTempFile(String absolutePath) {

		File file = new File(absolutePath);
		try {
			FileWriter writer = new FileWriter(file);
			writer.write("");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		File tempFolder = new File(System.getProperty("user.dir") + "\\temp");
		File[] files = tempFolder.listFiles();
		for (int i = 0; i < files.length; i++) {
			if (files[i].getName().equals("tempPath.sp")) {
				continue;
			}

			if (!SharedPreferenceUtil.containValue(files[i].getAbsolutePath())) {
				files[i].delete();
			}
		}

	}

	public FileTransportListener getFileLisenter() {
		return fileLisenter;
	}

	/*
	 * 通道断了
	 */
	public void onChannelInactive(ChannelHandlerContext ctx) throws IOException {

	}

	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

	}

}
