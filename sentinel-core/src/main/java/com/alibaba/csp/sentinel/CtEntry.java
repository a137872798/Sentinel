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
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.context.NullContext;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;

/**
 * Linked entry within current context.
 *
 * @author jialiang.linjl
 * @author Eric Zhao
 * sentinel 的核心对象
 */
class CtEntry extends Entry {

    /**
     * 该entry 关联的 父对象
     */
    protected Entry parent = null;
    /**
     * 子对象
     */
    protected Entry child = null;

    /**
     * 一个处理链对象  该对象本身应该是链式结构
     */
    protected ProcessorSlot<Object> chain;
    /**
     * 关联的上下文对象
     */
    protected Context context;

    /**
     * 该对象的初始化 需要传入被包装的资源对象
     * @param resourceWrapper
     * @param chain
     * @param context
     */
    CtEntry(ResourceWrapper resourceWrapper, ProcessorSlot<Object> chain, Context context) {
        super(resourceWrapper);
        this.chain = chain;
        this.context = context;

        setUpEntryFor(context);
    }

    /**
     * 安装当前上下文对象
     * @param context
     */
    private void setUpEntryFor(Context context) {
        // The entry should not be associated to NullContext.
        if (context instanceof NullContext) {
            return;
        }
        // 如果传入的上下文对象 已经包含一个entry 了 那么那个entry 将会作为父对象 也就是本对象会沿父对象的一些数据
        this.parent = context.getCurEntry();
        if (parent != null) {
            ((CtEntry)parent).child = this;
        }
        // 这里更改了 curEntry
        context.setCurEntry(this);
    }

    @Override
    public void exit(int count, Object... args) throws ErrorEntryFreeException {
        trueExit(count, args);
    }

    /**
     * 本资源使用完毕 退出前 统计数据 并设置到context 中
     * @param context
     * @param count
     * @param args
     * @throws ErrorEntryFreeException
     */
    protected void exitForContext(Context context, int count, Object... args) throws ErrorEntryFreeException {
        if (context != null) {
            // Null context should exit without clean-up.
            if (context instanceof NullContext) {
                return;
            }
            // 假设 某context 先绑定到 父级entry 后 又绑定到子级entry  那么此时 父级entry 尝试exit 那么 会从子级开始递归调用exit
            // 并在最后抛出异常
            if (context.getCurEntry() != this) {
                String curEntryNameInContext = context.getCurEntry() == null ? null : context.getCurEntry().getResourceWrapper().getName();
                // Clean previous call stack.
                CtEntry e = (CtEntry)context.getCurEntry();
                while (e != null) {
                    e.exit(count, args);
                    e = (CtEntry)e.parent;
                }
                String errorMessage = String.format("The order of entry exit can't be paired with the order of entry"
                    + ", current entry in context: <%s>, but expected: <%s>", curEntryNameInContext, resourceWrapper.getName());
                throw new ErrorEntryFreeException(errorMessage);
            } else {
                // context 匹配当前entry时 先判断有无chain 对象 有的话 触发chain的exit
                if (chain != null) {
                    chain.exit(context, resourceWrapper, count, args);
                }
                // Restore the call stack.
                // 当本对象已经使用完毕上下文后 重新将父级对象设置到上下文中
                context.setCurEntry(parent);
                if (parent != null) {
                    ((CtEntry)parent).child = null;
                }
                // 代表本对象已经是context关联的最上级entry了
                if (parent == null) {
                    // Default context (auto entered) will be exited automatically.
                    // 判断该上下文的名称是否是 default_context 是的话会清除本对象
                    if (ContextUtil.isDefaultContext(context)) {
                        ContextUtil.exit();
                    }
                }
                // Clean the reference of context in current entry to avoid duplicate exit.
                clearEntryContext();
            }
        }
    }

    protected void clearEntryContext() {
        this.context = null;
    }

    /**
     * 当使用资源结束后 通过该方法 进行统计
     * @param count tokens to release.
     * @param args extra parameters
     * @return
     * @throws ErrorEntryFreeException
     */
    @Override
    protected Entry trueExit(int count, Object... args) throws ErrorEntryFreeException {
        exitForContext(context, count, args);

        return parent;
    }

    @Override
    public Node getLastNode() {
        return parent == null ? null : parent.getCurNode();
    }
}