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
package com.alibaba.csp.sentinel.slots.block;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.node.DefaultNode;

/**
 * Base interface of all rules.
 *
 * @author youji.zj
 * 该对象用于检测本次请求是否应该被限流
 */
public interface Rule {

    /**
     * Check whether current statistical indicators meet this rule, which means not exceeding any threshold.
     *
     * @param context current {@link Context}   本次资源相关的context
     * @param node    current {@link com.alibaba.csp.sentinel.node.Node}   当前资源相关的node
     * @param count   tokens needed. 需要多少token
     * @param args    arguments of the original invocation.  其他相关参数
     * @return If current statistical indicators not exceeding any threshold return true, otherwise return false. 是否需要被限流
     */
    boolean passCheck(Context context, DefaultNode node, int count, Object... args);

}
