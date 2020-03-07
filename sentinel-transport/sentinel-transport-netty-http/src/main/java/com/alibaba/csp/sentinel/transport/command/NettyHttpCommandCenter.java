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
package com.alibaba.csp.sentinel.transport.command;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.alibaba.csp.sentinel.command.CommandHandler;
import com.alibaba.csp.sentinel.command.CommandHandlerProvider;
import com.alibaba.csp.sentinel.concurrent.NamedThreadFactory;
import com.alibaba.csp.sentinel.spi.SpiOrder;
import com.alibaba.csp.sentinel.transport.command.netty.HttpServer;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.transport.CommandCenter;

/**
 * Implementation of {@link CommandCenter} based on Netty HTTP library.
 *
 * @author Eric Zhao
 * 应该是启动一个接收命令行的服务器
 */
@SpiOrder(SpiOrder.LOWEST_PRECEDENCE - 100)
public class NettyHttpCommandCenter implements CommandCenter {

    /**
     * 基于netty.channel 实现的服务器
     */
    private final HttpServer server = new HttpServer();

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    private final ExecutorService pool = Executors.newSingleThreadExecutor(
        new NamedThreadFactory("sentinel-netty-command-center-executor"));

    /**
     * 使用一个额外的线程 异步启动服务器
     * @throws Exception
     */
    @Override
    public void start() throws Exception {
        // 启动服务器的线程会被阻塞
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                } catch (Exception ex) {
                    RecordLog.info("Start netty server error", ex);
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        });
    }

    @Override
    public void stop() throws Exception {
        server.close();
        pool.shutdownNow();
    }

    @Override
    public void beforeStart() throws Exception {
        // Register handlers
        Map<String, CommandHandler> handlers = CommandHandlerProvider.getInstance().namedHandlers();
        server.registerCommands(handlers);
    }
}
