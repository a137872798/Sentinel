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
package com.alibaba.csp.sentinel.slots.statistic;

/**
 * @author Eric Zhao
 * 统计事件类型枚举
 */
public enum MetricEvent {

    /**
     * Normal pass.   正常接收的请求数
     */
    PASS,
    /**
     * Normal block.  被阻塞的请求数
     */
    BLOCK,
    /**
     * 出现异常的数量
     */
    EXCEPTION,
    /**
     * 未出现异常的数据
     */
    SUCCESS,
    /**
     * 响应时间
     */
    RT,

    /**
     * Passed in future quota (pre-occupied, since 1.5.0).
     */
    OCCUPIED_PASS
}
