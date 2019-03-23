package com.uestc;

import java.util.Scanner;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;

public class Client {

	public static void main(String[] args) {

		Bootstrap bootstrap = new Bootstrap();
		NioEventLoopGroup group = new NioEventLoopGroup();

		bootstrap.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(Channel ch) {
				ch.pipeline().addLast(new StringEncoder());
				ch.pipeline().addLast(new TransportChannelHandler());
			}
		});

		Channel channel = bootstrap.connect("127.0.0.1", 8000).channel();

		Scanner scanner = new Scanner(System.in);
		while (true) {
			String msg = scanner.nextLine();

			channel.writeAndFlush(msg);
//			try {
//				Thread.sleep(2000);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
		}
		
	}
}
