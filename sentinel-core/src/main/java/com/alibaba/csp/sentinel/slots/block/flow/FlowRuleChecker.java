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
package com.alibaba.csp.sentinel.slots.block.flow;

import java.util.Collection;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.server.EmbeddedClusterTokenServerProvider;
import com.alibaba.csp.sentinel.cluster.client.TokenClientProvider;
import com.alibaba.csp.sentinel.cluster.TokenResultStatus;
import com.alibaba.csp.sentinel.cluster.TokenResult;
import com.alibaba.csp.sentinel.cluster.TokenService;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.function.Function;

/**
 * Rule checker for flow control rules.
 *
 * @author Eric Zhao
 * 负责检测某个请求是否满足条件
 */
public class FlowRuleChecker {

    public void checkFlow(Function<String, Collection<FlowRule>> ruleProvider, ResourceWrapper resource,
                          Context context, DefaultNode node, int count, boolean prioritized) throws BlockException {
        if (ruleProvider == null || resource == null) {
            return;
        }
        Collection<FlowRule> rules = ruleProvider.apply(resource.getName());
        if (rules != null) {
            // 这里要检验所有的限流规则
            for (FlowRule rule : rules) {
                if (!canPassCheck(rule, context, node, count, prioritized)) {
                    throw new FlowException(rule.getLimitApp(), rule);
                }
            }
        }
    }

    public boolean canPassCheck(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node,
                                                    int acquireCount) {
        return canPassCheck(rule, context, node, acquireCount, false);
    }

    /**
     * 注意这里prioritized是怎么使用的
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    public boolean canPassCheck(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                                    boolean prioritized) {
        // 规则一定要指定针对的应用 默认情况应该是设置成 default
        String limitApp = rule.getLimitApp();
        if (limitApp == null) {
            return true;
        }

        // 如果在集群模式下  集群检查要访问 TokenServer
        if (rule.isClusterMode()) {
            return passClusterCheck(rule, context, node, acquireCount, prioritized);
        }

        // 否则在本地检测
        return passLocalCheck(rule, context, node, acquireCount, prioritized);
    }

    /**
     * 在本地检查某个请求是否允许
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean passLocalCheck(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                          boolean prioritized) {
        Node selectedNode = selectNodeByRequesterAndStrategy(rule, context, node);
        // 没找到匹配的node时 直接成功
        if (selectedNode == null) {
            return true;
        }

        // 通过内部流量控制器 来限流
        return rule.getRater().canPass(selectedNode, acquireCount, prioritized);
    }

    /**
     * 选择一个合适的节点 通过该节点内统计的数据 走限流规则
     * 这里是处理 除了 Direct的其他策略
     * @param rule
     * @param context
     * @param node
     * @return
     */
    static Node selectReferenceNode(FlowRule rule, Context context, DefaultNode node) {
        // 其他策略会设置该值
        String refResource = rule.getRefResource();
        int strategy = rule.getStrategy();

        // 设置了Relate 或者 chain 模式 却没有引用的资源 那么直接返回 null
        if (StringUtil.isEmpty(refResource)) {
            return null;
        }

        // 如果关联到资源  那么返回对应资源的集群节点(jvm级别该资源的所有统计数据)
        // 也就是说本次的请求是否会被限流是看另一个资源的情况 它们有某种依赖关系 流弊( •̀ ω •́ )y
        if (strategy == RuleConstant.STRATEGY_RELATE) {
            // 这里返回资源对应的集群节点
            return ClusterBuilderSlot.getClusterNode(refResource);
        }

        // chain 代表针对同一个上下文 进行限流
        if (strategy == RuleConstant.STRATEGY_CHAIN) {
            // name 就是上下文名称
            if (!refResource.equals(context.getName())) {
                return null;
            }
            return node;
        }
        // No node.
        return null;
    }

    private static boolean filterOrigin(String origin) {
        // Origin cannot be `default` or `other`.
        return !RuleConstant.LIMIT_APP_DEFAULT.equals(origin) && !RuleConstant.LIMIT_APP_OTHER.equals(origin);
    }

