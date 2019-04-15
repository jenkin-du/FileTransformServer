package com.uestc.net.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uestc.net.protocol.TransportFrameDecoder;
import com.uestc.net.protocol.TransportFrameEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * @describe
 * @author shen
 * @date 2018/1/7
 * @email 875415435@qq.com
 * @org UESTC
 *
 */
public class FileServer {

	/*
	 * 日志组件，orj.slf4j(Simple Logging Facade for Java)
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(FileServer.class);

	/**
	 * 加载配置文件
	 *
	 * public static final String fileBase; public static final String storeFile;
	 * public static final String storePass; public static final String keyPass;
	 *
	 * static { /* Java.util.Properties主要用于读取Java的配置文件 配置文件常为.properties的文本文件
	 * 文件的内容的格式是“键=值”的格式，文本注释信息可以用"#"来注释 *
	 * 
	 * //新建一个Properties类 Properties props = new Properties();
	 * 
	 * //加载配置文件 /* try { props.load(new
	 * FileInputStream("C:\\WorkspaceEclipse\\SimleTeeth\\SimleTeethServer\\bin\\resources\\SSLServer.properties"));
	 * } catch (Exception e) { e.printStackTrace(); System.exit(-1); } storeFile =
	 * props.getProperty("ssl.keystore"); storePass =
	 * props.getProperty("ssl.storepass"); keyPass =
	 * props.getProperty("ssl.keypass"); fileBase =
	 * props.getProperty("file.base.dir");
	 * 
	 * }
	 */

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;

	/**
	 * 启动文件服务器
	 */
	// 设置服务器的端口,默认20001
	private int listenPort = 20002;

	// 含参构造函数，可以设置服务器的端口
	public FileServer() {

	}

	// 启动服务器的函数
	public void start() throws Exception {

		TransportServerHandler transportServerHandler = new TransportServerHandler();
		/*
		 * ServerBootstrap负责初始化netty服务器，并且开始监听端口的socket请求
		 * Netty内部都是通过线程在处理各种数据，EventLoopGroup就是用来管理调度他们的
		 */
		ServerBootstrap bootstrap = new ServerBootstrap();
		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();

		try {
			// 指定通道类型为NiOServerSocketChannel
			bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000).childOption(ChannelOption.SO_TIMEOUT, 1)
					.childOption(ChannelOption.SO_KEEPALIVE, true)
					// 连接后调用的handler
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							// ch.pipeline().addLast("SSLHandler",new
							// SslHandler(sslEngine));
							// 超时处理器
							ch.pipeline().addLast(new IdleStateHandler(0, 0, 60));
							// 自己实现的帧解码器,继承了ChannelInboundHandlerAdapter,入站处理器
							ch.pipeline().addLast("decoder", new TransportFrameDecoder(transportServerHandler));
							// 自己实现的msg编码器,继承了MessageToByteEncoder,出站处理器
							ch.pipeline().addLast("encoder", new TransportFrameEncoder());

						}
					}).option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true)
					.childOption(ChannelOption.TCP_NODELAY, true);

			// 绑定端口并设定监听对象,通过调用sync同步方法阻塞直到绑定成功
			ChannelFuture f = bootstrap.bind(listenPort).sync();
			LOGGER.info("Server started,listen port:" + listenPort);
			// 应用程序会一直等待，直到channel关闭
			f.channel().closeFuture().sync();

		} catch (InterruptedException e) {
			LOGGER.error("Netty not start：", e);
		} finally {
			// 关闭EventLoopGroup，释放掉所有资源包括创建的线程
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}

	}

	// 关闭；
	public void stop() {

		if (bossGroup != null && !bossGroup.isShutdown()) {
			bossGroup.shutdownGracefully();
		}
		if (workerGroup != null && !workerGroup.isShutdown()) {
			workerGroup.shutdownGracefully();
		}

	}

}
