package com.alibaba.csp.sentinel.metric.extension;

import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.metric.extension.callback.MetricEntryCallback;
import com.alibaba.csp.sentinel.metric.extension.callback.MetricExitCallback;
import com.alibaba.csp.sentinel.slots.statistic.StatisticSlotCallbackRegistry;

/**
 * Register callbacks for metric extension.
 *
 * @author Carpenter Lee
 * @since 1.6.1
 * 看来这是一个入口  为 ProcessorSlotExitCallback 注册了实现了 MetricExitCallback
 * 而 MetricExitCallback 内部又有很多的基于SPI 加载的实现类
 */
public class MetricCallbackInit implements InitFunc {
    @Override
    public void init() throws Exception {
        StatisticSlotCallbackRegistry.addEntryCallback(MetricEntryCallback.class.getCanonicalName(),
            new MetricEntryCallback());
        StatisticSlotCallbackRegistry.addExitCallback(MetricExitCallback.class.getCanonicalName(),
            new MetricExitCallback());
    }
}
