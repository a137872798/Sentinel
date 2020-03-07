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
package com.alibaba.csp.sentinel.cluster.flow.statistic.metric;

import com.alibaba.csp.sentinel.cluster.flow.statistic.data.ClusterFlowEvent;
import com.alibaba.csp.sentinel.cluster.flow.statistic.data.ClusterMetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.LongAdder;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;

/**
 * @author Eric Zhao
 * @since 1.4.0
 */
public class ClusterMetricLeapArray extends LeapArray<ClusterMetricBucket> {

    /**
     * 统计已经被提前占用的事件的数量
     */
    private final LongAdder[] occupyCounter;
    private boolean hasOccupied = false;

    public ClusterMetricLeapArray(int sampleCount, int intervalInMs) {
        super(sampleCount, intervalInMs);
        ClusterFlowEvent[] events = ClusterFlowEvent.values();
        this.occupyCounter = new LongAdder[events.length];
        for (ClusterFlowEvent event : events) {
            occupyCounter[event.ordinal()] = new LongAdder();
        }
    }

    @Override
    public ClusterMetricBucket newEmptyBucket(long timeMillis) {
        return new ClusterMetricBucket();
    }

    /**
     * 进入到该方法时 已经做好并发处理了
     * @param w
     * @param startTime  the start time of the bucket in milliseconds
     * @return
     */
    @Override
    protected WindowWrap<ClusterMetricBucket> resetWindowTo(WindowWrap<ClusterMetricBucket> w, long startTime) {
        w.resetTo(startTime);
        w.value().reset();
        transferOccupyToBucket(w.value());
        return w;
    }

    /**
     * 新的窗口在创建时 就将之前被抢占的请求设置到bucket中
     * @param bucket
     */
    private void transferOccupyToBucket(/*@Valid*/ ClusterMetricBucket bucket) {
        // 如果当前处在被占用的状态 那么将占用的数据转移到新的buffer中
        if (hasOccupied) {
            // 将occupy桶中Pass的数量累加到newBucket的occupyPass上
            transferOccupiedCount(bucket, ClusterFlowEvent.PASS, ClusterFlowEvent.OCCUPIED_PASS);
            // 将occupy桶中Pass的数量累加到newBucket的pass上
            transferOccupiedThenReset(bucket, ClusterFlowEvent.PASS);
            // 将occupy桶中PASS_REQUEST的数量累加到newBucket的PASS_REQUEST上
            transferOccupiedThenReset(bucket, ClusterFlowEvent.PASS_REQUEST);
            hasOccupied = false;
        }
    }

    private void transferOccupiedCount(ClusterMetricBucket bucket, ClusterFlowEvent source, ClusterFlowEvent target) {
        bucket.add(target, occupyCounter[source.ordinal()].sum());
    }

    private void transferOccupiedThenReset(ClusterMetricBucket bucket, ClusterFlowEvent event) {
        bucket.add(event, occupyCounter[event.ordinal()].sumThenReset());
    }

    // occupy对应那些设置了优先标识的req  当本次已经没有足够的token获取了 就抢先占领下个时间窗口的token

    public void addOccupyPass(int count) {
        occupyCounter[ClusterFlowEvent.PASS.ordinal()].add(count);
        occupyCounter[ClusterFlowEvent.PASS_REQUEST.ordinal()].add(1);
        this.hasOccupied = true;
    }

    public long getOccupiedCount(ClusterFlowEvent event) {
        return occupyCounter[event.ordinal()].sum();
    }

    public long getFirstCountOfWindow(ClusterFlowEvent event) {
        if (event == null) {
            return 0;
        }
        WindowWrap<ClusterMetricBucket> windowWrap = getValidHead();
        if (windowWrap == null) {
            return 0;
        }
        return windowWrap.value().get(event);
    }
}