    /**
     * 此时传递到这一层的是按照 context 和 resource 2个维度划分的node  那么这里限流的维度是什么呢 就要跟根据策略信息
     * 返回真正被拦截的那个node  相当于该方法做了一个映射 将被包装的node 映射成需要被限流的node
     * @param rule
     * @param context
     * @param node
     * @return
     */
    static Node selectNodeByRequesterAndStrategy(/*@NonNull*/ FlowRule rule, Context context, DefaultNode node) {
        // The limit app should not be empty.  规则对应的应用信息
        String limitApp = rule.getLimitApp();
        // 规则对应的策略
        int strategy = rule.getStrategy();
        // 是否包含原始信息
        String origin = context.getOrigin();

        // filterOrigin  代表 origin 不是 "default" "other"
        // 这里就代表 要根据 originNode 来进行限流
        if (limitApp.equals(origin) && filterOrigin(origin)) {
            // 如果使用的是直接策略 那么就 按照originNode 的维度 而不是资源维度进行限流
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                // Matches limit origin, return origin statistic node.
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        // 规则上可以选择指定 app信息 默认情况就是default
        } else if (RuleConstant.LIMIT_APP_DEFAULT.equals(limitApp)) {
            // 这里直接返回了 资源级别的节点进行限流 也就是 clusterNode  哦 如果是指定了 limitApp 那应该就是只限流以某个应用调用该资源
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                // Return the cluster node.
                return node.getClusterNode();
            }

            return selectReferenceNode(rule, context, node);
            // 如果本次调用该资源传入的应用名是 other
        } else if (RuleConstant.LIMIT_APP_OTHER.equals(limitApp)
                // 并且没有找到专门定义 limitApp 为 other的 rule 那么 按照本次调用该资源的 origin限流 (如果没设置就返回null)
            && FlowRuleManager.isOtherOrigin(origin, rule.getResource())) {
            if (strategy == RuleConstant.STRATEGY_DIRECT) {
                return context.getOriginNode();
            }

            return selectReferenceNode(rule, context, node);
        }

        return null;
    }

    /**
     * 在集群模式下检测某个请求是否允许
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean passClusterCheck(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                            boolean prioritized) {
        try {
            // 基于集群的检测要先获取服务器(客户端)对象  如果当前是client 模式 那么就发请求到server 如果是server模式 那么嵌套模式下
            // server内部就有一个 service 对象 可以直接进行限流判定
            TokenService clusterService = pickClusterService();
            if (clusterService == null) {
                // 当没有找到服务器时   采用降级措施(使用本地检测)
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            }
            // 这个是以namespace 也就是一个 sentinel集群为单位 唯一的id
            long flowId = rule.getClusterConfig().getFlowId();
            // 发起请求并获得一个结果
            TokenResult result = clusterService.requestToken(flowId, acquireCount, prioritized);
            // 处理本次结果
            return applyTokenResult(result, rule, context, node, acquireCount, prioritized);
            // If client is absent, then fallback to local mode.
        } catch (Throwable ex) {
            RecordLog.warn("[FlowRuleChecker] Request cluster token unexpected failed", ex);
        }
        // Fallback to local flow control when token client or server for this rule is not available.
        // If fallback is not enabled, then directly pass.
        return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
    }

    /**
     * 判断如果无法基于cluster进行检测时(或者失败时)  是否使用本地检测
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean fallbackToLocalOrPass(FlowRule rule, Context context, DefaultNode node, int acquireCount,
                                                 boolean prioritized) {
        if (rule.getClusterConfig().isFallbackToLocalWhenFail()) {
            return passLocalCheck(rule, context, node, acquireCount, prioritized);
        } else {
            // The rule won't be activated, just pass.
            return true;
        }
    }

    private static TokenService pickClusterService() {
        if (ClusterStateManager.isClient()) {
            return TokenClientProvider.getClient();
        }
        // 注意这里返回的是嵌套模式的服务器
        if (ClusterStateManager.isServer()) {
            return EmbeddedClusterTokenServerProvider.getServer();
        }
        return null;
    }

    /**
     * 处理从集群获取到的信息
     * @param result
     * @param rule
     * @param context
     * @param node
     * @param acquireCount
     * @param prioritized
     * @return
     */
    private static boolean applyTokenResult(/*@NonNull*/ TokenResult result, FlowRule rule, Context context,
                                                         DefaultNode node,
                                                         int acquireCount, boolean prioritized) {
        switch (result.getStatus()) {
            case TokenResultStatus.OK:
                return true;
            case TokenResultStatus.SHOULD_WAIT:
                // Wait for next tick.
                try {
                    Thread.sleep(result.getWaitInMs());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return true;
            case TokenResultStatus.NO_RULE_EXISTS:
            case TokenResultStatus.BAD_REQUEST:
            case TokenResultStatus.FAIL:
            case TokenResultStatus.TOO_MANY_REQUEST:
                return fallbackToLocalOrPass(rule, context, node, acquireCount, prioritized);
            case TokenResultStatus.BLOCKED:
            default:
                return false;
        }
    }
}