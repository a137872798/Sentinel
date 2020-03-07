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
package com.alibaba.csp.sentinel.cluster.flow.statistic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.statistic.data.ClusterFlowEvent;
import com.alibaba.csp.sentinel.cluster.flow.statistic.metric.ClusterMetric;
import com.alibaba.csp.sentinel.cluster.flow.statistic.metric.ClusterParamMetric;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * @author Eric Zhao
 * @since 1.4.1
 * 该对象负责生成 ClusterMetricNode
 */
public class ClusterMetricNodeGenerator {

    public static Map<String, List<ClusterMetricNode>> generateCurrentNodeMap(String namespace) {
        Map<String, List<ClusterMetricNode>> map = new HashMap<>();
        // 获取该namespace 下管理的所有流量对象
        Set<Long> flowIds = ClusterFlowRuleManager.getFlowIdSet(namespace);
        // 另一组限流对象
        Set<Long> paramFlowIds = ClusterParamFlowRuleManager.getFlowIdSet(namespace);
        for (Long id : flowIds) {
            // flowId -> flowRule -> node 一一对应的关系
            ClusterMetricNode node = flowToMetricNode(id);
            if (node == null) {
                continue;
            }
            // 将节点信息都保存到map中
            putToMap(map, node);
        }
        for (Long id : paramFlowIds) {
            // 从另一个容器中获取node
            ClusterMetricNode node = paramToMetricNode(id);
            if (node == null) {
                continue;
            }
            putToMap(map, node);
        }

        return map;
    }

    /**
     * 以resourceName 作为key 保存node信息
     * @param map
     * @param node
     */
    private static void putToMap(Map<String, List<ClusterMetricNode>> map, ClusterMetricNode node) {
        List<ClusterMetricNode> nodeList = map.get(node.getResourceName());
        if (nodeList == null) {
            nodeList = new ArrayList<>();
            map.put(node.getResourceName(), nodeList);
        }
        nodeList.add(node);
    }

    /**
     * 根据 flowId 找到对应的规则对象
     * @param flowId
     * @return
     */
    public static ClusterMetricNode flowToMetricNode(long flowId) {
        // 获取对应的规则 以及统计数据的槽
        FlowRule rule = ClusterFlowRuleManager.getFlowRuleById(flowId);
        if (rule == null) {
            return null;
        }
        ClusterMetric metric = ClusterMetricStatistics.getMetric(flowId);
        if (metric == null) {
            return new ClusterMetricNode().setFlowId(flowId)
                .setResourceName(rule.getResource());
        }
        return new ClusterMetricNode()
            .setFlowId(flowId)
            .setResourceName(rule.getResource())
            .setBlockQps(metric.getAvg(ClusterFlowEvent.BLOCK))
            .setPassQps(metric.getAvg(ClusterFlowEvent.PASS))
            .setTimestamp(TimeUtil.currentTimeMillis());
    }

    public static ClusterMetricNode paramToMetricNode(long flowId) {
        ParamFlowRule rule = ClusterParamFlowRuleManager.getParamRuleById(flowId);
        if (rule == null) {
            return null;
        }
        ClusterParamMetric metric = ClusterParamMetricStatistics.getMetric(flowId);
        if (metric == null) {
            return new ClusterMetricNode().setFlowId(flowId)
                .setResourceName(rule.getResource())
                .setTimestamp(TimeUtil.currentTimeMillis())
                .setTopParams(new HashMap<Object, Double>(0));
        }
        return new ClusterMetricNode()
            .setFlowId(flowId)
            .setResourceName(rule.getResource())
            .setTimestamp(TimeUtil.currentTimeMillis())
            .setTopParams(metric.getTopValues(5));
    }
}
