/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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

/**
 * @author Eric Zhao
 * @since 1.5.0
 * 尝试占据什么东西
 */
public interface OccupySupport {

    /**
     * Try to occupy latter time windows' tokens. If occupy success, a value less than
     * {@code occupyTimeout} in {@link OccupyTimeoutProperty} will be return.
     *
     * <p>
     * Each time we occupy tokens of the future window, current thread should sleep for the
     * corresponding time for smoothing QPS. We can't occupy tokens of the future with unlimited,
     * the sleep time limit is {@code occupyTimeout} in {@link OccupyTimeoutProperty}.
     * </p>
     *
     * @param currentTime  current time millis.
     * @param acquireCount tokens count to acquire.
     * @param threshold    qps threshold.    如果当前申请的token数会使得超过该qps 那么申请失败
     * @return time should sleep. Time >= {@code occupyTimeout} in {@link OccupyTimeoutProperty} means
     * occupy fail, in this case, the request should be rejected immediately.
     * 尝试申请多少个token   同时返回结果为推荐阻塞的时长
     */
    long tryOccupyNext(long currentTime, int acquireCount, double threshold);

    /**
     * Get current waiting amount. Useful for debug.
     *
     * @return current waiting amount
     * 获取当前的阻塞时间
     */
    long waiting();

    /**
     * Add request that occupied.
     *
     * @param futureTime   future timestamp that the acquireCount should be added on.
     * @param acquireCount tokens count.  请求的tokens数量
     *                     添加一个占据的请求
     */
    void addWaitingRequest(long futureTime, int acquireCount);

    /**
     * Add occupied pass request, which represents pass requests that borrow the latter windows' token.
     *
     * @param acquireCount tokens count.
     */
    void addOccupiedPass(int acquireCount);

    /**
     * Get current occupied pass QPS.
     *
     * @return current occupied pass QPS
     * 获取当前的QPS (每秒处理的请求数)
     */
    double occupiedPassQps();
}
