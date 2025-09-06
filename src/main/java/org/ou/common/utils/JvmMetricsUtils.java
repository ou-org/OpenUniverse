package org.ou.common.utils;

import java.lang.management.*;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class JvmMetricsUtils {

    public static Map<String, Object> createJvmMetricsMap() {
        Map<String, Object> map = new LinkedHashMap<>();

        // JVM Runtime Metrics
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        map.put("jvm_uptime_ms", runtime.getUptime());
        map.put("jvm_name", runtime.getName());
        map.put("java_version", System.getProperty("java.version"));
        map.put("java_vendor", System.getProperty("java.vendor"));
        map.put("available_processors", Runtime.getRuntime().availableProcessors());

        // OS Metrics
        OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
        map.put("os_name", osMxBean.getName());
        map.put("os_version", osMxBean.getVersion());
        map.put("os_architecture", osMxBean.getArch());
        map.put("system_load_average", osMxBean.getSystemLoadAverage());

        // CPU Load (if available)
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
            map.put("cpu_process_load", osBean.getProcessCpuLoad());
            map.put("cpu_system_load", osBean.getCpuLoad());
        }

        // Memory Metrics
        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();

        map.put("memory_heap_used_mb", heap.getUsed() / (1024 * 1024));
        map.put("memory_heap_committed_mb", heap.getCommitted() / (1024 * 1024));
        map.put("memory_heap_max_mb", heap.getMax() / (1024 * 1024));
        map.put("memory_nonheap_used_mb", nonHeap.getUsed() / (1024 * 1024));

        // Memory Pools
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String keyPrefix = "memory_pool_" + pool.getName().toLowerCase().replace(" ", "_");
            map.put(keyPrefix + "_used_mb", pool.getUsage().getUsed() / (1024 * 1024));
            map.put(keyPrefix + "_committed_mb", pool.getUsage().getCommitted() / (1024 * 1024));
            map.put(keyPrefix + "_max_mb", pool.getUsage().getMax() / (1024 * 1024));
        }

        // Garbage Collector Metrics
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String keyPrefix = "gc_" + gcBean.getName().toLowerCase(Locale.ENGLISH).replace(" ", "_");
            map.put(keyPrefix + "_collections", gcBean.getCollectionCount());
            map.put(keyPrefix + "_collection_time_ms", gcBean.getCollectionTime());
        }

        // Thread Metrics
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        map.put("threads_count", threadMxBean.getThreadCount());
        map.put("threads_peak_count", threadMxBean.getPeakThreadCount());
        map.put("threads_daemon_count", threadMxBean.getDaemonThreadCount());
        map.put("threads_total_started", threadMxBean.getTotalStartedThreadCount());

        long[] deadlockedThreads = threadMxBean.findDeadlockedThreads();
        map.put("threads_deadlocked_count", (deadlockedThreads == null) ? 0 : deadlockedThreads.length);

        // Class Loading Metrics
        ClassLoadingMXBean classLoadingMxBean = ManagementFactory.getClassLoadingMXBean();
        map.put("classes_total_loaded", classLoadingMxBean.getTotalLoadedClassCount());
        map.put("classes_currently_loaded", classLoadingMxBean.getLoadedClassCount());
        map.put("classes_total_unloaded", classLoadingMxBean.getUnloadedClassCount());

        return map;
    }
}
