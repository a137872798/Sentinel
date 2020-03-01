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

import java.util.Collection;

/**
 * Service interface of flow control.
 *
 * @author Eric Zhao
 * @since 1.4.0
 * token 相关的服务层   该接口属于cluster 层 也就是在集群中有一个中心节点 用于协调整个集群的数据(类似注册中心的特性)
 */
public interface TokenService {

    /**
     * Request tokens from remote token server.
     *
     * @param ruleId the unique rule ID
     * @param acquireCount token count to acquire
     * @param prioritized whether the request is prioritized   是否要优先处理
     * @return result of the token request
     * ruleId 本身在整个cluster 中唯一确定  比如某个服务想要申请token 那么就需要从协调点查看能否正常申请成功 那么就需要传入能全局性定义的ruleId
     * 那不是挺奇怪的么  协调节点相当于要接收相当于所有集群内节点的总请求量 那就没有做集群的意义了
     */
    TokenResult requestToken(Long ruleId, int acquireCount, boolean prioritized);

    /**
     * Request tokens for a specific parameter from remote token server.
     *
     * @param ruleId the unique rule ID
     * @param acquireCount token count to acquire
     * @param params parameter list
     * @return result of the token request
     * 使用一组额外的参数去尝试获取token
     */
    TokenResult requestParamToken(Long ruleId, int acquireCount, Collection<Object> params);
}
