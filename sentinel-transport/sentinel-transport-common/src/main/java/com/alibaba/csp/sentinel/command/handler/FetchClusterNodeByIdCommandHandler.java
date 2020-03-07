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
package com.alibaba.csp.sentinel.command.handler;

import com.alibaba.csp.sentinel.command.CommandHandler;
import com.alibaba.csp.sentinel.command.CommandRequest;
import com.alibaba.csp.sentinel.command.CommandResponse;
import com.alibaba.csp.sentinel.command.annotation.CommandMapping;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.command.vo.NodeVo;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;

/**
 * @author qinan.qn
 * 根据id 定位到某个节点 每个资源在集群模式下都会被封装成一个 ClusterNode
 */
@CommandMapping(name = "clusterNodeById", desc = "get clusterNode VO by id, request param: id={resourceName}")
public class FetchClusterNodeByIdCommandHandler implements CommandHandler<String> {

    /**
     * 如果本机就是中心节点 那么可以获取到node信息 否则会返回空
     * 然后整个sentinel集群中 每个节点都会启动一个 HttpServer 以Http协议的方式 获取sentinel信息
     * @param request the request to handle
     * @return
     */
    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        String id = request.getParam("id");
        if (StringUtil.isEmpty(id)) {
            return CommandResponse.ofFailure(new IllegalArgumentException("Invalid parameter: empty clusterNode name"));
        }
        ClusterNode node = ClusterBuilderSlot.getClusterNode(id);
        if (node != null) {
            return CommandResponse.ofSuccess(JSON.toJSONString(NodeVo.fromClusterNode(id, node)));
        } else {
            return CommandResponse.ofSuccess("{}");
        }
    }
}
