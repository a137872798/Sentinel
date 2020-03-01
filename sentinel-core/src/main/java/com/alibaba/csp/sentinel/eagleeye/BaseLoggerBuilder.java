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
 * @param <T>
 */
class BaseLoggerBuilder<T extends BaseLoggerBuilder<T>> {

    /**
     * 日志前缀
     */
    protected final String loggerName;

    /**
     * 写入日志文件路径
     */
    protected String filePath = null;

    /**
     * 日志文件支持的大小
     */
    protected long maxFileSize = 1024;

    protected char entryDelimiter = '|';

    protected int maxBackupIndex = 3;

    BaseLoggerBuilder(String loggerName) {
        this.loggerName = loggerName;
    }

    // 3个日志文件路径

    public T logFilePath(String logFilePath) {
        return configLogFilePath(logFilePath, EagleEye.EAGLEEYE_LOG_DIR);
    }

    public T appFilePath(String appFilePath) {
        return configLogFilePath(appFilePath, EagleEye.APP_LOG_DIR);
    }

    public T baseLogFilePath(String baseLogFilePath) {
        return configLogFilePath(baseLogFilePath, EagleEye.BASE_LOG_DIR);
    }

    /**
     * 配置文件路径
     * @param filePathToConfig
     * @param basePath
     * @return
     */
    @SuppressWarnings("unchecked")
    private T configLogFilePath(String filePathToConfig, String basePath) {
        EagleEyeCoreUtils.checkNotNullEmpty(filePathToConfig, "filePath");
        if (filePathToConfig.charAt(0) != '/') {
            filePathToConfig = basePath + filePathToConfig;
        }
        this.filePath = filePathToConfig;
        return (T)this;
    }

    @SuppressWarnings("unchecked")
    public T configLogFilePath(String filePath) {
        EagleEyeCoreUtils.checkNotNullEmpty(filePath, "filePath");
        this.filePath = filePath;
        return (T)this;
    }

    @SuppressWarnings("unchecked")
    public T maxFileSizeMB(long maxFileSizeMB) {
        if (maxFileSize < 10) {
            throw new IllegalArgumentException("Invalid maxFileSizeMB");
        }
        this.maxFileSize = maxFileSizeMB * 1024 * 1024;
        return (T)this;
    }

    @SuppressWarnings("unchecked")
    public T maxBackupIndex(int maxBackupIndex) {
        if (maxBackupIndex < 1) {
            throw new IllegalArgumentException("");
        }
        this.maxBackupIndex = maxBackupIndex;
        return (T)this;
    }

    @SuppressWarnings("unchecked")
    public T entryDelimiter(char entryDelimiter) {
        this.entryDelimiter = entryDelimiter;
        return (T)this;
    }

    String getLoggerName() {
        return loggerName;
    }
}
