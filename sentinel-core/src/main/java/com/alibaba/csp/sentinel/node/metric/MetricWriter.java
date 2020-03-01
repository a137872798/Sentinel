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
package com.alibaba.csp.sentinel.node.metric;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.alibaba.csp.sentinel.log.LogBase;
import com.alibaba.csp.sentinel.util.PidUtil;
import com.alibaba.csp.sentinel.config.SentinelConfig;
import com.alibaba.csp.sentinel.log.RecordLog;

/**
 * This class is responsible for writing {@link MetricNode} to disk:
 * <ol>
 * <li>metric with the same second should write to the same file;</li>
 * <li>single file size must be controlled;</li>
 * <li>file name is like: {@code ${appName}-metrics.log.pid${pid}.yyyy-MM-dd.[number]}</li>
 * <li>metric of different day should in different file;</li>
 * <li>every metric file is accompanied with an index file, which file name is {@code ${metricFileName}.idx}</li>
 * </ol>
 *
 * @author leyou
 * 该对象用于将 MetricNode 写入到文件中
 */
public class MetricWriter {

    private static final String CHARSET = SentinelConfig.charset();
    public static final String METRIC_BASE_DIR = RecordLog.getLogBaseDir();
    /**
     * Note: {@link MetricFileNameComparator}'s implementation relays on the metric file name,
     * we should be careful when changing the metric file name.
     *
     * @see #formMetricFileName(String, int)
     */
    public static final String METRIC_FILE = "metrics.log";
    public static final String METRIC_FILE_INDEX_SUFFIX = ".idx";
    public static final Comparator<String> METRIC_FILE_NAME_CMP = new MetricFileNameComparator();

    private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    /**
     * 排除时差干扰
     */
    private long timeSecondBase;
    private String baseDir;
    private String baseFileName;
    /**
     * file must exist when writing
     */
    private File curMetricFile;
    private File curMetricIndexFile;

    // 对应 curMetricFile 的输出流
    private FileOutputStream outMetric;
    private DataOutputStream outIndex;
    private BufferedOutputStream outMetricBuf;
    private long singleFileSize;
    /**
     * 以天为单位  一天最多保存多少文件
     */
    private int totalFileCount;
    private boolean append = false;
    private final int pid = PidUtil.getPid();

    /**
     * 秒级统计，忽略毫秒数。
     */
    private long lastSecond = -1;

    public MetricWriter(long singleFileSize) {
        this(singleFileSize, 6);
    }

    /**
     * 通过每个文件的长度 以及总文件数进行初始化
     * @param singleFileSize
     * @param totalFileCount
     */
    public MetricWriter(long singleFileSize, int totalFileCount) {
        if (singleFileSize <= 0 || totalFileCount <= 0) {
            throw new IllegalArgumentException();
        }
        RecordLog.info(
            "[MetricWriter] Creating new MetricWriter, singleFileSize=" + singleFileSize + ", totalFileCount="
                + totalFileCount);
        this.baseDir = METRIC_BASE_DIR;
        // 创建统计文件夹
        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        long time = System.currentTimeMillis();
        this.lastSecond = time / 1000;
        this.singleFileSize = singleFileSize;
        this.totalFileCount = totalFileCount;
        try {
            this.timeSecondBase = df.parse("1970-01-01 00:00:00").getTime() / 1000;
        } catch (Exception e) {
            RecordLog.warn("[MetricWriter] Create new MetricWriter error", e);
        }
    }

    /**
     * 如果传入了time，就认为nodes中所有的时间时间戳都是time.
     *
     * @param time
     * @param nodes
     */
    public synchronized void write(long time, List<MetricNode> nodes) throws Exception {
        if (nodes == null) {
            return;
        }
        // 为所有节点设置写入的时间戳
        for (MetricNode node : nodes) {
            node.setTimestamp(time);
        }

        String appName = SentinelConfig.getAppName();
        if (appName == null) {
            appName = "";
        }
        // first write, should create file
        // 文件还没创建时 使用特殊前缀生成文件名
        if (curMetricFile == null) {
            baseFileName = formMetricFileName(appName, pid);
            // 在文件名上增加时间信息
            closeAndNewFile(nextFileNameOfDay(time));
        }
        if (!(curMetricFile.exists() && curMetricIndexFile.exists())) {
            closeAndNewFile(nextFileNameOfDay(time));
        }

        long second = time / 1000;
        if (second < lastSecond) {
            // 时间靠前的直接忽略，不应该发生。
        } else if (second == lastSecond) {
            // 代表写入的新节点 与之前的时间相同
            for (MetricNode node : nodes) {
                outMetricBuf.write(node.toFatString().getBytes(CHARSET));
            }
            outMetricBuf.flush();
            // 如果此时 outMetricBuf的数据长度 超过了单个文件允许的最大长度  则打开新的文件  那么本次即使超过了 maxSize 也是允许的
            // 先写入 而不是先创建新文件再写入
            if (!validSize()) {
                closeAndNewFile(nextFileNameOfDay(time));
            }
        } else {
            // 代表在不同的时间点 将当前时间以及对应的 偏移量写入 index文件    看来这些涉及到存储数据的框架都有一个套路 也就是index文件
            // 因为一个个文件的扫描做了很多无用功 那么直接创建一个索引文件 只根据时间 以及核心索引字段(比如偏移量) 快速定位文件就好
            writeIndex(second, outMetric.getChannel().position());
            // 判断是否已经到了新的一天 如果到了就 开启新文件
            if (isNewDay(lastSecond, second)) {
                closeAndNewFile(nextFileNameOfDay(time));
                for (MetricNode node : nodes) {
                    outMetricBuf.write(node.toFatString().getBytes(CHARSET));
                }
                outMetricBuf.flush();
                if (!validSize()) {
                    closeAndNewFile(nextFileNameOfDay(time));
                }
            } else {
                for (MetricNode node : nodes) {
                    outMetricBuf.write(node.toFatString().getBytes(CHARSET));
                }
                outMetricBuf.flush();
                if (!validSize()) {
                    closeAndNewFile(nextFileNameOfDay(time));
                }
            }
            lastSecond = second;
        }
    }

