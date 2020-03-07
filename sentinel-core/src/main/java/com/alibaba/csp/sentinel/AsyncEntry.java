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
package com.alibaba.csp.sentinel;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.NullContext;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;

/**
 * The entry for asynchronous resources.
 *
 * @author Eric Zhao
 * @since 0.2.0
 */
public class AsyncEntry extends CtEntry {

    /**
     * 本入口关联的上下文对象
     */
    private Context asyncContext;

    /**
     * 核心的处理流程就在ProcessorSlot 中
     * 注意这里的初始化 只是设置了context 字段 没有设置 asyncContext字段
     * @param resourceWrapper
     * @param chain
     * @param context
     */
    AsyncEntry(ResourceWrapper resourceWrapper, ProcessorSlot<Object> chain, Context context) {
        super(resourceWrapper, chain, context);
    }

    /**
     * Remove current entry from local context, but does not exit.
     */
    void cleanCurrentEntryInLocal() {
        if (context instanceof NullContext) {
            return;
        }
        Context originalContext = context;
        if (originalContext != null) {
            // 获取当前上下文绑定的entry 对象
            Entry curEntry = originalContext.getCurEntry();
            // 此时原始的context curEntry一定是指向该对象的  在 该对象的构造器中会自动修改context指向的entry
            if (curEntry == this) {
                Entry parent = this.parent;
                // 这里要移除 原本context的curEntry  也就是一旦开启异步 它不会跟之前的调用链共享一个上下文 而是单独获得了一个上下文
                originalContext.setCurEntry(parent);
                if (parent != null) {
                    ((CtEntry)parent).child = null;
                }
            } else {
                String curEntryName = curEntry == null ? "none"
                    : curEntry.resourceWrapper.getName() + "@" + curEntry.hashCode();
                String msg = String.format("Bad async context state, expected entry: %s, but actual: %s",
                    getResourceWrapper().getName() + "@" + hashCode(), curEntryName);
                throw new IllegalStateException(msg);
            }
        }
    }

    public Context getAsyncContext() {
        return asyncContext;
    }

    /**
     * The async context should not be initialized until the node for current resource has been set to current entry.
     * 开始初始化异步上下文对象
     */
    void initAsyncContext() {
        if (asyncContext == null) {
            if (context instanceof NullContext) {
                asyncContext = context;
                return;
            }
            // 该对象内部同时包含 context 和异步的context
            this.asyncContext = Context.newAsyncContext(context.getEntranceNode(), context.getName())
                .setOrigin(context.getOrigin())
                .setCurEntry(this);
        } else {
            RecordLog.warn(
                "[AsyncEntry] Duplicate initialize of async context for entry: " + resourceWrapper.getName());
        }
    }

    @Override
    protected void clearEntryContext() {
        super.clearEntryContext();
        this.asyncContext = null;
    }

    @Override
    protected Entry trueExit(int count, Object... args) throws ErrorEntryFreeException {
        exitForContext(asyncContext, count, args);

        return parent;
    }
}
