package cn.wandersnail.bluetooth;

/**
 * date: 2019/8/3 12:08
 * author: zengfansheng
 */
public class BTException extends RuntimeException {
    private static final long serialVersionUID = 1164256970604106282L;

    public BTException(String message) {
        super(message);
    }

    public BTException(String message, Throwable cause) {
        super(message, cause);
    }

    public BTException(Throwable cause) {
        super(cause);
    }
}
