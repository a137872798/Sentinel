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
package com.alibaba.csp.sentinel.cluster;

import java.util.Map;

/**
 * Result entity of acquiring cluster flow token.
 *
 * @author Eric Zhao
 * @since 1.4.0
 * 某次申请获取token 的结果
 */
public class TokenResult {

    /**
     * 返回的状态码
     */
    private Integer status;

    /**
     * 还剩余多少token
     */
    private int remaining;
    /**
     * 应该是没有拿到token 还需要等待多少时间 对应 StatisticNode
     */
    private int waitInMs;

    /**
     * 携带的一组额外信息
     */
    private Map<String, String> attachments;

    public TokenResult() {}

    public TokenResult(Integer status) {
        this.status = status;
    }

    public Integer getStatus() {
        return status;
    }

    public TokenResult setStatus(Integer status) {
        this.status = status;
        return this;
    }

    public int getRemaining() {
        return remaining;
    }

    public TokenResult setRemaining(int remaining) {
        this.remaining = remaining;
        return this;
    }

    public int getWaitInMs() {
        return waitInMs;
    }

    public TokenResult setWaitInMs(int waitInMs) {
        this.waitInMs = waitInMs;
        return this;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public TokenResult setAttachments(Map<String, String> attachments) {
        this.attachments = attachments;
        return this;
    }

    @Override
    public String toString() {
        return "TokenResult{" +
            "status=" + status +
            ", remaining=" + remaining +
            ", waitInMs=" + waitInMs +
            ", attachments=" + attachments +
            '}';
    }
}
