package com.uestc.net.callback;

/**
 * <pre>
 *     author : jenkin
 *     e-mail : jekin-du@foxmail.com
 *     time   : 2019/03/09
 *     desc   : 用户回调
 *     version: 1.0
 * </pre>
 */
public interface TransportListener {


    /**
     * 传输过程
     */
    void onProgress(float progress);

    /**
     * 传输成功
     */
    void onComplete();
    
    /**
     * 传输失败
     */
    void onFailure();
}
