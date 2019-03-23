package com.uestc.net.protocol;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

public interface MessageHandler {

    void handleMessage(ChannelHandlerContext ctx, Message msg);

    void channelInactive(ChannelHandlerContext ctx) throws Exception;

    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    void response(final Message msg, final Channel channel);
}
