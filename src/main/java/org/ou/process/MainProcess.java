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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.ProcessHandle.Info;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.ou.common.constants.IConstants;
import org.ou.common.constants.IDocConst;
import org.ou.common.constants.IDocTypes;
import org.ou.common.constants.IEventConst;
import org.ou.common.constants.IMsg;
import org.ou.common.constants.IPropPrefixes;
import org.ou.common.constants.ISystemProperties;
import org.ou.common.cron.CronUtils;
import org.ou.common.utils.CommitSignInfo;
import org.ou.common.utils.CommitSignatureUtils;
import org.ou.common.utils.CommonUtils;
import org.ou.common.utils.CurrentDateTimeUtils;
import org.ou.common.utils.EncryptedPlaceholderUtils;
import org.ou.common.utils.FileUtils;
import org.ou.common.utils.GitUtils;
import org.ou.common.utils.JarUtils;
import org.ou.common.utils.MdUtils;
import org.ou.common.utils.PasswordDialogUtils;
import org.ou.common.utils.RunCommandUtils;
import org.ou.common.utils.SignatureUtils;
import org.ou.common.utils.SolrUtils;
import org.ou.common.utils.TemplateUtils;
import org.ou.common.utils.TerminalUtils;
import org.ou.common.utils.UnixUtils;
import org.ou.indexer.DirectoryTreeProcessor;
import org.ou.indexer.DocKeyUtils;
import org.ou.main.Main;
import org.ou.to.AbstractTo;
import org.ou.to.CommandTo;
import org.ou.to.EventDocTo;
import org.ou.to.EventPublisherDocTo;
import org.ou.to.ExportSettings;
import org.ou.to.JobDocTo;
import org.ou.to.ProcessorDocTo;
import org.ou.to.RootDocTo;
import org.ou.to.SystemDocTo;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

/**
 * <p>
 * MainProcess class.</p>
 *
 *
 * @since 1.0.21
 */
public class MainProcess {

    /*
| File/Directory    | Type | Description |
|-----------------  |------|-------------|
| `.git`            | Dir  | Contains all Git-related metadata. |
| `.gitignore`      | File | Specifies untracked files that should be ignored by Git. |
| `.ouignore`       | File | Specifies unscanned files that should be ignored by OpenUniverse. |
| `write-lock.json` | File | Used to prevent concurrent operations on the repository. |
| `schema.json`     | File | Defines a discovered data structure for the repository. |
| `commit.txt`      | File | Contains the latest commit hash. |
| `node-id.txt`     | File | Stores the node ID, used for distributed systems tracking. |
| `log.ndjson`      | File | Log file in newline-delimited JSON format, used for structured logging. |
| `error-log.txt`   | File | Stores logs of errors encountered. |    
     */
    public static Path repoPath;
    /**
     * Constant <code>tmpRepoDir</code>
     */
    public static final String PROP_PREFIX_SEPARATOR = ":";

    public static Path tmpRepoDir;
    public static Path errorLogPath;
    public static volatile int healthUndeliveredRecordsCount = 0;
    public static volatile int healthUndeliveredRecordsCountDMQ = 0;
    public static final ProcessHandle mainProcessHandle = ProcessHandle.current();

    private static final ZonedDateTime STARTED_AT = ZonedDateTime.now(ZoneOffset.UTC);

    /**
     * Constant <code>keepRunning=true</code>
     */
    public static volatile boolean keepRunning = true;

    /**
     * Constant <code>pauseEventReceived=false</code>
     */
    public static volatile boolean pauseEventReceived = false;
    /**
     * Constant <code>resumeEventReceived=false</code>
     */
    public static volatile boolean resumeEventReceived = false;
    public static Map<String /* eventType */, AbstractTo> allEventsMap = new HashMap<>();

    private static final int QUEUE_CAPACITY = Integer.MAX_VALUE;
    public static final BlockingQueue<Map<String, Object>> loggerQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    public static final BlockingQueue<TriggerQueueEntry> triggersQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    public static final Collection<ExportThread> exportConsoles = new ArrayList<>(0);
    public static final Collection<DmqThread> dlqThreads = new ArrayList<>(0);

    private static Git git;
    private static boolean noColor;

    private static final Collection<Thread> threads = new ArrayList<>();
    private static Thread loggerThread;
    private static final Map<String /* id */, List<ProcessObj>> processMap = new HashMap<>();

    public static RootDocTo rootTo;

    private static final String TMP_SOLR_CONFIG_XML_RES = "solr_tmp/conf/solrconfig.xml";
    private static final String TMP_SOLR_SCHEMA_XML_RES = "solr_tmp/conf/schema.xml";

    private static final String STOP_COLOR = "\033[0m";
    private static final String COLOR_GRAY = "\033[2;37m";
    private static final String COLOR_WHITE_BOLD = "\033[1m";
    private static final String SEPARATOR_80 = "________________________________________________________________________________";
    //                                         "01234567890123456789012345678901234567890123456789012345678901234567890123456789"
    private static final String SEPARATOR_80_COLOR = COLOR_GRAY + SEPARATOR_80 + STOP_COLOR;

    private static final String OK = " OK";
    private static final String OK_COLOR = COLOR_WHITE_BOLD + OK + STOP_COLOR;

    private static final Collection<EventDocTo> allEventDocTos = new ArrayList<>();
    private static EncryptedPlaceholderUtils.IDecryptor decryptor;
    private static char[] secret;

    private static final String DIALOG_TITLE_SECRET = "OpenUniverse - Secret";
    private static final String DIALOG_LABEL_SECRET = "Enter secret:";

