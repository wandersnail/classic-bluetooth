package cn.wandersnail.bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * date: 2020/5/5 19:44
 * author: zengfansheng
 */
public interface ConnectCallback {
    /**
     * 连接成功
     */
    void onSuccess();

    /**
     * 连接失败
     *
     * @param errMsg 错误信息
     * @param e      异常
     */
    void onFail(@NonNull String errMsg, @Nullable Throwable e);
}