    public synchronized void close() throws Exception {
        if (outMetricBuf != null) {
            outMetricBuf.close();
        }
        if (outIndex != null) {
            outIndex.close();
        }
    }

    private void writeIndex(long time, long offset) throws Exception {
        outIndex.writeLong(time);
        outIndex.writeLong(offset);
        outIndex.flush();
    }

    /**
     * 根据当前时间 找到对应目录 并返回一个新的文件名
     * @param time
     * @return
     */
    private String nextFileNameOfDay(long time) {
        List<String> list = new ArrayList<String>();
        File baseFile = new File(baseDir);
        DateFormat fileNameDf = new SimpleDateFormat("yyyy-MM-dd");
        String dateStr = fileNameDf.format(new Date(time));
        String fileNameModel = baseFileName + "." + dateStr;
        for (File file : baseFile.listFiles()) {
            String fileName = file.getName();
            if (fileName.contains(fileNameModel)
                && !fileName.endsWith(METRIC_FILE_INDEX_SUFFIX)
                && !fileName.endsWith(".lck")) {
                list.add(file.getAbsolutePath());
            }
        }
        Collections.sort(list, METRIC_FILE_NAME_CMP);
        if (list.isEmpty()) {
            return baseDir + fileNameModel;
        }
        String last = list.get(list.size() - 1);
        int n = 0;
        String[] strs = last.split("\\.");
        if (strs.length > 0 && strs[strs.length - 1].matches("[0-9]{1,10}")) {
            n = Integer.parseInt(strs[strs.length - 1]);
        }
        return baseDir + fileNameModel + "." + (n + 1);
    }

    /**
     * A comparator for metric file name. Metric file name is like: <br/>
     * <pre>
     * metrics.log.2018-03-06
     * metrics.log.2018-03-07
     * metrics.log.2018-03-07.10
     * metrics.log.2018-03-06.100
     * </pre>
     * <p>
     * File name with the early date is smaller, if date is same, the one with the small file number is smaller.
     * Note that if the name is an absolute path, only the fileName({@link File#getName()}) part will be considered.
     * So the above file names should be sorted as: <br/>
     * <pre>
     * metrics.log.2018-03-06
     * metrics.log.2018-03-06.100
     * metrics.log.2018-03-07
     * metrics.log.2018-03-07.10
     *
     * </pre>
     * </p>
     */
    private static final class MetricFileNameComparator implements Comparator<String> {
        private final String pid = "pid";

        @Override
        public int compare(String o1, String o2) {
            String name1 = new File(o1).getName();
            String name2 = new File(o2).getName();
            String dateStr1 = name1.split("\\.")[2];
            String dateStr2 = name2.split("\\.")[2];
            // in case of file name contains pid, skip it, like Sentinel-Admin-metrics.log.pid22568.2018-12-24
            if (dateStr1.startsWith(pid)) {
                dateStr1 = name1.split("\\.")[3];
                dateStr2 = name2.split("\\.")[3];
            }

            // compare date first
            int t = dateStr1.compareTo(dateStr2);
            if (t != 0) {
                return t;
            }

            // same date, compare file number
            t = name1.length() - name2.length();
            if (t != 0) {
                return t;
            }
            return name1.compareTo(name2);
        }
    }

