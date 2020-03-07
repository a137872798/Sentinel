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
package com.alibaba.csp.sentinel.context;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphO;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.EntranceNode;
import com.alibaba.csp.sentinel.node.Node;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;

/**
 * This class holds metadata of current invocation:<br/>
 *
 * <ul>
 * <li>the {@link EntranceNode}: the root of the current invocation
 * tree.</li>
 * <li>the current {@link Entry}: the current invocation point.</li>
 * <li>the current {@link Node}: the statistics related to the
 * {@link Entry}.</li>
 * <li>the origin: The origin is useful when we want to control different
 * invoker/consumer separately. Usually the origin could be the Service Consumer's app name
 * or origin IP. </li>
 * </ul>
 * <p>
 * Each {@link SphU}#entry() or {@link SphO}#entry() should be in a {@link Context},
 * if we don't invoke {@link ContextUtil}#enter() explicitly, DEFAULT context will be used.
 * </p>
 * <p>
 * A invocation tree will be created if we invoke {@link SphU}#entry() multi times in
 * the same context.
 * </p>
 * <p>
 * Same resource in different context will count separately, see {@link NodeSelectorSlot}.
 * </p>
 *
 * @author jialiang.linjl
 * @author leyou(lihao)
 * @author Eric Zhao
 * @see ContextUtil
 * @see NodeSelectorSlot
 * 在调用某个资源的过程中使用的上下文对象
 * 并发调用某一资源时 通过 ThreadLocal 来解决并发问题
 */
public class Context {

    /**
     * Context name.
     * 上下文的名称
     */
    private final String name;

    /**
     * The entrance node of current invocation tree.
     * 该上下文关联的 入口节点
     */
    private DefaultNode entranceNode;

    /**
     * Current processing entry.
     * 当前正在处理的entry
     */
    private Entry curEntry;

    /**
     * The origin of this context (usually indicate different invokers, e.g. service consumer name or origin IP).
     */
    private String origin = "";

    /**
     * 本上下文是否处在异步环境
     */
    private final boolean async;

    /**
     * Create a new async context.
     *
     * @param entranceNode entrance node of the context
     * @param name context name
     * @return the new created context
     * @since 0.2.0
     */
    public static Context newAsyncContext(DefaultNode entranceNode, String name) {
        return new Context(name, entranceNode, true);
    }

    public Context(DefaultNode entranceNode, String name) {
        this(name, entranceNode, false);
    }

    public Context(String name, DefaultNode entranceNode, boolean async) {
        this.name = name;
        this.entranceNode = entranceNode;
        this.async = async;
    }

    public boolean isAsync() {
        return async;
    }

    public String getName() {
        return name;
    }

    public Node getCurNode() {
        return curEntry == null ? null : curEntry.getCurNode();
    }

    public Context setCurNode(Node node) {
        this.curEntry.setCurNode(node);
        return this;
    }

    public Entry getCurEntry() {
        return curEntry;
    }

    /**
     * 修改当前curEntry
     * @param curEntry
     * @return
     */
    public Context setCurEntry(Entry curEntry) {
        this.curEntry = curEntry;
        return this;
    }

    public String getOrigin() {
        return origin;
    }

    public Context setOrigin(String origin) {
        this.origin = origin;
        return this;
    }

    public double getOriginTotalQps() {
        return getOriginNode() == null ? 0 : getOriginNode().totalQps();
    }

    public double getOriginBlockQps() {
        return getOriginNode() == null ? 0 : getOriginNode().blockQps();
    }

    public double getOriginPassReqQps() {
        return getOriginNode() == null ? 0 : getOriginNode().successQps();
    }

    public double getOriginPassQps() {
        return getOriginNode() == null ? 0 : getOriginNode().passQps();
    }

    public long getOriginTotalRequest() {
        return getOriginNode() == null ? 0 : getOriginNode().totalRequest();
    }

    public long getOriginBlockRequest() {
        return getOriginNode() == null ? 0 : getOriginNode().blockRequest();
    }

    public double getOriginAvgRt() {
        return getOriginNode() == null ? 0 : getOriginNode().avgRt();
    }

    public int getOriginCurThreadNum() {
        return getOriginNode() == null ? 0 : getOriginNode().curThreadNum();
    }

    public DefaultNode getEntranceNode() {
        return entranceNode;
    }

    /**
     * Get the parent {@link Node} of the current.
     *
     * @return the parent node of the current.
     * 返回该上下文关联的最上层节点
     */
    public Node getLastNode() {
        if (curEntry != null && curEntry.getLastNode() != null) {
            return curEntry.getLastNode();
        } else {
            return entranceNode;
        }
    }

    public Node getOriginNode() {
        return curEntry == null ? null : curEntry.getOriginNode();
    }

    @Override
    public String toString() {
        return "Context{" +
            "name='" + name + '\'' +
            ", entranceNode=" + entranceNode +
            ", curEntry=" + curEntry +
            ", origin='" + origin + '\'' +
            ", async=" + async +
            '}';
    }
}
