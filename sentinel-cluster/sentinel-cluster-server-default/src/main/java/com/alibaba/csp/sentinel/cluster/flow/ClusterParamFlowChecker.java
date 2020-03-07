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
package com.alibaba.csp.sentinel.cluster.flow;

import java.util.Collection;

import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.flow.rule.ClusterParamFlowRuleManager;
import com.alibaba.csp.sentinel.cluster.flow.statistic.ClusterParamMetricStatistics;
import com.alibaba.csp.sentinel.cluster.flow.statistic.limit.GlobalRequestLimiter;
import com.alibaba.csp.sentinel.cluster.flow.statistic.metric.ClusterParamMetric;
import com.alibaba.csp.sentinel.cluster.server.log.ClusterServerStatLogUtil;
import com.alibaba.csp.sentinel.slots.block.ClusterRuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;

/**
 * @author jialiang.linjl
 * @author Eric Zhao
 * @since 1.4.0
 */
public final class ClusterParamFlowChecker {

    static boolean allowProceed(long flowId) {
        String namespace = ClusterParamFlowRuleManager.getNamespace(flowId);
        return GlobalRequestLimiter.tryPass(namespace);
    }

    /**
     * 相比 clusterParamFlowCheck 就是多了一组参数
     * @param rule
     * @param count
     * @param values
     * @return
     */
    static TokenResult acquireClusterToken(ParamFlowRule rule, int count, Collection<Object> values) {
        Long id = rule.getClusterConfig().getFlowId();

        // 如果在 qps 这层被拦截了返回  TOO_MANY_REQUEST
        if (!allowProceed(id)) {
            return new TokenResult(TokenResultStatus.TOO_MANY_REQUEST);
        }

        // 找到统计数据
        ClusterParamMetric metric = ClusterParamMetricStatistics.getMetric(id);
        if (metric == null) {
            // Unexpected state, return FAIL.
            return new TokenResult(TokenResultStatus.FAIL);
        }
        // 当参数为空时 总是请求成功
        if (values == null || values.isEmpty()) {
            // Empty parameter list will always pass.
            return new TokenResult(TokenResultStatus.OK);
        }
        double remaining = -1;
        boolean hasPassed = true;
        Object blockObject = null;
        for (Object value : values) {
            double latestQps = metric.getAvg(value);
            // 根据当前规则来计算 全局阈值  只要有一个参数 对应的qps不满足条件 本次申请token的操作就失败了
            double threshold = calcGlobalThreshold(rule, value);
            double nextRemaining = threshold - latestQps - count;
            remaining = nextRemaining;
            if (nextRemaining < 0) {
                hasPassed = false;
                blockObject = value;
                break;
            }
        }

        // 为每个参数增加qps  param实际上就相当于泛化了
        if (hasPassed) {
            for (Object value : values) {
                metric.addValue(value, count);
            }
            ClusterServerStatLogUtil.log(String.format("param|pass|%d", id));
        } else {
            ClusterServerStatLogUtil.log(String.format("param|block|%d|%s", id, blockObject));
        }
        // 如果本次请求携带多个 param 那么 remaining统一返回 -1
        if (values.size() > 1) {
            // Remaining field is unsupported for multi-values.
            remaining = -1;
        }

        return hasPassed ? newPassResponse((int)remaining): newBlockResponse();
    }

    private static TokenResult newPassResponse(int remaining) {
        return new TokenResult(TokenResultStatus.OK)
            .setRemaining(remaining)
            .setWaitInMs(0);
    }

    private static TokenResult newBlockResponse() {
        return new TokenResult(TokenResultStatus.BLOCKED)
            .setRemaining(0)
            .setWaitInMs(0);
    }

    private static double calcGlobalThreshold(ParamFlowRule rule, Object value) {
        double count = getRawThreshold(rule, value);
        switch (rule.getClusterConfig().getThresholdType()) {
            case ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL:
                return count;
            case ClusterRuleConstant.FLOW_THRESHOLD_AVG_LOCAL:
            default:
                int connectedCount = ClusterParamFlowRuleManager.getConnectedCount(rule.getClusterConfig().getFlowId());
                return count * connectedCount;
        }
    }

    private static double getRawThreshold(ParamFlowRule rule, Object value) {
        Integer itemCount = rule.retrieveExclusiveItemCount(value);
        if (itemCount == null) {
            return rule.getCount();
        } else {
            return itemCount;
        }
    }

    private ClusterParamFlowChecker() {}
}
