package com.uestc.net.callback;

import io.netty.channel.ChannelHandlerContext;

/**
 * @author jenkin
 *         网络状态监听器
 *
 */
public interface NetStateListener {

//    void processSuccess(String info);
//
//    void processFailure(String info);

    //网络出现错误
    void exceptionCaught(Throwable cause);

    //网络断开
    void channelInactive(ChannelHandlerContext ctx);
}
