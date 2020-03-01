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
package com.alibaba.csp.sentinel.command;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import com.alibaba.csp.sentinel.command.annotation.CommandMapping;
import com.alibaba.csp.sentinel.spi.ServiceLoaderUtil;
import com.alibaba.csp.sentinel.util.StringUtil;

/**
 * Provides and filters command handlers registered via SPI.
 *
 * @author Eric Zhao
 * commandHandler 的迭代器对象
 * 某台机器支持哪些commandHandler 是通过SPI机制进行拓展的
 */
public class CommandHandlerProvider implements Iterable<CommandHandler> {

    /**
     * JDK SPI 对象本身就实现了迭代器接口 再每次调用时 会从.service 文件中多读取一行
     */
    private final ServiceLoader<CommandHandler> serviceLoader = ServiceLoaderUtil.getServiceLoader(
        CommandHandler.class);

    /**
     * Get all command handlers annotated with {@link CommandMapping} with command name.
     *
     * @return list of all named command handlers
     * 返回name 与 handler的映射
     */
    public Map<String, CommandHandler> namedHandlers() {
        Map<String, CommandHandler> map = new HashMap<String, CommandHandler>();
        for (CommandHandler handler : serviceLoader) {
            String name = parseCommandName(handler);
            if (!StringUtil.isEmpty(name)) {
                map.put(name, handler);
            }
        }
        return map;
    }

    /**
     * 解析handler的注解
     * @param handler
     * @return
     */
    private String parseCommandName(CommandHandler handler) {
        CommandMapping commandMapping = handler.getClass().getAnnotation(CommandMapping.class);
        if (commandMapping != null) {
            return commandMapping.name();
        } else {
            return null;
        }
    }

    @Override
    public Iterator<CommandHandler> iterator() {
        return serviceLoader.iterator();
    }

    private static final CommandHandlerProvider INSTANCE = new CommandHandlerProvider();

    public static CommandHandlerProvider getInstance() {
        return INSTANCE;
    }
}
