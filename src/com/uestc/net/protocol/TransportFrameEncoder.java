package com.uestc.net.protocol;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import org.apache.commons.codec.digest.DigestUtils;

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

	// 分段下载，每段100M
	private static final int SEGMENT_LENGTH = 1024 * 1024 * 100;

	private RandomAccessFile raf;

	@Override
	protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {

		System.out.println("TransportFrameEncoder encode " + message);

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
				System.out.println("TransportFrameEncoder encode: offset:" + offset);

				raf = new RandomAccessFile(new File(filePath), "rw");
				// 跳过已读的字节数
				raf.seek(offset);
				// 读取指定长度的数据
				byte[] bytes = new byte[1024];
				// 剩下数据不足一个分段
				if (offset + SEGMENT_LENGTH >= fileLength) {

					long segmentLength = fileLength - offset;
					if (segmentLength > 0) {
						message.getFile().setSegmentLength(segmentLength);
						message.setAction("fileDownloadAck");
						message.addParam("ack",Message.Ack.FILE_READY);

						// 转化为json格式的支付串
						String jsonMessage = JSON.toJSONString(message);
						byte[] byteMsg = jsonMessage.getBytes();
						int msgLength = byteMsg.length;

						System.out.println("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);
						System.out.println("TransportFrameEncoder encode: msgLength = " + msgLength);

						// 写入参数
						out.writeInt(msgLength);
						out.writeBytes(byteMsg);
						// 写入数据
						while (raf.read(bytes) != -1) {
							out.writeBytes(bytes);
						}

						System.out.println("TransportFrameEncoder encode: write end!!!");
						raf.close();
					} else {

						message.setAction("fileDownloadAck");
						message.addParam("ack", Message.Ack.FILE_ENCODE_WRONG);
						message.setHasFileData(false);	

						channelHandlerContext.channel().writeAndFlush(message);
					}

				} else {
					message.setAction("fileDownloadSegmentAck");
					message.getFile().setSegmentLength(SEGMENT_LENGTH);
					// 转化为json格式的支付串
					String jsonMessage = JSON.toJSONString(message);
					byte[] byteMsg = jsonMessage.getBytes();
					int msgLength = byteMsg.length;

					System.out.println("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);
					System.out.println("TransportFrameEncoder encode: msgLength = " + msgLength);

					// 写入参数
					out.writeInt(msgLength);
					out.writeBytes(byteMsg);

					// 读取一个分段的数据
					for (int i = 0; i < SEGMENT_LENGTH / 1024; i++) {
						if (raf.read(bytes) != -1) {
							out.writeBytes(bytes);
						}
					}

					System.out.println("TransportFrameEncoder encode: write end!!!");
				}

				raf.close();
			} else {
				// 下载文件没找到
				if (message.getAction().equals("fileDownloadAck")) {
					message.addParam("ack", Message.Ack.FILE_NOT_EXIST);
					message.setHasFileData(false);

					// 转化为json格式的支付串
					String jsonMessage = JSON.toJSONString(message);

					System.out.println("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);

					byte[] byteMsg = jsonMessage.getBytes();
					int msgLength = byteMsg.length;

					System.out.println("TransportFrameEncoder encode: msgLength = " + msgLength);

					// 写入参数
					out.writeInt(msgLength);
					out.writeBytes(byteMsg);
				}
			}
		} else {

			// 没有文件传输
			// 转化为json格式的支付串
			String jsonMessage = JSON.toJSONString(message);

			System.out.println("TransportFrameEncoder encode: jsonMessage:" + jsonMessage);

			byte[] byteMsg = jsonMessage.getBytes();
			int msgLength = byteMsg.length;

			System.out.println("TransportFrameEncoder encode: msgLength = " + msgLength);

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
