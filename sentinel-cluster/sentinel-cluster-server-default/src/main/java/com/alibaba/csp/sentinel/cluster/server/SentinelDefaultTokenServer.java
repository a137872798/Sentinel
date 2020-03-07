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
package com.alibaba.csp.sentinel.cluster.server;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.csp.sentinel.cluster.ClusterStateManager;
import com.alibaba.csp.sentinel.cluster.registry.ConfigSupplierRegistry;
import com.alibaba.csp.sentinel.cluster.server.config.ClusterServerConfigManager;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfig;
import com.alibaba.csp.sentinel.cluster.server.config.ServerTransportConfigObserver;
import com.alibaba.csp.sentinel.cluster.server.connection.ConnectionManager;
import com.alibaba.csp.sentinel.init.InitExecutor;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.util.HostNameUtil;
import com.alibaba.csp.sentinel.util.StringUtil;

/**
 * @author Eric Zhao
 * @since 1.4.0
 * 作为 nettyServer的一个开关
 */
public class SentinelDefaultTokenServer implements ClusterTokenServer {

    /**
     * 是否使用嵌套模式
     */
    private final boolean embedded;

    private ClusterTokenServer server;
    private int port;
    private final AtomicBoolean shouldStart = new AtomicBoolean(false);

    static {
        // 初始化编解码器等
        InitExecutor.doInit();
    }

    public SentinelDefaultTokenServer() {
        this(false);
    }

    /**
     * 默认该节点只能作为 server 自身不能执行业务
     * @param embedded
     */
    public SentinelDefaultTokenServer(boolean embedded) {
        this.embedded = embedded;
        ClusterServerConfigManager.addTransportConfigChangeObserver(new ServerTransportConfigObserver() {
            @Override
            public void onTransportConfigChange(ServerTransportConfig config) {
                changeServerConfig(config);
            }
        });
        initNewServer();
    }

    private void initNewServer() {
        if (server != null) {
            return;
        }
        int port = ClusterServerConfigManager.getPort();
        if (port > 0) {
            this.server = new NettyTransportServer(port);
            this.port = port;
        }
    }

    private synchronized void changeServerConfig(ServerTransportConfig config) {
        if (config == null || config.getPort() <= 0) {
            return;
        }
        int newPort = config.getPort();
        if (newPort == port) {
            return;
        }
        try {
            // 使用新端口重启服务器
            if (server != null) {
                stopServer();
            }
            this.server = new NettyTransportServer(newPort);
            this.port = newPort;
            startServerIfScheduled();
        } catch (Exception ex) {
            RecordLog.warn("[SentinelDefaultTokenServer] Failed to apply modification to token server", ex);
        }
    }

    private void startServerIfScheduled() throws Exception {
        if (shouldStart.get()) {
            if (server != null) {
                server.start();
                ClusterStateManager.markToServer();
                if (embedded) {
                    RecordLog.info("[SentinelDefaultTokenServer] Running in embedded mode");
                    handleEmbeddedStart();
                }
            }
        }
    }

    private void stopServer() throws Exception {
        if (server != null) {
            server.stop();
            if (embedded) {
                handleEmbeddedStop();
            }
        }
    }

    // 为什么只有嵌套模式需要这样做呢???

    /**
     * 如果是嵌套模式下进行停机
     */
    private void handleEmbeddedStop() {
        String namespace = ConfigSupplierRegistry.getNamespaceSupplier().get();
        if (StringUtil.isNotEmpty(namespace)) {
            ConnectionManager.removeConnection(namespace, HostNameUtil.getIp());
        }
    }

    /**
     * 以嵌套 模式启动的时候还要注册连接
     */
    private void handleEmbeddedStart() {
        // client 去哪个server判断本次限流结果 就是通过namespace 来划分的  也就是每个服务器会有自己的namespace
        // (当然一个jvm上可以启动多个server)
        String namespace = ConfigSupplierRegistry.getNamespaceSupplier().get();
        if (StringUtil.isNotEmpty(namespace)) {
            // Mark server global mode as embedded.
            ClusterServerConfigManager.setEmbedded(true);
            if (!ClusterServerConfigManager.getNamespaceSet().contains(namespace)) {
                Set<String> namespaceSet = new HashSet<>(ClusterServerConfigManager.getNamespaceSet());
                namespaceSet.add(namespace);
                ClusterServerConfigManager.loadServerNamespaceSet(namespaceSet);
            }

            // Register self to connection group.
            ConnectionManager.addConnection(namespace, HostNameUtil.getIp());
        }
    }

    @Override
    public void start() throws Exception {
        if (shouldStart.compareAndSet(false, true)) {
            startServerIfScheduled();
        }
    }

    @Override
    public void stop() throws Exception {
        if (shouldStart.compareAndSet(true, false)) {
            stopServer();
        }
    }
}
