/*
 * The MIT License
 * Copyright © 2025 OpenUniverse
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.ou.process;

import java.io.OutputStream;
import java.lang.ProcessHandle.Info;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.solr.common.util.Hash;
import org.eclipse.jgit.api.Git;
import org.ou.common.constants.IConstants;
import org.ou.common.constants.IEventConst;
import org.ou.common.constants.IRecordConst;
import org.ou.common.utils.CommonUtils;
import org.ou.common.utils.JvmMetricsUtils;
import org.ou.common.utils.NtpUtils;
import org.ou.common.utils.SignatureUtils;
import org.ou.common.utils.TsaUtils;
import org.ou.to.AbstractTo;
import org.ou.to.ExportSettings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * <p>
 * LoggerUtils class.</p>
 *
 *
 * @since 1.0.21
 */
public class LoggerUtils extends Thread {

    private static final ObjectMapper om = new JsonMapper() //
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     *
     * @param prev_record_chain_hash
     * @param outputToConsole
     * @param exportConsoles
     * @param git
     * @param logPath
     * @param actionUuid
     * @param map
     * @param serialNo
     * @param nodeInfoMap
     * @param repoInfoMap
     * @param gitRevCommitMap
     * @param privateKey
     * @param signatureAlgorithm
     * @param hashAlgorithm
     * @return
     * @throws Exception
     */
    public static String printLoggerMap(String prev_record_chain_hash, boolean outputToConsole, Collection<ExportThread> exportConsoles, Collection<DmqThread> dlqThreads, Git git, Path logPath, String actionUuid, Map<String, Object> map, long serialNo, Map<String, Object> nodeInfoMap, Map<String, Object> repoInfoMap, Map<String, Object> gitRevCommitMap, String hashAlgorithm) throws Exception {
        Map<String, Object> eventMap = (Map) map.get(IConstants.EVENT_KEY);
        String eventClass = (String) eventMap.get(IEventConst.EVENT_SOURCE_CLASS_KEY);
        String eventType = (String) eventMap.get(IEventConst.EVENT_TYPE_KEY);

        Map<String, Object> recordMap = (Map) map.get(IConstants.RECORD_KEY);
        //String recordType = (String) recordMap.get(IRecordConst.RECORD_TYPE_KEY);
        //recordMap.put(IRecordConst.RECORD_TIMESTAMP_KEY, ZonedDateTime.now(ZoneOffset.UTC));
        recordMap.put(IRecordConst.RECORD_TIMESTAMP_KEY, ZonedDateTime.now(ZoneOffset.UTC));
        recordMap.put(IRecordConst.RECORD_SERIAL_NO_KEY, serialNo);

        AbstractTo eventTo = MainProcess.allEventsMap.get(eventType);
        if (eventTo != null) { // avoid special events
            eventMap.put("description", eventTo.headTo.description);
            eventMap.put("tags", eventTo.headTo.tags);
            eventMap.put("attr", eventTo.headTo.attr);
        }
        if (eventType.equals(IEventConst.EVENT_TYPE_VALUE_HEALTH_OU)) {
            Info info = MainProcess.mainProcessHandle.info();
            Optional<Duration> optionalDuration = info.totalCpuDuration();
            Optional<String> optionalCommandLine = info.commandLine();

            eventMap.put("logger_queue_size", MainProcess.loggerQueue.size());
            eventMap.put("triggers_queue_size", MainProcess.triggersQueue.size());
            eventMap.put("undelivered_records_count", MainProcess.healthUndeliveredRecordsCount);
            eventMap.put("undelivered_records_count_dmq", MainProcess.healthUndeliveredRecordsCountDMQ);
            eventMap.put("process_pid", MainProcess.mainProcessHandle.pid());
            eventMap.put("process_total_cpu_duration", optionalDuration.get().toString());
            eventMap.put("process_total_command_line", optionalCommandLine.get());
        } else if (eventType.equals(IEventConst.EVENT_TYPE_VALUE_HEALTH_JVM)) {
            eventMap.putAll(JvmMetricsUtils.createJvmMetricsMap());
        }

        map.put("node", nodeInfoMap); // {DOCUMENTATION}
        map.put("repo", repoInfoMap); // {DOCUMENTATION}
        map.put("commit", gitRevCommitMap); // {DOCUMENTATION}

        Map<String, Object> mapE = new LinkedHashMap<>(map.size() + 1);
        mapE.put(IRecordConst.RECORD_ID_KEY, UUID.randomUUID().toString());
        mapE.putAll(map);

        // flatten map
        Map<String, Object> target = new LinkedHashMap<>();
        CommonUtils.flattenMap("_", mapE, "", target);
        mapE = CommonUtils.suffixMapKeyNames(target);

        if (IEventConst.EVENT_SOURCE_CLASS_VALUE_CONTROL.equals(eventClass)) {
            Files.writeString(logPath, om.writeValueAsString(mapE) + '\n', StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
            //String logFileName = logPath.getFileName().toString();
            //GitUtils.commitFile(git, logFileName);
        }

        Map<String, Object> treeMap = new TreeMap<>(mapE);

        if (MainProcess.signatureSettings != null && MainProcess.signatureSettings.privateKey != null) {
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_SIGNATURE_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_SIGNATURE_ALG_KEY, null);
        }
        if (MainProcess.timestampSettings != null && !MainProcess.timestampSettings.tsaUrls.isEmpty()) {
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_TSA_URL_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ALG_OID_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ALG_NAME_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_NONCE_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_RESPONSE_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_SERIAL_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_TIMESTAMP_KEY, null);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ERROR_KEY, null);
        }

        treeMap.put(IRecordConst.OUT_RECORD_RECORD_FORMAT_HASH_KEY, null);

        Collection<String> keys = treeMap.keySet();

        int hash = getRecordFormatHash(keys);
        treeMap.put(IRecordConst.OUT_RECORD_RECORD_FORMAT_HASH_KEY, hash);

        byte[] bs = om.writeValueAsBytes(treeMap);
        String record_hash = CommonUtils.hashBytes(bs, hashAlgorithm);
        treeMap.put(IRecordConst.OUT_RECORD_HASH_KEY, record_hash);

        bs = (prev_record_chain_hash + om.writeValueAsString(treeMap)).getBytes(StandardCharsets.UTF_8);
        String record_chain_hash = CommonUtils.hashBytes(bs, hashAlgorithm);
        treeMap.put(IRecordConst.OUT_RECORD_CHAIN_HASH_KEY, record_chain_hash);

        if (MainProcess.signatureSettings != null && MainProcess.signatureSettings.privateKey != null) {
            String signature = SignatureUtils.sign(bs, MainProcess.signatureSettings.privateKey, MainProcess.signatureSettings.signatureAlgorithm);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_SIGNATURE_KEY, signature);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_SIGNATURE_ALG_KEY, MainProcess.signatureSettings.signatureAlgorithm);
        }

        if (MainProcess.timestampSettings != null && !MainProcess.timestampSettings.tsaUrls.isEmpty()) {
            BigInteger nonce = BigInteger.valueOf(System.currentTimeMillis());
            byte[] digestBs = MainProcess.timestampSettings.messageDigest.digest(bs);
            byte[] tsaRequestBs = TsaUtils.generateTsaRequest(digestBs, MainProcess.timestampSettings.algOid, nonce);

            for (String tsaUrl : MainProcess.timestampSettings.tsaUrls) {
                if (treeMap.get(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_TSA_URL_KEY) == null) {
                    try {
                        byte[] tsaResponseBs = TsaUtils.getTsaResponse(tsaUrl, tsaRequestBs);
                        Map<String, Object> timestampDataMap = TsaUtils.parseTsaResponse(tsaResponseBs);
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_TSA_URL_KEY, tsaUrl);
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ALG_OID_KEY, MainProcess.timestampSettings.algOidStr);
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ALG_NAME_KEY, MainProcess.timestampSettings.algName);
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_NONCE_KEY, CommonUtils.bytesToHex(nonce.toByteArray()));
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_RESPONSE_KEY, CommonUtils.bytesToHex(tsaResponseBs));
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_SERIAL_KEY, timestampDataMap.get("serial"));
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_TIMESTAMP_KEY, timestampDataMap.get("timestamp"));
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ERROR_KEY, null);
                        break;
                    } catch (Exception e) {
                        treeMap.put(IRecordConst.OUT_RECORD_RECORD_TIMESTAMP_ERROR_KEY, e.getMessage());
                        e.printStackTrace();                        
                    }
                }
            }
        }

        if (MainProcess.ntpSettings.ntpServer != null) {
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_NTP_SERVER_HOST_KEY, MainProcess.ntpSettings.ntpServer);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_NTP_SERVER_PORT_KEY, MainProcess.ntpSettings.ntpPort);
            String utcIsoTime = NtpUtils.getUtcIsoTime(MainProcess.ntpSettings.ntpClient, MainProcess.ntpSettings.ntpHostAddr, MainProcess.ntpSettings.ntpPort);
            treeMap.put(IRecordConst.OUT_RECORD_RECORD_NTP_TIMESTAMP_KEY, utcIsoTime);
        }

        if (outputToConsole) {
            OutputStream os = System.out;
            synchronized (os) {
                byte[] jsonBs = CommonUtils.om.writeValueAsBytes(treeMap);
                os.write(jsonBs);
                os.write((byte) '\n');
                os.flush();
            }
        }
        output(treeMap, exportConsoles, dlqThreads);
        return record_chain_hash;
    }

    private static int getRecordFormatHash(Collection<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            sb.append(key);
        }
        return Hash.murmurhash3_x86_32(sb, 0, sb.length(), 0);
    }

    private static Object buildJsonObj(Map<String, Object> treeMap, ExportSettings exportSettings) throws Exception {
        if (exportSettings.storeAsArray) {
            return new ArrayList(treeMap.values());
        }
        return treeMap;
    }

    private static void output(Map<String, Object> treeMap, Collection<ExportThread> exportConsoles, Collection<DmqThread> dmqThreads) throws Exception {
        if (exportConsoles == null) {
            return;
        }
        for (final ExportThread exportConsole : exportConsoles) {
            ExportSettings exportSettings = exportConsole.getExportSettings();
            Object jsonObj = buildJsonObj(treeMap, exportSettings);
            new Thread() {
                @Override
                public void run() {
                    try {
                        exportConsole.sendString(jsonObj);
                    } catch (Throwable t) {
                        try {
                            MainProcess.healthUndeliveredRecordsCount++;
                            Map<String, Object> undeliveredRecordMap = createUndeliveredRecordMap(jsonObj, exportSettings, t);
                            for (DmqThread dmqThread : dmqThreads) {
                                try {
                                    dmqThread.sendString(undeliveredRecordMap);
                                } catch (Throwable e) {
                                    MainProcess.healthUndeliveredRecordsCountDMQ++;
                                    MainProcess.appendToErrorLog(e, false);
                                }
                            }
                        } catch (Throwable e) {
                            MainProcess.appendToErrorLog(e, false);
                        }

                    }
                }
            }.start();
        }
    }

    private static Map<String, Object> createUndeliveredRecordMap(Object jsonObj, ExportSettings exportSettings, Throwable t) {
        Map<String, Object> map = new LinkedHashMap<>();
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        List<Map<String, Object>> stackTraceElementsList = new ArrayList(stackTraceElements.length);
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            Map<String, Object> stackTraceElementMap = new LinkedHashMap<>();
            stackTraceElementMap.put("file_name_s", stackTraceElement.getFileName());
            stackTraceElementMap.put("line_number_i", stackTraceElement.getLineNumber());
            stackTraceElementMap.put("method_name_s", stackTraceElement.getMethodName());
            stackTraceElementMap.put("class_name_s", stackTraceElement.getClassName());
            stackTraceElementMap.put("class_loader_name_s", stackTraceElement.getClassLoaderName());
            stackTraceElementMap.put("module_name_s", stackTraceElement.getModuleName());
            stackTraceElementMap.put("module_version_s", stackTraceElement.getModuleVersion());
            stackTraceElementsList.add(stackTraceElementMap);
        }
        map.put("export_target_id_s", exportSettings.id);
        map.put("message_s", t.getMessage());
        map.put("message_localized_s", t.getLocalizedMessage());
        map.put("stack_trace_as_string_s", CommonUtils.getStackTraceAsString(t));
        map.put("stack_trace_as_array_ao", stackTraceElementsList);
        map.put("undelivered_record_" + (jsonObj instanceof Map ? "o" : "ao"), jsonObj);
        return map;
    }

}
