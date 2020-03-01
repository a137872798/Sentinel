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
package com.alibaba.csp.sentinel.eagleeye;

/**
 * 类似于日志接口
 */
public abstract class EagleEyeAppender {

    /**
     * 在原有基础上追加日志信息
     * @param log
     */
    public abstract void append(String log);

    /**
     * 将信息写入到文件中
     */
    public void flush() {
        // do nothing
    }

    public void rollOver() {
        // do nothing
    }

    public void reload() {
        // do nothing
    }

    public void close() {
        // do nothing
    }

    public void cleanup() {
        // do nothing
    }

    public String getOutputLocation() {
        return null;
    }
}
