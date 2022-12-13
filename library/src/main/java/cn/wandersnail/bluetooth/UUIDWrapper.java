package cn.wandersnail.bluetooth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * @author xzh
 * Time: 2022/11/03 11:23
 * Description: uuid 包装
 */
public class UUIDWrapper {

    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private UUIDWrapper() {
    }

    @NonNull
    private UUID uuid = SPP_UUID;

    @NonNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * 使用默认的UUID 默认使用{@link #SPP_UUID}连接
     *
     * @return 默认UUID包装器
     */
    public static UUIDWrapper useDefault() {
        return new UUIDWrapper();
    }

    /**
     * 使用自定义UUID连接
     *
     * @param uuid 自定义UUID
     * @return UUID包装器
     */
    public static UUIDWrapper useCustom(@NonNull UUID uuid) {
        UUIDWrapper wrapper = new UUIDWrapper();
        wrapper.uuid = uuid;
        return wrapper;
    }

}