    /**
     * <p>
     * process.</p>
     *
     * @param repoDir
     * @param printSchema
     * @param printTargets
     * @param start
     * @param printStatus
     * @param stop
     * @param secret
     * @param stdinSec
     * @param assumeYes
     * @param noColor
     * @param outputToConsole
     * @throws java.lang.Exception if any.
     */
    public static void process( //
            String[] args, //
            String repoDir, //
            boolean printSchema, //
            boolean printTargets, //
            boolean printStatus, //
            boolean start, //
            boolean stop, //
            char[] secret, //
            boolean stdinSec, //
            boolean gui, //
            boolean assumeYes, //
            boolean noColor, //
            boolean outputToConsole //
    ) throws Exception {
        MainProcess.secret = secret;
        if (start) {
            if (stdinSec) {
                if (gui) {
                    secret = PasswordDialogUtils.askPassword(DIALOG_TITLE_SECRET, DIALOG_LABEL_SECRET);
                } else {
                    secret = TerminalUtils.readPassword(DIALOG_LABEL_SECRET);
                }
            }
            if (!assumeYes) {
                boolean yes = TerminalUtils.isYes("Start the process? y/N:");
                if (!yes) {
                    CommonUtils.exitWithMsg("Operation canceled.");
                    return;
                }
            }
        }

        String appDir = System.getenv("APPDIR"); // Run within AppImage
        boolean appImage = appDir != null;
        String jreDir = ISystemProperties.USER_DIR + "/jre";
        boolean jreDirExists = (Files.isDirectory(Path.of(jreDir)));

        MainProcess.noColor = noColor;

        repoPath = Paths.get(repoDir);

        Path jarSHA256reportPath = repoPath.resolve("jar-SHA256-report.txt");

        Path jarSignerReportPath = repoPath.resolve("jar-jarsigner-report.txt");

        // Path to the .git directory, which contains all Git-related metadata  
        Path dotGitPath = repoPath.resolve(".git");

        // Path to the .gitignore file, which specifies untracked files that should be ignored by Git  
        Path gitignorePath = repoPath.resolve(".gitignore");

        // Path to a lock file, used to prevent concurrent operations on the repository  
        Path lockedPath = repoPath.resolve("write-lock.json");

        // Path to the schema.json file, which defines a discovered data structure for the repository  
        Path schemaJsonPath = repoPath.resolve("schema.json");

        // Path to a file containing the latest commit hash  
        Path lattestCommitHashFilePath = repoPath.resolve("commit.txt");

        // Path to a file storing the node ID, used for distributed systems tracking  
        Path nodeIdFilePath = repoPath.resolve("node-id.txt");

        // Path to the log file in newline-delimited JSON format, used for structured logging  
        Path logPath = repoPath.resolve("log.ndjson");

        // Path to the error log file, storing logs of errors encountered  
        errorLogPath = repoPath.resolve("error-log.txt");
        //
        if (!Files.exists(repoPath)) {
            CommonUtils.exitWithMsg(IMsg.GIT_REPOSITORY_DIR_NOT_EXIST, repoPath);
            return;
        } else if (!Files.isDirectory(repoPath)) {
            CommonUtils.exitWithMsg(IMsg.GIT_REPOSITORY_DIR_NOT_DIR, repoPath);
            return;
        } else if (!Files.exists(dotGitPath) || !Files.isDirectory(dotGitPath)) {
            CommonUtils.exitWithMsg(IMsg.INVALID_GIT_REPOSITORY, repoPath);
            return;
        }

        // solr
        boolean lockedRepo = false;
        Map<String, Object> statusMap = new LinkedHashMap<>();
        if (Files.exists(lockedPath)) {
            String jsonStr = Files.readString(lockedPath).strip();
            if (!jsonStr.isEmpty()) {
                try {
                    statusMap = CommonUtils.om.readValue(jsonStr, Map.class);
                    Number pidObj = (Number) statusMap.get("pid");
                    if (pidObj != null) {
                        long pid = pidObj.longValue();
                        Optional<ProcessHandle> optionalProcessHandle = ProcessHandle.of(pid);
                        if (optionalProcessHandle.isPresent()) {
                            ProcessHandle processHandle = optionalProcessHandle.get();
                            if (processHandle.isAlive()) {
                                lockedRepo = true;
                                Info info = processHandle.info();
                                Optional<Duration> optTotalCpuDuration = info.totalCpuDuration();
                                Duration totalCpuDuration = optTotalCpuDuration.isPresent() ? optTotalCpuDuration.get() : null;
                                statusMap.put("total_сpu_duration", totalCpuDuration.toString());
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        if (stop) {
            Number pidObj = (Number) statusMap.get("pid");
            if (pidObj == null) {
                CommonUtils.exitWithMsg(IMsg.NOTHIG_TO_STOP);
                return;
            } else {
                long pid = pidObj.longValue();
                Optional<ProcessHandle> optProcessHandle = ProcessHandle.of(pid);
                if (optProcessHandle.isPresent()) {
                    ProcessHandle processHandle = optProcessHandle.get();
                    boolean ok = processHandle.destroy();
                    if (!ok) {
                        ok = processHandle.destroyForcibly();
                    }
                    if (ok) {
                        System.err.println(IMsg.INFO_PROCESS_STOPPED);
                        System.exit(0);
                        return;
                    } else {
                        CommonUtils.exitWithMsg(IMsg.CAN_NOT_STOP);
                        return;
                    }
                }
            }
            return;
        }

        git = Git.open(repoPath.toFile());
        Repository repository = git.getRepository();
        boolean cleanRepo = git.status().call().isClean();

        if (printStatus) {
            statusMap.put("clean_repo", cleanRepo);
            statusMap.put("locked_repo", lockedRepo);

            String statusJson = CommonUtils.omFormat.writeValueAsString(statusMap);
            System.out.println(statusJson);
            System.exit(0);
            return;
        }

        if (!cleanRepo) {
            CommonUtils.exitWithMsg(IMsg.GIT_REPOSITORY_IS_NOT_CLEAN);
            return;
        }

        if (lockedRepo) {
            CommonUtils.exitWithMsg(IMsg.REPOSITORY_IS_LOCKED, lockedPath);
            return;
        }

        if (CommitSignatureUtils.isRepoSigningCommits(repository)) {
            CommitSignInfo commitSignInfo = CommitSignatureUtils.getCommitSignInfo(repository, "HEAD");
            if (!commitSignInfo.issuerKeyIdTrusted) {
                CommonUtils.exitWithMsg(IMsg.COMMIT_SIGNATURE_VERIFICATION_FAILED);
                return;
            }

            if ( //
                    commitSignInfo.pgpSignature == null
                    || //
                    commitSignInfo.signedData == null
                    || //
                    commitSignInfo.issuerKeyIdHex == null
                    || //
                    !commitSignInfo.issuerKeyIdTrusted //
                    ) {
                CommonUtils.exitWithMsg(IMsg.COMMIT_SIGNATURE_VERIFICATION_FAILED);
                return;
            }
            byte[] keyring = CommitSignatureUtils.exportPublicKeys();
            boolean verified = CommitSignatureUtils.verifyCommitSignature(commitSignInfo, keyring);
            if (!verified) {
                CommonUtils.exitWithMsg(IMsg.COMMIT_SIGNATURE_VERIFICATION_FAILED);
                return;
            }
        }

        // BEGIN .gitignore
        //String gitignoreFileName = gitignorePath.getFileName().toString();
        String lockedFilePattern = lockedPath.getFileName().toString();
        String lattestCommitHashFilePathPattern = lattestCommitHashFilePath.getFileName().toString();

        if (!Files.exists(gitignorePath)) {
            Files.writeString(gitignorePath, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            //GitUtils.commitFile(git, gitignoreFileName);
        }

        List<String> gitignoreLines = Files.readAllLines(gitignorePath);
        boolean foundLocked = false;
        boolean foundLattestCommitHashFile = false;
        for (String line : gitignoreLines) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.equals(lockedFilePattern)) {
                foundLocked = true;
            }
            if (line.equals(lattestCommitHashFilePathPattern)) {
                foundLattestCommitHashFile = true;
            }
        }

        if (!foundLocked) {
            Files.writeString(gitignorePath, "\n" + lockedFilePattern, StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
            //GitUtils.commitFile(git, gitignoreFileName);
        }
        if (!foundLattestCommitHashFile) {
            Files.writeString(gitignorePath, "\n" + lattestCommitHashFilePathPattern, StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
            //GitUtils.commitFile(git, gitignoreFileName);
        }
        // END .gitignore

        if (!Files.exists(nodeIdFilePath)) {
            //String nodeIdFileName = nodeIdFilePath.getFileName().toString();
            Files.writeString(nodeIdFilePath, UUID.randomUUID().toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            //GitUtils.commitFile(git, nodeIdFileName);
        }
        String nodeId = Files.readString(nodeIdFilePath);

        tmpRepoDir = Files.createTempDirectory("repo-tmp-");
        final Path tmpSolrHomeDir = Files.createTempDirectory("solr-home-tmp-");

        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        Scheduler scheduler = schedulerFactory.getScheduler();

        Runtime.getRuntime()
                .addShutdownHook(new Thread(() -> {
                    if (printSchema || printTargets || printStatus) {
                        try {
                            git.close();
                            FileUtils.deleteDir(tmpSolrHomeDir);
                            FileUtils.deleteDir(tmpRepoDir);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            return;
                        }
                    }
                    System.err.println();
                    System.err.println("Starting shutdown process...");
                    System.err.println();
                    try {
                        // Stop cron scheduler                        
                        System.err.println("Stopping cron scheduler" + (noColor ? OK : OK_COLOR));
                        scheduler.shutdown(true);

                        // Send stop signal
                        System.err.println("Send stop signal" + (noColor ? OK : OK_COLOR));
                        Map<String, Object> mapStop = new LinkedHashMap<>();
                        mapStop.put(IEventConst.EVENT_SOURCE_CLASS_KEY, IEventConst.EVENT_SOURCE_CLASS_VALUE_CONTROL);
                        mapStop.put(IEventConst.EVENT_TYPE_KEY, IEventConst.EVENT_TYPE_VALUE_CONTROL_STOP);
                        TriggerUtils.processEvent(mapStop, rootTo, null, triggersQueue, loggerQueue);
                        keepRunning = false;

                        // Destroy triggers
                        System.err.println("Destroying all triggers" + (noColor ? OK : OK_COLOR));
                        for (Collection<ProcessObj> processObjs : processMap.values()) {
                            for (ProcessObj processObj : processObjs) {
                                if (processObj.stdinOutputStream == null && processObj.stdoutInputStream != null) {
                                    processObj.process.destroy();
                                }
                            }
                        }
                        // Destroy processors
                        System.err.println("Destroying all processors" + (noColor ? OK : OK_COLOR));
                        for (Collection<ProcessObj> processObjs : processMap.values()) {
                            for (ProcessObj processObj : processObjs) {
                                if (processObj.stdinOutputStream != null && processObj.stdoutInputStream != null) {
                                    processObj.process.destroy();
                                }
                            }
                        }

                        System.err.println("Shutting down workers" + (noColor ? OK : OK_COLOR));
                        for (Thread thread : threads) {
                            thread.interrupt();
                        }
                        System.err.println("Stopping all loggers" + (noColor ? OK : OK_COLOR));
                        if (loggerThread != null) {
                            //loggerThread.join();
                            loggerThread.interrupt();
                        }
                        for (ExportThread exportConsole : exportConsoles) {
                            exportConsole.close();
                        }
                        for (DmqThread dlqThread : dlqThreads) {
                            dlqThread.close();
                        }

                    } catch (Throwable t) {
                        System.err.println(t);
                        appendToErrorLog(t, false);
                    } finally {
                        try {
                            // //String errorLogFileName = errorLogPath.getFileName().toString();
                            // //GitUtils.commitFile(git, errorLogFileName);
                            // System.err.println("Waiting for last commit" + (noColor ? OK : OK_COLOR));
                            // while (!(GitUtils.isCommitted(git, errorLogPath) && GitUtils.isCommitted(git, logPath))) {
                            //     Thread.sleep(1000);
                            // }

                            System.err.println("Closing repository" + (noColor ? OK : OK_COLOR));
                            git.close();
                            System.err.println("Unlocking repository" + (noColor ? OK : OK_COLOR));
                            Files.deleteIfExists(lockedPath);
                            System.err.println("Deleting temporary files" + (noColor ? OK : OK_COLOR));
                            FileUtils.deleteDir(tmpSolrHomeDir);
                            FileUtils.deleteDir(tmpRepoDir);

                            GitUtils.runCommit(repoDir, "Pre-shutdown commit");

                            System.err.println();
                            System.err.println("*** Shutdown completed ***");
                            System.err.println();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
                ));

        Map<String, Object> repoInfoMap = new LinkedHashMap<>();

        repoInfoMap.put("name", repoPath.getFileName().toString());
        repoInfoMap.put("path", repoPath.toAbsolutePath().toString());
        repoInfoMap.put("description", repository.getGitwebDescription());

        RevCommit revCommit = GitUtils.copyRepoToDir(git, repository, tmpRepoDir);
        Map<String, Object> gitRevCommitMap = GitUtils.revCommitToMap(revCommit);

        Map<String, Object> nodeInfoMap = new LinkedHashMap<>();

        String username = ISystemProperties.USER_NAME;
        int uid = UnixUtils.getUid();
        Map<String, Integer> gidMap = UnixUtils.getGroups();
        Collection<String> groups = UnixUtils.getUserGroups(username);
        Collection<Integer> groupsIds = UnixUtils.getGroupIds();
        boolean standalone = ISystemProperties.OU_STANDALONE != null && ISystemProperties.OU_STANDALONE.equals("true");
        String stdoutRedirect = UnixUtils.getRedirectedStdoutTarget();

        nodeInfoMap.put("id", nodeId);
        nodeInfoMap.put("uid", uid);
        nodeInfoMap.put("gid", groupsIds);
        nodeInfoMap.put("groups", groups);
        nodeInfoMap.put("group", gidMap);
        nodeInfoMap.put("username", username);
        if (stdoutRedirect != null) {
            nodeInfoMap.put("stdout_redirect", stdoutRedirect);
        }
        nodeInfoMap.put("hostname", UnixUtils.getHostname());
        nodeInfoMap.put("ou_exec_wrapper_type_standalone", standalone);
        nodeInfoMap.put("ou_exec_wrapper_type_app_image", appImage);
        nodeInfoMap.put("ou_exec_wrapper_type_java", true);
        nodeInfoMap.put("java_version", ISystemProperties.JAVA_VERSION);
        nodeInfoMap.put("java_vendor", ISystemProperties.JAVA_VENDOR);
        nodeInfoMap.put("working_dir", ISystemProperties.USER_DIR);
        nodeInfoMap.put("os_name", ISystemProperties.OS_NAME);
        nodeInfoMap.put("os_version", ISystemProperties.OS_VERSION);
        nodeInfoMap.put("os_arch", ISystemProperties.OS_ARCH);
        nodeInfoMap.put("implementation_title", Main.MANIFEST_IMPLEMENTATION_TITLE);
        nodeInfoMap.put("implementation_version", Main.MANIFEST_IMPLEMENTATION_VERSION);
        nodeInfoMap.put("implementation_vendor", Main.MANIFEST_IMPLEMENTATION_VENDOR);
        nodeInfoMap.put("implementation_build_date_time", Main.MANIFEST_BUILD_TIME);

        Map<String, Object> defaultPropertiesMap = new HashMap<>();
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            defaultPropertiesMap.put((String) entry.getKey(), entry.getValue());
        }
        Map<String, Object> currentDateTimeInfo = CurrentDateTimeUtils.getCurrentDateTimeInfo();
        for (Map.Entry<String, Object> entry : currentDateTimeInfo.entrySet()) {
            defaultPropertiesMap.put((String) entry.getKey(), entry.getValue().toString());
        }

        defaultPropertiesMap.put("repo", repoDir);
        defaultPropertiesMap.put("tmpRepo", tmpRepoDir.toString());
        defaultPropertiesMap.put("current.timestamp", STARTED_AT.toString());

        defaultPropertiesMap.put("user", ISystemProperties.USER_NAME);
        defaultPropertiesMap.put("host", UnixUtils.getHostname());

        System.getenv().forEach((key, value) -> {
            if (key != null && value != null) {
                defaultPropertiesMap.put(IPropPrefixes.ENV + PROP_PREFIX_SEPARATOR + key, value);
            }
        });

        Properties systemProperties = System.getProperties();
        for (String key : systemProperties.stringPropertyNames()) {
            defaultPropertiesMap.put(IPropPrefixes.SYS + PROP_PREFIX_SEPARATOR + key, systemProperties.getProperty(key));
        }

        for (int i = 0; i < args.length; i++) {
            defaultPropertiesMap.put(IPropPrefixes.ARG + PROP_PREFIX_SEPARATOR + Integer.toString(i), args[i]);
        }

        //
        // process markdown files
        // ----------------------------------------------------------------------------------------------------------------------------------------------
        //
        MdUtils.processMarkdownDir(tmpRepoDir);

        final String coreName = "core";
        SolrUtils.createSolrHome(tmpSolrHomeDir, coreName, TMP_SOLR_CONFIG_XML_RES, TMP_SOLR_SCHEMA_XML_RES);
        try (SolrClient tmpSolrClient = SolrUtils.startSolrClient(tmpSolrHomeDir, coreName)) {
            Map<String /* doc key */, AbstractTo> allAbstractTos = DirectoryTreeProcessor.processDocs(tmpSolrClient, tmpRepoDir, defaultPropertiesMap);
            rootTo = (RootDocTo) allAbstractTos.get(DocKeyUtils.DOC_KEY_ROOT);
            if (rootTo == null) {
                CommonUtils.exitWithMsg("ERROR: Document type %s with name %s is not declared.", IDocTypes.DOC_TYPE_ROOT, IConstants.ROOT_DOC_NAME);
                return;
            }
            // String msg = isAllowedAbstractTo(rootTo);
            // if (msg != null) {
            //     CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, rootTo.headTo.docFile);
            //     return;
            // }

            rootTo.properties = TemplateUtils.overrideProperties(defaultPropertiesMap, rootTo.properties, null, null);

            if (rootTo.decryptCommandTo != null) {
                rootTo.decryptCommandTo.cmd = FileUtils.convertToRealPath(MainProcess.tmpRepoDir, rootTo.headTo.docFile, rootTo.decryptCommandTo.cmd).toString();
                //
                List<DecryptorInstance> pool = new ArrayList<>();
                for (ProcessObj p : CommandExecutorUtils.execute(rootTo.decryptCommandTo)) {
                    pool.add(new DecryptorInstance(p));
                }

                decryptor = new EncryptedPlaceholderUtils.IDecryptor() {
                    @Override
                    public String decrypt(String encrypted, char[] password) throws Exception {
                        return DecryptorUtils.decrypt(pool, encrypted, password);
                    }
                };
            }
            rootTo.properties = TemplateUtils.overrideProperties(rootTo.properties, rootTo.properties, decryptor, secret);
            rootTo.hashAlgorithm = (String) TemplateUtils.transform(rootTo.hashAlgorithm, rootTo.properties, decryptor, secret);

            String msg = isAllowedAbstractTo(rootTo);
            if (msg != null) {
                CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, rootTo.headTo.docFile);
                return;
            }

            Collection<AbstractTo> eventTosResults = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_EVENT, null);
            for (AbstractTo abstractTo : eventTosResults) {
                msg = isAllowedAbstractTo(abstractTo);
                if (msg != null) {
                    CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, abstractTo.headTo.docFile);
                    return;
                }
                abstractTo.properties = TemplateUtils.overrideProperties(rootTo.properties, abstractTo.properties, decryptor, secret);
                allEventDocTos.add((EventDocTo) abstractTo);
            }

            for (AbstractTo eventTo : allEventDocTos) {
                allEventsMap.put(eventTo.headTo.name, eventTo);
            }

            if (rootTo.triggers != null) {
                rootTo.eventPublishersTos = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_TRIGGERS_EVENT_PUBLISHER, rootTo.triggers);

                for (AbstractTo abstractTo : rootTo.eventPublishersTos) {
                    msg = isAllowedAbstractTo(abstractTo);
                    if (msg != null) {
                        CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, abstractTo.headTo.docFile);
                        return;
                    }
                    abstractTo.properties = TemplateUtils.overrideProperties(rootTo.properties, abstractTo.properties, decryptor, secret);
                }
                rootTo.calendarTos = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_TRIGGERS_EVENT_CALENDAR, rootTo.triggers);
                for (AbstractTo abstractTo : rootTo.calendarTos) {
                    msg = isAllowedAbstractTo(abstractTo);
                    if (msg != null) {
                        CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, abstractTo.headTo.docFile);
                        return;
                    }
                    abstractTo.properties = TemplateUtils.overrideProperties(rootTo.properties, abstractTo.properties, decryptor, secret);
                }
                rootTo.schedulerTos = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_TRIGGERS_EVENT_SCHEDULER, rootTo.triggers);
                for (AbstractTo abstractTo : rootTo.schedulerTos) {
                    msg = isAllowedAbstractTo(abstractTo);
                    if (msg != null) {
                        CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, abstractTo.headTo.docFile);
                        return;
                    }
                    abstractTo.properties = TemplateUtils.overrideProperties(rootTo.properties, abstractTo.properties, decryptor, secret);
                }

            }
            if (rootTo.jobs != null) {
                Collection<AbstractTo> jobTosResults = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_JOB, rootTo.jobs);
                for (AbstractTo abstractTo : jobTosResults) {
                    msg = isAllowedAbstractTo(abstractTo);
                    if (msg != null) {
                        CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, abstractTo.headTo.docFile);
                        return;
                    }
                    JobDocTo jobTo = (JobDocTo) abstractTo;

                    jobTo.properties = TemplateUtils.overrideProperties(rootTo.properties, jobTo.properties, decryptor, secret);
                    if (jobTo.processors != null) {
                        Collection<AbstractTo> actionTosResults = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_EVENT_PROCESSOR, jobTo.processors);
                        for (AbstractTo processorTo : actionTosResults) {
                            msg = isAllowedAbstractTo(processorTo);
                            if (msg != null) {
                                CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, processorTo.headTo.docFile);
                                return;
                            }

                            processorTo.properties = TemplateUtils.overrideProperties(jobTo.properties, processorTo.properties, decryptor, secret);
                            jobTo.processorTos.add((ProcessorDocTo) processorTo);
                        }
                    }
                    if (jobTo.systems != null) {
                        Collection<AbstractTo> systemTosResults = queryDocs(tmpSolrClient, allAbstractTos, IDocTypes.DOC_TYPE_SYSTEM, jobTo.systems);
                        for (AbstractTo systemTo : systemTosResults) {
                            msg = isAllowedAbstractTo(systemTo);
                            if (msg != null) {
                                CommonUtils.exitWithMsg(IMsg.CONSTRAINT_VIOLATION, msg, systemTo.headTo.docFile);
                                return;
                            }

                            systemTo.properties = TemplateUtils.overrideProperties(jobTo.properties, systemTo.properties, decryptor, secret);
                            ((SystemDocTo) systemTo).systemDefJson = TemplateUtils.transform(((SystemDocTo) systemTo).systemDefJson, systemTo.properties, decryptor, secret);

                            jobTo.systemTos.add((SystemDocTo) systemTo);
                        }
                    }
                    rootTo.jobTos.add((JobDocTo) jobTo);
                }
            }
            Map<String, Object> schemaMap = generateSchemaDataMap(rootTo);
            String schemaJson = CommonUtils.omFormat.writeValueAsString(schemaMap);
            if (printSchema) {
                System.out.println(schemaJson);
            } else if (printTargets) {
                Collection<String> exportTargetsIds = new ArrayList<>();
                for (ExportSettings exportTarget : rootTo.exportTargets) {
                    exportTargetsIds.add(exportTarget.id);
                }
                String targetsJson = CommonUtils.omFormat.writeValueAsString(exportTargetsIds);
                System.out.println(targetsJson);
            }

            if (!start) {
                System.exit(0);
                return;
            }

            final String SEPARATOR_80_WITH_COLOR_SUPPORT = noColor ? SEPARATOR_80 : SEPARATOR_80_COLOR;
            System.err.println();
            System.err.println(SEPARATOR_80_WITH_COLOR_SUPPORT);
            try (InputStream is = MainProcess.class.getResourceAsStream("/logo/logo.txt")) {
                System.err.print(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }

            System.err.println();
            System.err.println();
            System.err.println(Main.createHeader());
            System.err.println(SEPARATOR_80_WITH_COLOR_SUPPORT);
            System.err.println();

            Path jarPath = JarUtils.getSelfJar();
            if (jarPath != null) {
                String jarSHA256Report = JarUtils.createJarSHA256Report(jarPath);
                Files.writeString(jarSHA256reportPath, jarSHA256Report, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);

                String jarsignerExecutable = jreDirExists ? jreDir + "/bin/jarsigner" : "jarsigner";
                String jarSignerReport = JarUtils.createJarSignerReport(jarPath, jarsignerExecutable);
                Files.writeString(jarSignerReportPath, jarSignerReport, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);

                if (jarSignerReport.contains("jar verified.")) {
                    System.err.println("INFO: The OpenUniverse JAR file signature is verified.");
                } else {
                    CommonUtils.exitWithMsg(IMsg.JAR_SIGNATURE_VERIFICATION_FAILED, repoPath);
                    return;
                }
            }

            GitUtils.runCommit(repoDir, "Pre-start commit");

            scheduler.start();

            startToErrorLog();

            //
            // Get untracked commit history
            //
            long lastCommitTime;
            if (Files.exists(lattestCommitHashFilePath)) {
                lastCommitTime = Long.parseLong(Files.readString(lattestCommitHashFilePath).strip());
            } else {
                lastCommitTime = 0;
            }
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                // if (commit.getShortMessage().startsWith(GitUtils.INTERNAL_COMMIT_MSG_PREFIX)) {
                //     continue;
                // }
                if (commit.getCommitterIdent().getWhenAsInstant().toEpochMilli() < lastCommitTime) {
                    break;
                }
                Map<String, Object> mapCommit = GitUtils.revCommitToMap(commit);
                mapCommit.put(IEventConst.EVENT_SOURCE_CLASS_KEY, IEventConst.EVENT_SOURCE_CLASS_VALUE_REPO);
                mapCommit.put(IEventConst.EVENT_TYPE_KEY, IEventConst.EVENT_TYPE_VALUE_REPO_COMMIT);
                TriggerUtils.processEvent(mapCommit, rootTo, null, triggersQueue, loggerQueue);
            }
            Files.writeString(lattestCommitHashFilePath, Long.toString(System.currentTimeMillis()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                lockedPath.toFile().deleteOnExit();
                Map<String, Object> savedStatusMap = new LinkedHashMap<>();
                Info info = mainProcessHandle.info();

                Optional<Instant> optStartInstant = info.startInstant();
                Instant startInstant = optStartInstant.isPresent() ? optStartInstant.get() : null;

                Optional<String> optUser = info.user();
                String user = optUser.isPresent() ? optUser.get() : null;

                Optional<ProcessHandle> optParent = mainProcessHandle.parent();
                ProcessHandle parent = optParent.isPresent() ? optParent.get() : null;

                savedStatusMap.put("pid", mainProcessHandle.pid());
                savedStatusMap.put("parent_pid", parent.pid());
                savedStatusMap.put("user", user);
                savedStatusMap.put("since", startInstant.toString());

                String statusJson = CommonUtils.omFormat.writeValueAsString(savedStatusMap);
                Files.writeString(lockedPath, statusJson, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);

                Files.writeString(schemaJsonPath, schemaJson, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                //String schemaJsonFileName = schemaJsonPath.getFileName().toString();
                //GitUtils.commitFile(git, schemaJsonFileName);

                // *** Print stat START *** //
                System.err.println("INFO: Started at: %s".formatted(STARTED_AT.toString()));
                Optional<String> optCommandLine = mainProcessHandle.info().commandLine();
                String commandLine = optCommandLine.isPresent() ? optCommandLine.get() : null;
                if (commandLine != null) {
                    System.err.println("INFO: Command line: %s".formatted(commandLine));
                }
                System.err.println("INFO: JVM version: %s".formatted(ISystemProperties.JAVA_VERSION));

                System.err.println("INFO: Process ID (PID): %d".formatted(mainProcessHandle.pid()));

                if (System.console() == null) {
                    final String MSG_1 = "INFO: Standard output stream is redirected";
                    final String MSG_2 = "INFO: Standard output stream is redirected to %s";
                    if (stdoutRedirect == null) {
                        System.err.println(MSG_1);
                    } else {
                        System.err.println(MSG_2.formatted(stdoutRedirect));
                    }
                }

                int systemsCount = 0;
                int processorsCount = 0;
                for (JobDocTo jobTo : rootTo.jobTos) {
                    if (jobTo.systems != null) {
                        systemsCount += jobTo.systemTos.size();
                    }
                    if (jobTo.processors != null) {
                        processorsCount += jobTo.processorTos.size();
                    }
                }

                final String FMT_COUNT = "%6s";
                System.err.println("INFO: Documents:");
                System.err.println("┌──────────────────────────┬" + "───────┐");
                System.err.println("│ Document type            │" + " Count │");
                System.err.println("├──────────────────────────┼" + "───────┤");
                System.err.println("│ root                     │" + String.format(FMT_COUNT, 1) + " │");
                System.err.println("│ job                      │" + String.format(FMT_COUNT, rootTo.jobTos.size()) + " │");
                System.err.println("│ event                    │" + String.format(FMT_COUNT, allEventDocTos.size()) + " │");
                System.err.println("│ system                   │" + String.format(FMT_COUNT, systemsCount) + " │");
                System.err.println("│ processor                │" + String.format(FMT_COUNT, processorsCount) + " │");
                System.err.println("│ triggers/event_publisher │" + String.format(FMT_COUNT, rootTo.eventPublishersTos.size()) + " │");
                System.err.println("│ triggers/event_calendar  │" + String.format(FMT_COUNT, rootTo.calendarTos.size()) + " │");
                System.err.println("│ triggers/event_scheduler │" + String.format(FMT_COUNT, rootTo.schedulerTos.size()) + " │");
                System.err.println("└──────────────────────────┴" + "───────┘");
                System.err.println();
                // *** Print stat END *** //

                String signatureAlgorithm = null;
                PrivateKey privateKey = null;
                Map<String, Object> signSettingsMap = rootTo.signSettingsMap;
                if (signSettingsMap != null) {
                    signSettingsMap = (Map<String, Object>) TemplateUtils.transform(signSettingsMap, defaultPropertiesMap, decryptor, secret);
                    //TemplateUtils.decryptProperties(signSettingsMap, decryptCommand, secret);
                    //TemplateUtils.decryptProperties(pool, signSettingsMap, secret);

                    String keyStoreFile = (String) signSettingsMap.get("sign_key_store_file");
                    keyStoreFile = FileUtils.convertToRealPath(MainProcess.repoPath, rootTo.headTo.docFile, keyStoreFile).toString();

                    String keyStoreType = (String) signSettingsMap.get("sign_key_store_type");
                    if (keyStoreType == null || keyStoreType.isBlank()) {
                        keyStoreType = "PKCS12"; // default
                    }
                    String keyStorePassword = (String) signSettingsMap.get("sign_key_store_password");
                    if (keyStorePassword == null || keyStorePassword.isBlank()) {
                        keyStorePassword = ""; // default
                    }
                    String keyAlias = (String) signSettingsMap.get("sign_key_alias");
                    if (keyAlias == null || keyAlias.isBlank()) {
                        keyAlias = "sign"; // default
                    }
                    String keyPassword = (String) signSettingsMap.get("sign_key_password");
                    if (keyPassword == null || keyPassword.isBlank()) {
                        keyPassword = ""; // default
                    }
                    signatureAlgorithm = (String) signSettingsMap.get("sign_signature_algorithm");
                    if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) {
                        signatureAlgorithm = "SHA256withRSA"; // default
                    }
                    privateKey = SignatureUtils.getPrivateKey(keyStoreFile, keyStoreType, keyStorePassword, keyAlias, keyPassword);
                }
                String hashAlgorithm = rootTo.hashAlgorithm;

                //
                // DMQ
                //
                final String PREPARE_DMQ_MSG = "Prepare DMQ #%d" + (noColor ? OK : OK_COLOR);
                int n = 0;
                for (CommandTo dlqCommandTo : rootTo.exportDmqCommandTos) {
                    System.err.println(PREPARE_DMQ_MSG.formatted(n++));
                    DmqThread dlqThread = new DmqThread(dlqCommandTo);
                    dlqThreads.add(dlqThread);
                    dlqThread.start();
                }

                //
                // export
                //
                final String PREPARE_EXPORT_TARGET_MSG = "Prepare export target id=\"%s\"" + (noColor ? OK : OK_COLOR);
                for (ExportSettings exportSettings : rootTo.exportTargets) {
                    System.err.println(PREPARE_EXPORT_TARGET_MSG.formatted(exportSettings.id));
                    ExportThread exportConsole = new ExportThread(exportSettings);
                    exportConsoles.add(exportConsole);
                    exportConsole.start();
                }

                Map<String, Object> mapStart = new LinkedHashMap<>();
                mapStart.put(IEventConst.EVENT_SOURCE_CLASS_KEY, IEventConst.EVENT_SOURCE_CLASS_VALUE_CONTROL);
                mapStart.put(IEventConst.EVENT_TYPE_KEY, IEventConst.EVENT_TYPE_VALUE_CONTROL_START);
                TriggerUtils.processEvent(mapStart, rootTo, null, triggersQueue, loggerQueue);
                start(outputToConsole, git, logPath, scheduler, rootTo, nodeInfoMap, repoInfoMap, gitRevCommitMap, privateKey, signatureAlgorithm, hashAlgorithm);
            } catch (Throwable t) {
                appendToErrorLog(t, true);
            }

        }
    }

    private static void start(boolean outputToConsole, Git git, Path logPath, Scheduler scheduler, RootDocTo rootTo, Map<String, Object> nodeInfoMap, Map<String, Object> repoInfoMap, Map<String, Object> gitRevCommitMap, PrivateKey privateKey, String signatureAlgorithm, String hashAlgorithm) throws Exception {
        // Start logging
        loggerThread = new LoggerThread(outputToConsole, git, logPath, loggerQueue, nodeInfoMap, repoInfoMap, gitRevCommitMap, privateKey, signatureAlgorithm, hashAlgorithm);
        loggerThread.start();

        // Start processors
        System.err.println("Start event_processors" + (noColor ? OK : OK_COLOR));
        for (JobDocTo jobTo : rootTo.jobTos) {
            for (ProcessorDocTo processorTo : jobTo.processorTos) {
                int instancesCount = processorTo.commandTo.instancesCount;
                CommandTo commandTo = processorTo.commandTo;
                List<ProcessObj> processObjs = ProcessUtils.getProcessObjs(processorTo, commandTo, true, true, processMap, decryptor, secret);
                for (int i = 0; i < instancesCount; i++) {
                    ProcessObj processObj = processObjs.get(i);
                    Thread thread = new ProcessorThread(rootTo, processorTo, triggersQueue, loggerQueue, processObj);
                    thread.start();
                    threads.add(thread);
                }
            }
        }

        // Schedule calendars
        System.err.println("Start triggers/event_calendars" + (noColor ? OK : OK_COLOR));
        CronUtils.scheduleCalendars(scheduler, rootTo, triggersQueue, loggerQueue);

        // Schedule schedulers
        System.err.println("Start triggers/event_schedulers" + (noColor ? OK : OK_COLOR));
        CronUtils.scheduleSchedulers(scheduler, rootTo, triggersQueue, loggerQueue);

        // Start event triggers
        System.err.println("Start triggers/event_providers" + (noColor ? OK : OK_COLOR));
        for (AbstractTo abstractTo : rootTo.eventPublishersTos) {
            EventPublisherDocTo eventPublisherDocTo = (EventPublisherDocTo) abstractTo;
            CommandTo commandTo = eventPublisherDocTo.commandTo;
            int instancesCount = commandTo.instancesCount;
            for (int i = 0; i < instancesCount; i++) {
                List<ProcessObj> processObjs = ProcessUtils.getProcessObjs(eventPublisherDocTo, commandTo, false, true, processMap, decryptor, secret);
                ProcessObj processObj = processObjs.get(i);
                Thread thread = new TriggerThread(rootTo, eventPublisherDocTo, triggersQueue, loggerQueue, processObj);
                thread.start();
                threads.add(thread);
            }
        }

        System.err.println();
    }

    private static Map<String, Object> generateSchemaDataMap(RootDocTo rootTo) {
        Map<String, Object> layoutMap = new LinkedHashMap<>();
        //
        layoutMap.put("root", createEntry(rootTo));
        //

        List<Map<String, Object>> eventsList = new ArrayList<>();
        for (AbstractTo abstractTo : allEventDocTos) {
            eventsList.add(createEntry(abstractTo));
        }
        layoutMap.put("events", eventsList);
        //
        if (rootTo.triggers != null) {
            layoutMap.put("trigger_query", rootTo.triggers);
            List<Map<String, Object>> triggersList = new ArrayList<>();
            for (AbstractTo abstractTo : rootTo.eventPublishersTos) {
                triggersList.add(createEntry(abstractTo));
            }
            for (AbstractTo abstractTo : rootTo.calendarTos) {
                triggersList.add(createEntry(abstractTo));
            }
            for (AbstractTo abstractTo : rootTo.schedulerTos) {
                triggersList.add(createEntry(abstractTo));
            }
            layoutMap.put("triggers", triggersList);
        }
        //
        if (rootTo.jobs != null) {
            layoutMap.put("job_query", rootTo.jobs);
            List<Map<String, Object>> jobslist = new ArrayList<>();
            layoutMap.put("jobs", jobslist);
            for (JobDocTo jobTo : rootTo.jobTos) {
                Map jobMap = new LinkedHashMap();
                jobMap.put("job", createEntry(jobTo));

                if (jobTo.systems != null) {
                    jobMap.put("systemQuery", jobTo.systems);
                    List<Map<String, Object>> systemList = new ArrayList<>();
                    for (SystemDocTo systemTo : jobTo.systemTos) {
                        systemList.add(createEntry(systemTo));
                    }
                    jobMap.put("systems", systemList);
                }
                //
                if (jobTo.processors != null) {
                    jobMap.put("processor_query", jobTo.processors);
                    List<Map<String, Object>> processorList = new ArrayList<>();
                    for (ProcessorDocTo commandTo : jobTo.processorTos) {
                        processorList.add(createEntry(commandTo));
                    }
                    jobMap.put("processors", processorList);
                }
                jobslist.add(jobMap);
            }
        }
        return layoutMap;
    }

    /**
     *
     * @param solrClient
     * @param abstractTos
     * @param docType
     * @param query
     * @return
     * @throws Exception
     */
    public static Collection<AbstractTo> queryDocs(SolrClient solrClient, Map<String /* doc key */, AbstractTo> abstractTos, String docType, String query) throws Exception {
        SolrQuery solrQuery = new SolrQuery();
        if (query == null) {
            solrQuery.setQuery(IDocConst.HEAD_DOC_TYPE + ':' + docType);
        } else {
            solrQuery.setQuery(IDocConst.HEAD_DOC_TYPE + ':' + docType + " AND " + query);
        }

        solrQuery.setFields(IDocConst.HEAD_DOC_NAME);

        QueryResponse queryResponse = solrClient.query(solrQuery);
        SolrDocumentList sdl = queryResponse.getResults();

        Collection<AbstractTo> abstractTosResult = new ArrayList<>();
        for (int i = 0; i < sdl.size(); i++) {
            SolrDocument sd = sdl.get(i);
            String name = (String) sd.getFieldValue(IDocConst.HEAD_DOC_NAME);
            String docKey = DocKeyUtils.createDocKey(docType, name);
            AbstractTo abstractTo = abstractTos.get(docKey);
            abstractTosResult.add(abstractTo);
        }
        return abstractTosResult;
    }

    private static Map<String, Object> createEntry(AbstractTo abstractTo) {
        String name = abstractTo.headTo.name;
        String file = abstractTo.headTo.docFile;
        int indexInFile = abstractTo.headTo.indexInFile;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("file", file);
        map.put("indexInFile", indexInFile);
        return map;
    }

    private static String isAllowedAbstractTo(AbstractTo abstractTo) throws Exception {
        Map<String, Object> docInfoMap = new LinkedHashMap<>();
        docInfoMap.put("name", abstractTo.headTo.name);
        docInfoMap.put("description", abstractTo.headTo.description);
        docInfoMap.put("tags", abstractTo.headTo.tags);
        docInfoMap.put("attr", abstractTo.headTo.attr);
        byte[] jsonBs = CommonUtils.om.writeValueAsBytes(docInfoMap);

        List<CommandTo> validatorCommands = abstractTo.headTo.validatorCommands;
        if (validatorCommands != null) {
            String docFile = abstractTo.headTo.docFile;
            for (int i = 0; i < validatorCommands.size(); i++) {
                CommandTo commandTo = validatorCommands.get(i);
                commandTo.instancesCount = 1;
                commandTo.cmd = FileUtils.convertToRealPath(MainProcess.tmpRepoDir, docFile, commandTo.cmd).toString();
                int exitCode = RunCommandUtils.runCommand(abstractTo, commandTo, jsonBs, decryptor, secret);
                if (exitCode != 0) {
                    return "Validator %d failed: expected condition not met.".formatted(i);
                }
            }
        }
        return null;
    }

    /**
     * <p>
     * startToErrorLog.</p>
     *
     * @param git a {@link org.eclipse.jgit.api.Git} object
     * @param errorLogPath a {@link java.nio.file.Path} object
     */
    private static void startToErrorLog() {
        try {
            if (!Files.exists(errorLogPath) || Files.size(errorLogPath) != 0) {
                Files.write(errorLogPath, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                //String errorLogFileName = errorLogPath.getFileName().toString();
                //GitUtils.commitFile(git, errorLogFileName);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>
     * appendToErrorLog.</p>
     *
     * @param t a {@link java.lang.Throwable} object
     * @param triggerAsEvent
     */
    public static synchronized void appendToErrorLog(Throwable t, boolean triggerAsEvent) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8)) {
            String isoUtcNow = ZonedDateTime.now(ZoneOffset.UTC).toString();
            ps.print(isoUtcNow + ' ');
            t.printStackTrace(ps);
            ps.flush();
            baos.flush();
            byte[] bs = baos.toByteArray();
            Files.write(errorLogPath, bs, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
            if (keepRunning && triggerAsEvent) {
                Map<String, Object> errorMap = createErrorMap(t);
                TriggerUtils.processEvent(errorMap, rootTo, null, triggersQueue, loggerQueue);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static synchronized Map<String, Object> createErrorMap(Throwable t) {
        Map<String, Object> map = new HashMap<>();
        String msg = t.getMessage();
        String msgLocalized = t.getLocalizedMessage();
        String stackTrace = CommonUtils.getStackTraceAsString(t);
        map.put(IEventConst.EVENT_SOURCE_CLASS_KEY, IEventConst.EVENT_SOURCE_CLASS_VALUE_INTERNAL_ERROR);
        map.put(IEventConst.EVENT_TYPE_KEY, IEventConst.EVENT_TYPE_VALUE_INTERNAL_ERROR);
        map.put(IEventConst.EVENT_ERROR_MSG_KEY, msg);
        map.put(IEventConst.EVENT_ERROR_MSG_LOCALIZED_KEY, msgLocalized);
        map.put(IEventConst.EVENT_ERROR_STACK_TRACE_AS_STRING_KEY, stackTrace);

        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        for (StackTraceElement stackTraceElement : stackTraceElements) {
            map.put("stack_trace_file_name", stackTraceElement.getFileName());
            map.put("stack_trace_line_number", stackTraceElement.getLineNumber());
            map.put("stack_trace_method_name", stackTraceElement.getMethodName());
            map.put("stack_trace_class_name", stackTraceElement.getClassName());
            map.put("stack_trace_class_loader_name", stackTraceElement.getClassLoaderName());
            map.put("stack_trace_module_name", stackTraceElement.getModuleName());
            map.put("stack_trace_module_version", stackTraceElement.getModuleVersion());
        }
        return map;
    }

}
