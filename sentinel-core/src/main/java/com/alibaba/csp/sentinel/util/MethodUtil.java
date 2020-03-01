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
package com.alibaba.csp.sentinel.util;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/***
 * Util class for processing {@link Method}.
 *
 * @author youji.zj
 */
public final class MethodUtil {

    /**
     * 全局缓存 用于保存方法和对应方法名的关系
     */
    private static final Map<Method, String> methodNameMap = new ConcurrentHashMap<Method, String>();

    private static final Object LOCK = new Object();

    /**
     * Parse and resolve the method name, then cache to the map.
     *
     * @param method method instance
     * @return resolved method name
     * 获取某个方法的名称
     */
    public static String resolveMethodName(Method method) {
        if (method == null) {
            throw new IllegalArgumentException("Null method");
        }
        // 首先尝试从缓存获取 也就是如果打算多次调用一个方法 每次都要包装的话 使用缓存可以提高效率
        String methodName = methodNameMap.get(method);
        if (methodName == null) {
            synchronized (LOCK) {
                methodName = methodNameMap.get(method);
                if (methodName == null) {
                    StringBuilder sb = new StringBuilder();

                    // 这里还包含了 类名 参数名
                    String className = method.getDeclaringClass().getName();
                    String name = method.getName();
                    Class<?>[] params = method.getParameterTypes();
                    // className:methodName(paramName...)
                    sb.append(className).append(":").append(name);
                    sb.append("(");

                    int paramPos = 0;
                    for (Class<?> clazz : params) {
                        sb.append(clazz.getCanonicalName());
                        if (++paramPos < params.length) {
                            sb.append(",");
                        }
                    }
                    sb.append(")");
                    methodName = sb.toString();

                    methodNameMap.put(method, methodName);
                }
            }
        }
        return methodName;
    }

    /**
     * For test.
     */
    static void clearMethodMap() {
        methodNameMap.clear();
    }
}
