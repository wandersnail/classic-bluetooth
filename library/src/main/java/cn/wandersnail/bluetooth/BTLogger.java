package cn.wandersnail.bluetooth;

import cn.wandersnail.commons.util.AbstractLogger;

/**
 *
 *
 * date: 2020/5/20 10:54
 * author: zengfansheng
 */
class BTLogger extends AbstractLogger {    
    static final BTLogger instance = new BTLogger();

    @Override
    protected boolean accept(int priority, String tag, String msg) {
        return BTManager.isDebugMode;
    }
}