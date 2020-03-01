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

import java.util.ArrayList;
import java.util.List;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.command.CommandHandler;
import com.alibaba.csp.sentinel.command.CommandRequest;
import com.alibaba.csp.sentinel.command.CommandResponse;
import com.alibaba.csp.sentinel.command.annotation.CommandMapping;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.node.metric.MetricNode;
import com.alibaba.csp.sentinel.node.metric.MetricSearcher;
import com.alibaba.csp.sentinel.node.metric.MetricWriter;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.util.PidUtil;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * Retrieve and aggregate {@link MetricNode} metrics.
 *
 * @author leyou
 * @author Eric Zhao
 * 请求体中携带时间信息 然后从本地的日志文件中找到对应数据 并返回
 */
@CommandMapping(name = "metric", desc = "get and aggregate metrics, accept param: "
    + "startTime={startTime}&endTime={endTime}&maxLines={maxLines}&identify={resourceName}")
public class SendMetricCommandHandler implements CommandHandler<String> {

    /**
     * 该对象用于快速查找测量数据
     */
    private volatile MetricSearcher searcher;

    private final Object lock = new Object();

    @Override
    public CommandResponse<String> handle(CommandRequest request) {
        // Note: not thread-safe.
        if (searcher == null) {
            synchronized (lock) {
                String appName = SentinelConfig.getAppName();
                if (appName == null) {
                    appName = "";
                }
                if (searcher == null) {
                    searcher = new MetricSearcher(MetricWriter.METRIC_BASE_DIR,
                        MetricWriter.formMetricFileName(appName, PidUtil.getPid()));
                }
            }
        }
        // 从请求体中获取查询需要的参数
        String startTimeStr = request.getParam("startTime");
        String endTimeStr = request.getParam("endTime");
        String maxLinesStr = request.getParam("maxLines");
        String identity = request.getParam("identity");
        long startTime = -1;
        int maxLines = 6000;
        if (StringUtil.isNotBlank(startTimeStr)) {
            startTime = Long.parseLong(startTimeStr);
        } else {
            return CommandResponse.ofSuccess("");
        }
        List<MetricNode> list;
        try {
            // Find by end time if set.
            if (StringUtil.isNotBlank(endTimeStr)) {
                long endTime = Long.parseLong(endTimeStr);
                // 查询时间段范围内的节点信息
                list = searcher.findByTimeAndResource(startTime, endTime, identity);
            } else {
                // 如果携带了查询的长度信息
                if (StringUtil.isNotBlank(maxLinesStr)) {
                    maxLines = Integer.parseInt(maxLinesStr);
                }
                maxLines = Math.min(maxLines, 12000);
                list = searcher.find(startTime, maxLines);
            }
        } catch (Exception ex) {
            return CommandResponse.ofFailure(new RuntimeException("Error when retrieving metrics", ex));
        }
        if (list == null) {
            list = new ArrayList<>();
        }
        if (StringUtil.isBlank(identity)) {
            // 增加cpu相关的2个指标
            addCpuUsageAndLoad(list);
        }
        StringBuilder sb = new StringBuilder();
        for (MetricNode node : list) {
            sb.append(node.toThinString()).append("\n");
        }
        return CommandResponse.ofSuccess(sb.toString());
    }

    /**
     * add current cpu usage and load to the metric list.
     *
     * @param list metric list, should not be null
     */
    private void addCpuUsageAndLoad(List<MetricNode> list) {
        long time = TimeUtil.currentTimeMillis() / 1000 * 1000;
        double load = SystemRuleManager.getCurrentSystemAvgLoad();
        double usage = SystemRuleManager.getCurrentCpuUsage();
        if (load > 0) {
            MetricNode loadNode = toNode(load, time, Constants.SYSTEM_LOAD_RESOURCE_NAME);
            list.add(loadNode);
        }
        if (usage > 0) {
            MetricNode usageNode = toNode(usage, time, Constants.CPU_USAGE_RESOURCE_NAME);
            list.add(usageNode);
        }
    }

    /**
     * transfer the value to a MetricNode, the value will multiply 10000 then truncate
     * to long value, and as the {@link MetricNode#passQps}.
     * <p>
     * This is an eclectic scheme before we have a standard metric format.
     * </p>
     *
     * @param value    value to save.
     * @param ts       timestamp
     * @param resource resource name.
     * @return a MetricNode represents the value.
     */
    private MetricNode toNode(double value, long ts, String resource) {
        MetricNode node = new MetricNode();
        node.setPassQps((long)(value * 10000));
        node.setTimestamp(ts);
        node.setResource(resource);
        return node;
    }
}
