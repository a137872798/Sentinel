/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.node;

import java.util.List;
import java.util.Map;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.slots.statistic.metric.DebugSupport;
import com.alibaba.csp.sentinel.util.function.Predicate;

/**
 * Holds real-time statistics for resources.
 *
 * @author qinan.qn
 * @author leyou
 * @author Eric Zhao
 * 包含某个资源的实时统计数据
 */
public interface Node extends OccupySupport, DebugSupport {

    /**
     * Get incoming request per minute ({@code pass + block}).
     *
     * @return total request count per minute
     * 总请求量 (那么在集群环境下又是如何确保数据准确呢? 肯定不会加锁 不然效率很低 那么是使用快照的方式么)
     */
    long totalRequest();

    /**
     * Get pass count per minute.
     *
     * @return total passed request count per minute
     * @since 1.5.0
     * 只记录pass的数量
     */
    long totalPass();

    /**
     * Get {@link Entry#exit()} count per minute.
     *
     * @return total completed request count per minute
     * 只记录成功处理的数量
     */
    long totalSuccess();

    /**
     * Get blocked request count per minute (totalBlockRequest).
     *
     * @return total blocked request count per minute
     * 获取该资源被阻塞的请求数量
     */
    long blockRequest();

    /**
     * Get exception count per minute.
     *
     * @return total business exception count per minute
     * 总的异常次数
     */
    long totalException();

    /**
     * Get pass request per second.
     *
     * @return QPS of passed requests
     * pass相关的qps
     */
    double passQps();

    /**
     * Get block request per second.
     *
     * @return QPS of blocked requests
     */
    double blockQps();

    /**
     * Get {@link #passQps()} + {@link #blockQps()} request per second.
     *
     * @return QPS of passed and blocked requests
     */
    double totalQps();

    /**
     * Get {@link Entry#exit()} request per second.
     *
     * @return QPS of completed requests
     */
    double successQps();

    /**
     * Get estimated max success QPS till now.
     *
     * @return max completed QPS
     * 获取预估的最大成功qps  successQps() 相当于是平均值  这个是峰值
     */
    double maxSuccessQps();

    /**
     * Get exception count per second.
     *
     * @return QPS of exception occurs
     */
    double exceptionQps();

    /**
     * Get average rt per second.
     *
     * @return average response time per second
     * RT (响应时间)  这里代表平均响应时间
     */
    double avgRt();

    /**
     * Get minimal response time.
     *
     * @return recorded minimal response time
     */
    double minRt();

    /**
     * Get current active thread count.
     *
     * @return current active thread count
     * 当前线程数量 (应该是针对该资源)   想一下 如果是spring boot 那么客户端每发起一个请求会有一个线程专门处理servlet的逻辑 这个统计的就是那个线程???
     */
    int curThreadNum();

    /**
     * Get last second block QPS.
     * 获取上一秒的 block qps
     */
    double previousBlockQps();

    /**
     * Last window QPS.
     */
    double previousPassQps();

    /**
     * Fetch all valid metric nodes of resources.
     *
     * @return valid metric nodes of resources
     * 获取所有统计信息
     */
    Map<Long, MetricNode> metrics();

    /**
     * Fetch all raw metric items that satisfies the time predicate.
     *
     * @param timePredicate time predicate
     * @return raw metric items that satisfies the time predicate
     * @since 1.7.0
     */
    List<MetricNode> rawMetricsInMin(Predicate<Long> timePredicate);

    /**
     * Add pass count.
     *
     * @param count count to add pass
     */
    void addPassRequest(int count);

    /**
     * Add rt and success count.
     *
     * @param rt      response time
     * @param success success count to add
     */
    void addRtAndSuccess(long rt, int success);

    /**
     * Increase the block count.
     *
     * @param count count to add
     */
    void increaseBlockQps(int count);

    /**
     * Add the biz exception count.
     *
     * @param count count to add
     */
    void increaseExceptionQps(int count);

    /**
     * Increase current thread count.
     */
    void increaseThreadNum();

    /**
     * Decrease current thread count.
     */
    void decreaseThreadNum();

    /**
     * Reset the internal counter. Reset is needed when {@link IntervalProperty#INTERVAL} or
     * {@link SampleCountProperty#SAMPLE_COUNT} is changed.
     */
    void reset();
}
