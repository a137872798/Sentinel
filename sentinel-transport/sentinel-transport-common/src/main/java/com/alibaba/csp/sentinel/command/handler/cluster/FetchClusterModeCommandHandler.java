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
package com.alibaba.csp.sentinel.command.handler.cluster;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.command.CommandHandler;
import com.alibaba.csp.sentinel.command.CommandRequest;
import com.alibaba.csp.sentinel.command.CommandResponse;
import com.alibaba.csp.sentinel.command.annotation.CommandMapping;
import com.alibaba.fastjson.JSONObject;

/**
 * @author Eric Zhao
 * @since 1.4.0
 * 应该跟rocketMQ 类似的 每次请求会传递一个Command 标识本次请求的类型 之后找到匹配的handler处理
 * 该handler 用于处理拉取集群模式的请求  支持的Command 应该就是 getClusterMode
 */
@CommandMapping(name = "getClusterMode", desc = "get cluster mode status")
public class FetchClusterModeCommandHandler implements CommandHandler<String> {

    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        JSONObject res = new JSONObject()
                // 本机属于 server 还是 client 还是未启动
            .fluentPut("mode", ClusterStateManager.getMode())
                // 本机最后一次改动的时间
            .fluentPut("lastModified", ClusterStateManager.getLastModified())
                // 本机是否支持启动client/server  模式
            .fluentPut("clientAvailable", isClusterClientSpiAvailable())
            .fluentPut("serverAvailable", isClusterServerSpiAvailable());
        return CommandResponse.ofSuccess(res.toJSONString());
    }

    private boolean isClusterClientSpiAvailable() {
        return TokenClientProvider.getClient() != null;
    }

    private boolean isClusterServerSpiAvailable() {
        return EmbeddedClusterTokenServerProvider.getServer() != null;
    }
}
