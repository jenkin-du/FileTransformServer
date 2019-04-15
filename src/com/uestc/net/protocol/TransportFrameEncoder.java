package com.uestc.net.protocol;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * <pre>
 *     author : jenkin
 *     e-mail : jekin-du@foxmail.com
 *     time   : 2019/03/08
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class TransportFrameEncoder extends MessageToByteEncoder<Message> {

	private static final Logger LOGGER = LoggerFactory.getLogger(TransportFrameEncoder.class);
	
	// 分段下载，每段100M
	private static final int SEGMENT_LENGTH = 1024 * 1024 * 50;

	private RandomAccessFile raf;

	@Override
	protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {

		boolean hasFile = message.isHasFileData();
		if (hasFile) {
			String filePath = message.getFile().getFilePath();
			File file = new File(filePath);
			if (file.exists()) {
				// 添加文件属性
				long fileLength = file.length();
				message.getFile().setFileLength(fileLength);

				FileInputStream fis = new FileInputStream(new File(filePath));
				String md5 = DigestUtils.md5Hex(fis);
				message.getFile().setMd5(md5);

				long offset = message.getFile().getFileOffset();

				raf = new RandomAccessFile(new File(filePath), "rw");
				// 跳过已读的字节数
				raf.seek(offset);
				// 读取指定长度的数据
				byte[] bytes = new byte[1024];
				// 剩下数据不足一个分段
				if (offset + SEGMENT_LENGTH >= fileLength) {

					long segmentLength = fileLength - offset;
					if (segmentLength >= 0) {
						message.getFile().setSegmentLength(segmentLength);
						message.setAction(Message.Action.FILE_DOWNLOAD_RESPONSE);
						message.setResponse(Message.Response.FILE_READY);

						// 转化为json格式的支付串
						String jsonMessage = JSON.toJSONString(message);
						byte[] byteMsg = jsonMessage.getBytes();
						int msgLength = byteMsg.length;
						LOGGER.debug("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);
						// 写入参数
						out.writeInt(msgLength);
						out.writeBytes(byteMsg);
						// 写入数据
						while (raf.read(bytes) != -1) {
							out.writeBytes(bytes);
						}

						LOGGER.debug("write end");
						raf.close();
					} else {

						message.setAction(Message.Action.FILE_DOWNLOAD_RESPONSE);
						message.setResponse(Message.Response.FILE_ENCODE_WRONG);
						message.setHasFileData(false);

						channelHandlerContext.channel().writeAndFlush(message);
					}

				} else {
					message.setAction(Message.Action.FILE_DOWNLOAD_SEGMENT_RESPONSE);
					message.setResponse(Message.Response.FILE_READY);
					message.getFile().setSegmentLength(SEGMENT_LENGTH);
					// 转化为json格式的支付串
					String jsonMessage = JSON.toJSONString(message);
					byte[] byteMsg = jsonMessage.getBytes();
					int msgLength = byteMsg.length;

					LOGGER.debug("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);
					// 写入参数
					out.writeInt(msgLength);
					out.writeBytes(byteMsg);

					// 读取一个分段的数据
					for (int i = 0; i < SEGMENT_LENGTH / 1024; i++) {
						if (raf.read(bytes) != -1) {
							out.writeBytes(bytes);
						}
					}
					raf.close();
					LOGGER.debug("write end");

				}

				
			} else {
				// 下载文件没找到
				if (message.getAction().equals(Message.Action.FILE_DOWNLOAD_RESPONSE)) {
					message.setResponse(Message.Response.FILE_NOT_EXIST);
					message.setHasFileData(false);

					// 转化为json格式的支付串
					String jsonMessage = JSON.toJSONString(message);
					LOGGER.debug("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);

					byte[] byteMsg = jsonMessage.getBytes();
					int msgLength = byteMsg.length;

					// 写入参数
					out.writeInt(msgLength);
					out.writeBytes(byteMsg);
				}
			}
		} else {

			// 没有文件传输
			// 转化为json格式的支付串
			String jsonMessage = JSON.toJSONString(message);

			LOGGER.debug("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);

			byte[] byteMsg = jsonMessage.getBytes();
			int msgLength = byteMsg.length;

			// 写入参数
			out.writeInt(msgLength);
			out.writeBytes(byteMsg);
			
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);

		if (raf != null) {
			raf.close();
		}
	}
}
