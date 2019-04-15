package com.uestc.net.protocol;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.uestc.net.api.TransportServerHandler;
import com.uestc.net.callback.FileTransportListener;
import com.uestc.net.util.MD5Util;
import com.uestc.net.util.SharedPreferenceUtil;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * <pre>
 *     author : jenkin
 *     e-mail : jekin-du@foxmail.com
 *     time   : 2019/03/08
 *     desc   :
 *     version: 1.0
 * </pre>
 */
public class TransportFrameDecoder extends ChannelInboundHandlerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger(TransportFrameDecoder.class);

	// 消息头是否读
	private boolean msgHeaderRead = false;
	// 消息是否读
	private boolean msgRead = false;
	// 是否携带文件
	private boolean hasFile = false;
	// 首次写文件
	private boolean isFirstWrite = true;

	// 消息所占字节数
	private int msgSize = 0;
	// 文件大小
	private long fileSize = 0;
	// 文件剩余传输字节数
	private long fileLeftSize = 0;
	// 文件以传输字节数
	private long fileOffset = 0;
	// 传输的每段的字节数
	private long segmentLeftSize = 0;
	// 已读的每段的字节数
	private long segmentRead = 0;

	private byte[] remainingByte;// 本次没有读完的数据

	// 文件传输时写入的临时文件
	private File tempFile;
	private RandomAccessFile randomAccessFile;
	// 文件锁
	private FileLock lock;

	// 业务逻辑处理器
	private TransportServerHandler serverHandler;
	// 消息
	private Message msg;

	// 文件传输监听器
	private FileTransportListener fileListener;

	public TransportFrameDecoder(TransportServerHandler serverHandler) {
		this.serverHandler = serverHandler;
		this.fileListener = serverHandler.getFileLisenter();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object byteBuf) throws Exception {
		ByteBuf buf = (ByteBuf) byteBuf;

		// 读参数头
		if (!msgHeaderRead) {
			msgSize = buf.readInt();
			msgHeaderRead = true;

			LOGGER.debug("TransportFrameDecoder, paramSize:" + msgSize);
		}

		// 读参数
		if (!msgRead && msgSize > 0) {

			// 第一帧数据中能读出参数
			byte[] msgByte;
			if (remainingByte == null && buf.readableBytes() >= msgSize) {
				msgByte = new byte[(int) msgSize];
				int remaining = (int) (buf.readableBytes() - msgSize);
				// 读取参数字节
				buf.readBytes(msgByte);
				// 读取剩余字节
				if (remaining >= 0) {
					// 读取剩余字节
					remainingByte = new byte[remaining];
					buf.readBytes(remainingByte);

				}
				msgRead = true;

				// 解析数据
				String jsonMsg = new String(msgByte);

				LOGGER.debug("TransportFrameDecoder,jsonMsg: " + jsonMsg);

				msg = JSON.parseObject(jsonMsg, Message.class);
				// 有文件传输
				if (msg.isHasFileData()) {

					fileSize = msg.getFile().getFileLength();
					fileOffset = msg.getFile().getFileOffset();
					fileLeftSize = fileSize - fileOffset;
					segmentLeftSize = msg.getFile().getSegmentLength();
					hasFile = true;

					// 没有文件传输
				} else {
					// 文档分段传输，事件由Decoder处理，不向外分发
					if (msg.getAction().equals(Message.Action.FILE_DOWNLOAD_SEGMENT_REQUEST)) {
						handleFileDownLoadSegmentRequest(ctx, msg);
					} else {
						serverHandler.handleMessage(ctx, msg);
					}
					// 重置控制变量
					reset();
				}
			}

			// 第一帧数据中不能读出参数
			if (remainingByte == null && buf.readableBytes() < msgSize) {

				int remaining = buf.readableBytes();
				remainingByte = new byte[remaining];
				buf.readBytes(remainingByte);
			}

			// 第二帧数据中不能读出参数,直到能读出数据为止
			if (remainingByte != null && remainingByte.length + buf.readableBytes() < msgSize) {
				byte[] data = new byte[buf.readableBytes()];
				buf.readBytes(data);
				remainingByte = byteMerger(remainingByte, data);
			}

			// 第二帧数据中能读出参数
			if (remainingByte != null && remainingByte.length + buf.readableBytes() >= msgSize) {

				int toRead = msgSize - remainingByte.length;
				if (toRead > 0) {
					byte[] toReadByte = new byte[toRead];
					int remaining = buf.readableBytes() - toRead;

					buf.readBytes(toRead);
					msgByte = byteMerger(remainingByte, toReadByte);

					if (remaining >= 0) {
						// 读取剩余字节
						remainingByte = new byte[remaining];
						buf.readBytes(remainingByte);

					}
					msgRead = true;

					// 解析数据
					String jsonMessage = new String(msgByte);

					LOGGER.debug("TransportFrameDecoder:" + jsonMessage);
					// 有文件传输
					if (msg.isHasFileData()) {

						fileSize = msg.getFile().getFileLength();
						fileOffset = msg.getFile().getFileOffset();
						fileLeftSize = fileSize - fileOffset;
						segmentLeftSize = msg.getFile().getSegmentLength();
						hasFile = true;

						// 没有文件传输
					} else {
						// 文档分段传输，事件由Decoder处理，不向外分发
						if (msg.getAction().equals(Message.Action.FILE_DOWNLOAD_SEGMENT_REQUEST)) {
							handleFileDownLoadSegmentRequest(ctx, msg);
						} else {
							serverHandler.handleMessage(ctx, msg);
						}
						// 重置控制变量
						reset();
					}
				}
			}
		}

		if (hasFile && segmentLeftSize > 0) {

			try {
				if (randomAccessFile == null) {
					String key = MD5Util.getTempFileKey(msg);
					String tempFilePath = SharedPreferenceUtil.get(key);
					if (tempFilePath != null) {
						tempFile = new File(tempFilePath);
						if (tempFile.exists()) {
							randomAccessFile = new RandomAccessFile(tempFile, "rw");
							// 对文件进行加锁
							lock = randomAccessFile.getChannel().tryLock();
							if (lock == null) {
								fileListener.onExceptionCaught("file is locked");
								randomAccessFile.close();
								ctx.close();
								return;
							}
						} else {
							SharedPreferenceUtil.remove(key);

							// 生成临时文件路径
							createTempPath(msg);
							// 对文件进行加锁
							lock = randomAccessFile.getChannel().tryLock();
							if (lock == null) {
								fileListener.onExceptionCaught("file is locked");
								randomAccessFile.close();
								ctx.close();
								return;
							}
						}

					} else {
						// 生成临时文件路径
						createTempPath(msg);
						// 对文件进行加锁
						lock = randomAccessFile.getChannel().tryLock();
						if (lock == null) {
							fileListener.onExceptionCaught("file is locked");
							randomAccessFile.close();
							ctx.close();
							return;
						}

					}
				}
			} catch (Exception e) {
				System.err.println(e.getLocalizedMessage());

				msg.setAction(Message.Action.FILE_DOWNLOAD_RESPONSE);
				msg.setResponse(Message.Response.FILE_LOCKED);
				msg.setHasFileData(false);

				ctx.channel().writeAndFlush(msg);
				ctx.channel().close();
			}

			// 读取剩余的数据
			if (remainingByte != null) {

				if (segmentLeftSize <= remainingByte.length) {

					byte[] data = subBytes(remainingByte, 0, (int) segmentLeftSize);
					// 写文件,首次写文件需要设置写位置，以支持断点续传
					if (isFirstWrite) {
						randomAccessFile.seek(randomAccessFile.length());
						randomAccessFile.write(data);

						isFirstWrite = false;
					} else {
						randomAccessFile.write(data);
					}
					// 关闭文件
					lock.release();
					randomAccessFile.close();

					// 跟新数据传输进度
					segmentRead += segmentLeftSize;
					fileListener.onProgress((fileOffset + segmentRead) * 1.0 / fileSize, fileSize);

					// 实时检测整个文件大小是否读取完毕
					fileLeftSize -= segmentLeftSize;
					if (fileLeftSize == 0) {
						// 完成传输
						checkFileMD5(ctx, msg, tempFile);
					} else {
						// 分段传输文件
						if (msg.getAction().equals(Message.Action.FILE_UPLOAD_SEGMENT_RESPONSE)) {
							// 完成数据传输，处理业务逻辑
							handleFileUploadSegmentResponse(ctx, msg);
						}
					}
					// 重置控制变量
					reset();
					segmentLeftSize = 0;
					remainingByte = null;
				} else {
					// 写文件,首次写文件需要设置写位置，以支持断点续传
					if (isFirstWrite) {
						randomAccessFile.seek(randomAccessFile.length());
						randomAccessFile.write(remainingByte);

						isFirstWrite = false;
					} else {
						randomAccessFile.write(remainingByte);
					}

					// 实时检测整个文件大小是否读取完毕
					fileLeftSize -= remainingByte.length;
					segmentLeftSize -= remainingByte.length;
					segmentRead += remainingByte.length;
					remainingByte = null;

					// 数据传输进度
					fileListener.onProgress((fileOffset + segmentRead) * 1.0 / fileSize, fileSize);
				}

			}

			// 读取一帧的数据
			if (remainingByte == null && segmentLeftSize > 0) {

				if (segmentLeftSize <= buf.readableBytes()) {
					byte[] data = new byte[(int) segmentLeftSize];
					buf.readBytes(data);
					// 写文件,首次写文件需要设置写位置，以支持断点续传
					if (isFirstWrite) {
						randomAccessFile.seek(randomAccessFile.length());
						randomAccessFile.write(data);

						isFirstWrite = false;
					} else {
						randomAccessFile.write(data);
					}
					lock.release();
					randomAccessFile.close();

					// 更新数据传输进度
					segmentRead += segmentLeftSize;
					fileListener.onProgress((fileOffset + segmentRead) * 1.0 / fileSize, fileSize);

					// 实时检测整个文件大小是否读取完毕
					fileLeftSize -= segmentLeftSize;
					if (fileLeftSize == 0) {
						// 完成传输
						checkFileMD5(ctx, msg, tempFile);
					} else {
						// 分段传输文件
						if (msg.getAction().equals(Message.Action.FILE_UPLOAD_SEGMENT_RESPONSE)) {
							// 完成数据传输，处理业务逻辑
							handleFileUploadSegmentResponse(ctx, msg);
						}
					}
					// 重置控制变量
					reset();
					segmentLeftSize = 0;
				} else {
					// 实时检测整个文件大小是否读取完毕
					fileLeftSize -= buf.readableBytes();
					segmentLeftSize -= buf.readableBytes();
					segmentRead += buf.readableBytes();

					byte[] data = new byte[buf.readableBytes()];
					buf.readBytes(data);
					// 写文件,首次写文件需要设置写位置，以支持断点续传
					if (isFirstWrite) {
						randomAccessFile.seek(randomAccessFile.length());
						randomAccessFile.write(data);

						isFirstWrite = false;
					} else {
						randomAccessFile.write(data);
					}

					// 数据传输进度
					fileListener.onProgress((fileOffset + segmentRead) * 1.0 / fileSize, fileSize);
				}
			}
		}

		buf.release();
	}

	/**
	 * 处理分段上传响应
	 * 
	 * @param ctx
	 * @param msg
	 */
	private void handleFileUploadSegmentResponse(ChannelHandlerContext ctx, Message msg) {

		long fileOffset = msg.getFile().getFileOffset();
		long segmentLength = msg.getFile().getSegmentLength();

		msg.setAction(Message.Action.FILE_UPLOAD_SEGMENT_REQUEST);
		msg.getFile().setFileOffset(fileOffset + segmentLength);
		msg.setHasFileData(false);

		ctx.channel().writeAndFlush(msg);
	}

	/**
	 * 处理文件分段下载结果
	 * 
	 * @param ctx
	 * @param message
	 */
	private void handleFileDownLoadSegmentRequest(ChannelHandlerContext ctx, Message message) {

		message.setAction(Message.Action.FILE_DOWNLOAD_SEGMENT_RESPONSE);
		message.setHasFileData(true);

		ctx.channel().writeAndFlush(message);
	}

	/**
	 * 生成临时文件路径
	 *
	 * @throws IOException
	 */
	private void createTempPath(Message msg) throws IOException {

		UUID uuid = UUID.randomUUID();
		File tempFolder = new File("G:\\temp");
		boolean isOk = false;
		if (!tempFolder.exists()) {
			isOk = tempFolder.mkdir();
		}
		tempFile = new File(tempFolder.getAbsolutePath() + "\\" + uuid + ".tmp");
		tempFile.createNewFile();
		randomAccessFile = new RandomAccessFile(tempFile, "rw");

		String key = MD5Util.getTempFileKey(msg);
		// 保存临时文件路径
		SharedPreferenceUtil.save(key, tempFile.getAbsolutePath());

		System.out.println("tempFile:" + tempFile.getAbsolutePath());

	}

	/**
	 * 检查文件MD5
	 * 
	 * @param ctx
	 * @param msg
	 * @param tempFile
	 * @throws NoSuchAlgorithmException
	 */
	private void checkFileMD5(ChannelHandlerContext ctx, Message msg, File tempFile) throws NoSuchAlgorithmException {

		LOGGER.debug("checkFileMD5:tempFile" + tempFile.getAbsolutePath());

		try {
			String md5 = MD5Util.getFileMD5(tempFile);
			LOGGER.debug("md5: " + md5);

			String fileMD5 = msg.getFile().getMd5();
			LOGGER.debug("fileMD5:" + fileMD5);

			if (md5.equals(fileMD5)) {
				LOGGER.debug("checkFileMD5:correct");
				// 数据传输进度
				fileListener.onProgress(1, fileSize);
				// 完成数据读写
				fileListener.onComplete(true, tempFile.getAbsolutePath());
				// 完成数据传输，处理业务逻辑
				serverHandler.handleMessage(ctx, msg);
			} else {
				LOGGER.debug("checkFileMD5:wrong");
				// 数据传输进度
				fileListener.onProgress(0, fileLeftSize);
				// 完成数据读写
				fileListener.onComplete(false, tempFile.getAbsolutePath());
				// 重传
				msg.setAction(Message.Action.FILE_UPLOAD_RESULT);
				msg.setResponse(Message.Response.FILE_MD5_WRONG);
				msg.setHasFileData(false);

				// 删除临时文件
				tempFile.delete();
				// 删除临时文件记录
				String key = MD5Util.getTempFileKey(msg);
				SharedPreferenceUtil.remove(key);
				// 下载错误，响应服务器
				ctx.channel().writeAndFlush(msg);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		serverHandler.userEventTriggered(ctx, evt);

		if (evt instanceof IdleStateEvent) {

			IdleStateEvent event = (IdleStateEvent) evt;
			if (event.state() == IdleState.ALL_IDLE) {
				ctx.close();
				LOGGER.debug("userEventTriggered: IdleState.ALL_IDLE");
			}
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		super.channelReadComplete(ctx);

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);

		if (lock != null && lock.isValid()) {
			lock.release();
		}

		if (randomAccessFile != null) {
			randomAccessFile.close();
		}

		serverHandler.onChannelInactive(ctx);
		LOGGER.debug("channelInactive: channel has inactive");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		super.exceptionCaught(ctx, cause);

		if (lock != null && lock.isValid()) {
			lock.release();
		}

		if (randomAccessFile != null) {
			randomAccessFile.close();
		}

		ctx.close();

		LOGGER.debug(cause.getLocalizedMessage());
	}

	/**
	 * 字节数组融合
	 */
	private static byte[] byteMerger(byte[] bt1, byte[] bt2) {
		byte[] bt3 = new byte[bt1.length + bt2.length];
		System.arraycopy(bt1, 0, bt3, 0, bt1.length);
		System.arraycopy(bt2, 0, bt3, bt1.length, bt2.length);
		return bt3;
	}

	/**
	 * 截取字节数组
	 */
	private static byte[] subBytes(byte[] src, int begin, int count) {
		byte[] bs = new byte[count];
		System.arraycopy(src, begin, bs, 0, count);
		return bs;
	}

	/**
	 * 重置控制变量
	 * 
	 * @throws IOException
	 */
	private void reset() throws IOException {

		msgHeaderRead = false;
		msgRead = false;
		hasFile = false;
		isFirstWrite = true;

		msgSize = 0;
		segmentLeftSize = 0;
		segmentRead = 0;

		remainingByte = null;
		// 文件传输时写入的临时文件
		if (lock != null && lock.isValid()) {
			lock.release();
			lock = null;
		}
		tempFile = null;
		randomAccessFile = null;

		// 消息
		msg = null;
	}

}
