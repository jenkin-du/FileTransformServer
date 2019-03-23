package com.uestc;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class CustomerInboundHandler extends ChannelInboundHandlerAdapter{

	@Override
	public void channelInactive(ChannelHandlerContext ctx)  {
		
		try {
			super.channelInactive(ctx);
		} catch (Exception e) {
			System.out.println("异常发生");
			e.printStackTrace();
		}
	}
}