    /**
     * Get all metric files' name in {@code baseDir}. The file name must like
     * <pre>
     * baseFileName + ".yyyy-MM-dd.number"
     * </pre>
     * and not endsWith {@link #METRIC_FILE_INDEX_SUFFIX} or ".lck".
     *
     * @param baseDir      the directory to search.
     * @param baseFileName the file name pattern.
     * @return the metric files' absolute path({@link File#getAbsolutePath()})
     * @throws Exception
     * 找到base目录下的所有文件名
     */
    static List<String> listMetricFiles(String baseDir, String baseFileName) throws Exception {
        List<String> list = new ArrayList<String>();
        File baseFile = new File(baseDir);
        File[] files = baseFile.listFiles();
        if (files == null) {
            return list;
        }
        for (File file : files) {
            String fileName = file.getName();
            if (file.isFile()
                && fileNameMatches(fileName, baseFileName)
                // 这里排除掉后缀为 index  和 lck 的文件
                && !fileName.endsWith(MetricWriter.METRIC_FILE_INDEX_SUFFIX)
                && !fileName.endsWith(".lck")) {
                list.add(file.getAbsolutePath());
            }
        }
        Collections.sort(list, MetricWriter.METRIC_FILE_NAME_CMP);
        return list;
    }

    /**
     * Test whether fileName matches baseFileName. fileName matches baseFileName when
     * <pre>
     * fileName = baseFileName + ".yyyy-MM-dd.number"
     * </pre>
     *
     * @param fileName     file name
     * @param baseFileName base file name.
     * @return if fileName matches baseFileName return true, else return false.
     */
    public static boolean fileNameMatches(String fileName, String baseFileName) {
        if (fileName.startsWith(baseFileName)) {
            String part = fileName.substring(baseFileName.length());
            // part is like: ".yyyy-MM-dd.number", eg. ".2018-12-24.11"
            return part.matches("\\.[0-9]{4}-[0-9]{2}-[0-9]{2}(\\.[0-9]*)?");
        } else {
            return false;
        }
    }

    /**
     * 删除多余的文件
     * @throws Exception
     */
    private void removeMoreFiles() throws Exception {
        List<String> list = listMetricFiles(baseDir, baseFileName);
        if (list == null || list.isEmpty()) {
            return;
        }
        // 也就是当list 的长度超过了 totalFileCount 时 要删除多余的文件
        for (int i = 0; i < list.size() - totalFileCount + 1; i++) {
            String fileName = list.get(i);
            // 找到对应的 index 文件名
            String indexFile = formIndexFileName(fileName);
            new File(fileName).delete();
            RecordLog.info("[MetricWriter] Removing metric file: " + fileName);
            new File(indexFile).delete();
            RecordLog.info("[MetricWriter] Removing metric index file: " + indexFile);
        }
    }

    /**
     * 删除多余的文件 同时按照指定的fileName 生成新文件
     * @param fileName
     * @throws Exception
     */
    private void closeAndNewFile(String fileName) throws Exception {
        removeMoreFiles();
        // 这里要更换输出流
        if (outMetricBuf != null) {
            outMetricBuf.close();
        }
        if (outIndex != null) {
            outIndex.close();
        }
        outMetric = new FileOutputStream(fileName, append);
        outMetricBuf = new BufferedOutputStream(outMetric);
        // 更换当前指向的 file
        curMetricFile = new File(fileName);
        String idxFile = formIndexFileName(fileName);
        curMetricIndexFile = new File(idxFile);
        outIndex = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(idxFile, append)));
        RecordLog.info("[MetricWriter] New metric file created: " + fileName);
        RecordLog.info("[MetricWriter] New metric index file created: " + idxFile);
    }

    private boolean validSize() throws Exception {
        long size = outMetric.getChannel().size();
        return size < singleFileSize;
    }

    private boolean isNewDay(long lastSecond, long second) {
        long lastDay = (lastSecond - timeSecondBase) / 86400;
        long newDay = (second - timeSecondBase) / 86400;
        return newDay > lastDay;
    }

    /**
     * Form metric file name use the specific appName and pid. Note that only
     * form the file name, not include path.
     *
     * Note: {@link MetricFileNameComparator}'s implementation relays on the metric file name,
     * we should be careful when changing the metric file name.
     *
     * @param appName
     * @param pid
     * @return metric file name.
     * 生成 metric文件名
     */
    public static String formMetricFileName(String appName, int pid) {
        if (appName == null) {
            appName = "";
        }
        // dot is special char that should be replaced.
        final String dot = ".";
        final String separator = "-";
        if (appName.contains(dot)) {
            appName = appName.replace(dot, separator);
        }
        String name = appName + separator + METRIC_FILE;
        if (LogBase.isLogNameUsePid()) {
            name += ".pid" + pid;
        }
        return name;
    }

    /**
     * Form index file name of the {@code metricFileName}
     *
     * @param metricFileName
     * @return the index file name of the metricFileName
     */
    public static String formIndexFileName(String metricFileName) {
        return metricFileName + METRIC_FILE_INDEX_SUFFIX;
    }
}
