package com.uestc.net.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uestc.net.protocol.Message;
import com.uestc.net.protocol.MessageHandler;
import com.uestc.net.util.SharedPreferenceUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;

/**
 * 服务器使用
 */
public class TransportServerHandler implements MessageHandler {

	private static final String TAG = "NewTransportRequest";

	private static final Logger LOGGER = LoggerFactory.getLogger(TransportServerHandler.class);

	@Override
	public void handleMessage(ChannelHandlerContext ctx, Message msg) {

		String action = msg.getAction();
		if (action != null) {

			switch (action) {
			// 下载请求
			case "fileDownloadRequest":
				handleFileDownloadRequest(ctx, msg);
				break;

			// 处理下载结果
			case "fileDownloadResult":
				handleFileDownloadResult(ctx, msg);
				break;

			// 处理上传请求
			case "fileUploadRequest":
				handleFileUploadRequest(ctx, msg);
				break;

			// 处理分段上传响应
			case "fileUploadSegment":
				handleFileUploadSegment(ctx, msg);
				break;
			}
		}

	}

	/**
	 * 处理分段上传响应
	 * 
	 * @param ctx
	 * @param msg
	 */
	private void handleFileUploadSegment(ChannelHandlerContext ctx, Message msg) {

		System.out.println("handleFileUploadSegment " + msg);

		long fileLength = Long.parseLong(msg.getParam("fileLength"));
		long fileOffset = Long.parseLong(msg.getParam("fileOffset"));
		long segmentLength = Long.parseLong(msg.getParam("segmentLength"));

		if (fileOffset + segmentLength < fileLength) {
			Message rm = new Message();
			rm.setType(Message.Type.RESPONSE);
			rm.setAction("fileUploadSegmentResult");
			rm.addParam("result", "success");
			rm.addParam("fileName", msg.getParam("fileName"));
			rm.addParam("filePath", msg.getParam("filePath"));
			rm.addParam("fileLength", msg.getParam("fileLength"));
			rm.addParam("fileOffset", (fileOffset + segmentLength) + "");

			response(rm, ctx.channel());
		} else {
			Message rm = new Message();
			rm.setType(Message.Type.RESPONSE);
			rm.setAction("fileUploadResult");
			rm.addParam("result", Message.Result.SUCCESS);

			response(rm, ctx.channel());
		}

	}

	/**
	 * 处理上传请求
	 * 
	 * @param ctx
	 * @param msg
	 */
	private void handleFileUploadRequest(ChannelHandlerContext ctx, Message msg) {

		String tempPath = SharedPreferenceUtil.get(msg.getParam("fileName"));
		System.out.println("handleFileUploadRequest tempPath " + tempPath);
		// 获取已上传的内容，
		long offset = 0;
		if (tempPath != null) {
			File file = new File(tempPath);
			if (file.exists()) {
				System.out.println("file is exist");
				try {
					RandomAccessFile raf = new RandomAccessFile(file, "rw");
					System.out.println("handleFileUploadRequest tryLock:");
					FileLock lock = raf.getChannel().tryLock();
					System.out.println("handleFileUploadRequest lock:" + lock);
					// 没有被其他线程加锁，可以写
					if (lock != null && lock.isValid()) {
						System.out.println("file get lock");

						lock.release();
						raf.close();

						Message rm = new Message();
						rm.setType(Message.Type.RESPONSE);
						rm.setAction("fileUploadAck");
						rm.addParam("ack", Message.Ack.FILE_READY);
						rm.addParam("fileName", msg.getParam("fileName"));
						rm.addParam("filePath", msg.getParam("filePath"));

						offset = file.length();
						rm.addParam("fileOffset", offset + "");
						// 回应
						response(rm, ctx.channel());
					} else {
						System.out.println("file do not get lock");
						// 该文件被其他线程加锁，不可写
						raf.close();

						Message rm = new Message();
						rm.setType(Message.Type.RESPONSE);
						rm.setAction("fileUploadAck");
						rm.addParam("ack", Message.Ack.FILE_LOCKED);
						rm.addParam("fileName", msg.getParam("fileName"));
						rm.addParam("filePath", msg.getParam("filePath"));

						// 回应
						response(rm, ctx.channel());
					}
				} catch (IOException e) {
					e.printStackTrace();

					System.out.println(e.getLocalizedMessage());
				}

			} else {

				Message rm = new Message();
				rm.setType(Message.Type.RESPONSE);
				rm.setAction("fileUploadAck");
				rm.addParam("ack", Message.Ack.FILE_READY);
				rm.addParam("fileName", msg.getParam("fileName"));
				rm.addParam("filePath", msg.getParam("filePath"));
				rm.addParam("fileOffset", 0 + "");

				// 回应
				response(rm, ctx.channel());
			}
		} else {
			Message rm = new Message();
			rm.setType(Message.Type.RESPONSE);
			rm.setAction("fileUploadAck");
			rm.addParam("ack", Message.Ack.FILE_READY);
			rm.addParam("fileName", msg.getParam("fileName"));
			rm.addParam("filePath", msg.getParam("filePath"));
			rm.addParam("fileOffset", 0 + "");

			// 回应
			response(rm, ctx.channel());
		}

	}

	/**
	 * 处理下载结果
	 * 
	 * @param ctx
	 * @param msg
	 */
	private void handleFileDownloadResult(ChannelHandlerContext ctx, Message msg) {

		String result = msg.getParam("result");
		if (result.equals(Message.Result.SUCCESS)) {

			System.out.println("下载成功！！！！！！");
			ctx.close();
		} else if (result.equals(Message.Result.FILE_MD5_WRONG)) {
			// 重新传输
			String fileName = msg.getParam("fileName");
			if (fileName != null) {

				String filePath = "G:\\20190125-6.zip";

				Message responseMsg = new Message();
				responseMsg.setType(Message.Type.RESPONSE);
				responseMsg.setAction("fileDownloadAck");
				responseMsg.setHasFile(true);
				responseMsg.addParam("fileName", fileName);
				responseMsg.addParam("filePath", filePath);
				responseMsg.addParam("fileOffset", "0");

				LOGGER.debug("responseMsg:" + responseMsg);
				response(responseMsg, ctx.channel());

			}
		}
	}

	/**
	 * 处理下载请求
	 * 
	 * @param ctx
	 * @param msg
	 */
	private void handleFileDownloadRequest(ChannelHandlerContext ctx, Message msg) {

		String fileName = msg.getParam("fileName");
		if (fileName != null) {

			String filePath = "G:\\20190125-6.zip";

//			Message responseMsg = nefile:/D:/SmileTeeth/Project/Reference/FileTransformDemo/app/src/main/java/com/uestc/filetransformdemo/MainActivity.javaw Message();
//			responseMsg.setType(Message.Type.RESPONSE);
//			responseMsg.setAction("fileDownloadAck");
//			responseMsg.setHasFile(true);
//			responseMsg.addParam("fileName", fileName);
//			responseMsg.addParam("filePath", filePath);
//			responseMsg.addParam("fileOffset", msg.getParam("fileOffset"));
			msg.setAction("fileDownloadAck");
			msg.addParam("ack", Message.Ack.FILE_READY);
			msg.addParam("filePath", filePath);
			msg.setHasFile(true);

			response(msg, ctx.channel());

		}
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
					System.out.println("TransportServerHandler writeAndFlush success msg" + msg);
				} else {
					System.out.println("TransportServerHandler writeAndFlush failure");
				}
			}
		});
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

	}
}
