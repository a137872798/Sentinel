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
package com.alibaba.csp.sentinel.cluster.client.handler;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.csp.sentinel.cluster.response.ClusterResponse;

import io.netty.channel.ChannelPromise;

/**
 * @author Eric Zhao
 * @since 1.4.0
 * 相当于响应池 每个发出的请求都会在这里保存对应的 future(promise)对象
 * sentinel TokenClient 始终使用同步模式阻塞等待结果 所以不需要后台线程扫描超时对象
 */
public final class TokenClientPromiseHolder {

    private static final Map<Integer, SimpleEntry<ChannelPromise, ClusterResponse>> PROMISE_MAP = new ConcurrentHashMap<>();

    public static void putPromise(int xid, ChannelPromise promise) {
        PROMISE_MAP.put(xid, new SimpleEntry<ChannelPromise, ClusterResponse>(promise, null));
    }

    public static SimpleEntry<ChannelPromise, ClusterResponse> getEntry(int xid) {
        return PROMISE_MAP.get(xid);
    }

    public static void remove(int xid) {
        PROMISE_MAP.remove(xid);
    }

    /**
     * 代表 某个请求id对应的响应体已经收到
     * @param xid
     * @param response
     * @param <T>
     * @return
     */
    public static <T> boolean completePromise(int xid, ClusterResponse<T> response) {
        if (!PROMISE_MAP.containsKey(xid)) {
            return false;
        }
        SimpleEntry<ChannelPromise, ClusterResponse> entry = PROMISE_MAP.get(xid);
        if (entry != null) {
            ChannelPromise promise = entry.getKey();
            if (promise.isDone() || promise.isCancelled()) {
                return false;
            }
            entry.setValue(response);
            promise.setSuccess();
            return true;
        }
        return false;
    }

    private TokenClientPromiseHolder() {}
}
