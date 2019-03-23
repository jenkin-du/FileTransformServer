package com.uestc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class TransportChannelHandler extends SimpleChannelInboundHandler<String> {

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

		System.out.println(msg);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		try {
			super.channelActive(ctx);
		} catch (Exception e) {

			System.out.println("异常处理：" + e.toString());
			e.printStackTrace();
		}
	}

	
}
