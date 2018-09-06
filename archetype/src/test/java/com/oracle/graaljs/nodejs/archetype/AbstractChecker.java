/**
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graaljs.nodejs.archetype;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import org.junit.Test;

public abstract class AbstractChecker {
    private static final Logger CONSOLE;
    static {
        CONSOLE = Logger.getLogger(AbstractChecker.class.getName());
        final ConsoleHandler handler = new ConsoleHandler();
        CONSOLE.addHandler(handler);
        handler.setLevel(Level.FINE);
        CONSOLE.setLevel(Level.FINE);
    }
    protected abstract String serverCode();

    private Verifier createAndExec(
        String projectName, CountDownLatch cdl, Exception[] error,
        int[] port,
        boolean java, boolean js, boolean ruby, boolean r, boolean unitTest
    ) throws IOException, VerificationException {
        skipWithoutLanguage("js");
        if (ruby) skipWithoutLanguage("ruby");
        if (r) skipWithoutLanguage("r");

        File basedir = new File(System.getProperty("basedir"));
        assertTrue("Basedir is dir", basedir.isDirectory());

        File workdir = new File(new File(basedir, "target"), "archetypes");
        workdir.mkdirs();
        assertTrue("workdir is a dir", workdir.isDirectory());

        String workdirName = serverCode() + "X" + projectName;
        Verifier maven = new Verifier(workdir.getPath());
        maven.deleteDirectory(workdirName);

        String version = System.getProperty("archVersion");
        assertNotNull("version is specified", version);

        maven.getSystemProperties().put("archetypeGroupId", "com.oracle.graal-js");
        maven.getSystemProperties().put("archetypeArtifactId", "nodejs-archetype");
        maven.getSystemProperties().put("archetypeVersion", version);
        maven.getSystemProperties().put("archetypeCatalog", "local");
        maven.getSystemProperties().put("artifactId", workdirName);
        maven.getSystemProperties().put("groupId", "test.oracle.test");
        maven.addCliOption("-DalgorithmJava=" + java);
        maven.addCliOption("-DalgorithmJS=" + js);
        maven.addCliOption("-DalgorithmRuby=" + ruby);
        maven.addCliOption("-DalgorithmR=" + r);
        maven.addCliOption("-DunitTest=" + unitTest);
        maven.addCliOption("-DserverCode=" + serverCode());
        maven.setAutoclean(false);

        workdir.mkdirs();
        maven.executeGoal("archetype:generate");
        maven.verifyErrorFreeLog();

        File projectDir = new File(workdir, workdirName);
        assertTrue("Project dir created", projectDir.isDirectory());
        File pom = new File(projectDir, "pom.xml");
        assertTrue("pom.xml created", pom.isFile());
        File nbactions = new File(projectDir, "nbactions.xml");
        assertTrue("nbactions.xml created", nbactions.isFile());

        Verifier mvnProject = new Maven(projectDir.getPath());
        Executors.newSingleThreadExecutor().submit(() -> {
            boolean again;
            int retries = 10;
            do {
                again = false;
                try {
                    assignFreePort(projectDir, port);
                    mvnProject.executeGoals(Arrays.asList("package", "exec:exec"));
                    mvnProject.verifyErrorFreeLog();
                } catch (VerificationException ex) {
                    if (ex.getMessage().contains("listen EADDRINUSE")) {
                        again = retries-- > 0;
                        CONSOLE.log(Level.WARNING, "Port {0} is in use. Trying again: {1}", new Object[]{port[0], again});
                    }
                    if (!again) {
                        error[0] = ex;
                    }
                } catch (IOException ex) {
                    error[0] = ex;
                } finally {
                    if (!again) {
                        if (error[0] == null) {
                            CONSOLE.log(Level.INFO, "node.js server started on {0}", port[0]);
                        }
                        cdl.countDown();
                    }
                }
            } while (again);
        });
        mvnProject.addCliOption("--quiet");
        return mvnProject;
    }


    @Test
    public void allArchetypes() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);


        Verifier mvnProject = createAndExec("allArchetypes", cdl, error, prefix, true, true, true, true, true);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "120\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "720\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "24\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "3628800", true, cdl, mvnProject);

        assertQuit(cdl, error, prefix, mvnProject);
    }

    @Test
    public void noUnitTest() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);

        Verifier mvnProject = createAndExec("noUnitTest", cdl, error, prefix, true, true, true, true, false);

        File pom = new File(mvnProject.getBasedir(), "pom.xml");
        assertNoText("surefire", pom);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "120\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "720\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "24\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "3628800", true, cdl, mvnProject);

        File launcher = new File(new File(new File(new File(new File(mvnProject.getBasedir()), "src"), "main"), "js"), "launcher.js");
        assertNoText("process.env.CLASSPATH", launcher);

        assertQuit(cdl, error, prefix, mvnProject);
    }


    @Test
    public void justJava() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);

        Verifier mvnProject = createAndExec("justJava", cdl, error, prefix, true, false, false, false, true);

        assignNoTextInServices("Algorithm js()", mvnProject);
        assignNoTextInServices("Algorithm ruby()", mvnProject);
        assignNoTextInServices("Algorithm r()", mvnProject);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "120\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "Received: /r/10\n", cdl, mvnProject);

        assertQuit(cdl, error, prefix, mvnProject);
    }

    @Test
    public void justJavaScript() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);

        Verifier mvnProject = createAndExec("justJavaScript", cdl, error, prefix, false, true, false, false, true);

        assignNoTextInServices("JavaFactorial", mvnProject);
        assignNoTextInServices("Algorithm ruby", mvnProject);
        assignNoTextInServices("Algorithm r", mvnProject);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "720\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "Received: /r/10\n", cdl, mvnProject);
        assertQuit(cdl, error, prefix, mvnProject);
    }

    @Test
    public void justRuby() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);

        Verifier mvnProject = createAndExec("justRuby", cdl, error, prefix, false, false, true, false, true);

        assignNoTextInServices("JavaFactorial", mvnProject);
        assignNoTextInServices("Algorithm js()", mvnProject);
        assignNoTextInServices("Algorithm r()", mvnProject);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "24\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "Received: /r/10\n", cdl, mvnProject);

        assertQuit(cdl, error, prefix, mvnProject);
    }

    @Test
    public void justR() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);

        Verifier mvnProject = createAndExec("justR", cdl, error, prefix, false, false, false, true, true);

        assignNoTextInServices("JavaFactorial", mvnProject);
        assignNoTextInServices("Algorithm js", mvnProject);
        assignNoTextInServices("Algorithm ruby", mvnProject);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "3628800", true, cdl, mvnProject);

        assertQuit(cdl, error, prefix, mvnProject);
    }

    @Test
    public void empty() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);

        Verifier mvnProject = createAndExec("empty", cdl, error, prefix, false, false, false, false, true);

        assignNoTextInServices("JavaFactorial", mvnProject);
        assignNoTextInServices("BigInteger", mvnProject);
        assignNoTextInServices("Executor background", mvnProject);
        assignNoTextInServices("Executors", mvnProject);
        assignNoTextInServices("Algorithm js", mvnProject);
        assignNoTextInServices("Algorithm ruby", mvnProject);
        assignNoTextInServices("Algorithm r", mvnProject);

        assertUrl(prefix, "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix, "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix, "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix, "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix, "/r/10", "Received: /r/10\n", cdl, mvnProject);

        assertQuit(cdl, error, prefix, mvnProject);
    }

    private static void assertQuit(CountDownLatch cdl, VerificationException[] error, int[] prefix, Verifier mvnProject)
    throws IOException, VerificationException, InterruptedException {
        assertUrl(prefix, "/quit", "Quiting...\n", cdl, mvnProject);
        cdl.await();
        if (error[0] != null) {
            throw error[0];
        }
        assertNull("Exit is fine", error[1]);

        mvnProject.verifyTextInLog("Listening on http://localhost:" + prefix[0]);
    }

    private static void assertUrl(int[] port, String file, String msg, CountDownLatch waitFor, Verifier prj) throws IOException, InterruptedException {
        assertUrl(port, file, msg, false, waitFor, prj);
    }
    private static void assertUrl(int[] port, String file, String msg, boolean subString, CountDownLatch waitFor, Verifier prj) throws IOException, InterruptedException {
        IOException last = null;
        int failures = 0;
        int previousPort = port[0];
        String[] addresses = findAddresses();
        StringBuilder log = new StringBuilder();
        final int maxRetry = 500;
        boolean firstDump = false;
        while (failures++ < maxRetry) {
            if (previousPort != port[0]) {
                CONSOLE.log(Level.INFO, "Port changed from {0} to {1}. Resetting connections.", new Object[]{previousPort, port[0]});
                previousPort = port[0];
                failures = 0;
            }
            URL u = new URL("http", addresses[failures % addresses.length], port[0], file);
            File lf = new File(prj.getBasedir(), prj.getLogFileName());
            boolean doLog = false;
            if (lf.length() == 0) {
                if (failures > 0 && failures % 50 == 0) {
                    doLog = true;
                }
                if (failures > (int) (maxRetry * 0.9)) {
                    doLog = true;
                    if (!firstDump) {
                        dumpStackOfNode();
                        firstDump = true;
                    }
                }
            } else {
                doLog = true;
            }
            if (doLog) {
                String logMsg = String.format("Attempt %d, log file size %d, connecting to %s", failures, lf.length(), u);
                log.append(logMsg).append("\n");
                CONSOLE.log(Level.INFO, logMsg);
            }
            try (BufferedReader b = openReader(u)) {
                StringBuilder sb = new StringBuilder();
                for (;;) {
                    String line = b.readLine();
                    if (line == null) {
                        if (subString && sb.toString().contains(msg)) {
                            CONSOLE.log(Level.INFO, "substring {0} found", msg);
                            return;
                        }
                        assertEquals(msg, sb.toString());
                        CONSOLE.log(Level.INFO, "message {0} obtained", msg);
                        return;
                    }
                    sb.append(line).append("\n");
                }
            } catch (IOException ex) {
                last = ex;
                if (waitFor.getCount() > 0) {
                    Thread.sleep(100);
                    continue;
                }
                throw dumpLogFile(log, prj, ex);
            }
        }
        if (last != null) {
            throw dumpLogFile(log, prj, last);
        }
        CONSOLE.log(Level.SEVERE, "time out accessing {0}", msg);
        fail("time out accessing " + msg);
    }

    private static BufferedReader openReader(URL u) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection) u.openConnection(Proxy.NO_PROXY);
        conn.setConnectTimeout(3000);
        return new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
    }

    private static void assertNoText(String text, File file) throws IOException {
        for (String line : Files.readAllLines(file.toPath())) {
            assertEquals("No " + text + " in " + file, -1, line.indexOf(text));
        }
    }

    private static void assignNoTextInServices(String txt, Verifier verifier) throws IOException {
        File projectDir = new File(verifier.getBasedir());
        File java = new File(new File(new File(new File(new File(new File(new File(projectDir, "src"), "main"), "java"), "test"), "oracle"), "test"), "Services.java");
        assertTrue("Services.java exists" + java, java.isFile());
        assertNoText(txt, java);
    }

    private static final Pattern PORT = Pattern.compile("PORT *= *([0-9]+)");
    private static void assignFreePort(File projectDir, int[] freePort) throws IOException {
        freePort[0] = -1;
        Files.walkFileTree(projectDir.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (freePort[0] != -1) {
                    return FileVisitResult.SKIP_SIBLINGS;
                }
                String text = new String(Files.readAllBytes(file), "UTF-8");
                Matcher m = PORT.matcher(text);
                if (!m.find()) {
                    return FileVisitResult.CONTINUE;
                }
                assert Integer.parseInt(m.group(1)) >= 0;

                int free;
                try (ServerSocket ss = new ServerSocket(0)) {
                    free = ss.getLocalPort();
                    assertTrue("Free port found: " + free, free >= 1024);
                }
                CONSOLE.log(Level.INFO, "Trying to use port {0} for {1}", new Object[] { free, projectDir.getName() });

                int begin = m.start(1);
                int end = m.end(1);
                String newText = text.substring(0, begin) + free + text.substring(end);
                try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.WRITE)) {
                    w.write(newText);
                }
                freePort[0] = free;
                CONSOLE.log(Level.INFO, "Port is assigned to {0}", free);
                return FileVisitResult.SKIP_SIBLINGS;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw new IOException("Cannot check " + file);
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        assertNotEquals("Proper port shall be allocated", -1, freePort[0]);
    }

    private static String[] findAddresses() throws SocketException {
        List<String> arr = new ArrayList<>();
        arr.add("localhost");
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface face = en.nextElement();
            Enumeration<InetAddress> addresses = face.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                arr.add(address.getHostAddress());
            }
        }
        return arr.toArray(new String[arr.size()]);
    }

    private static IOException dumpLogFile(StringBuilder sb, Verifier prj, Throwable cause) throws IOException {
        try {
            dumpStackOfNode();
        } catch (InterruptedException ex) {
            throw (InterruptedIOException) new InterruptedIOException(ex.getMessage()).initCause(ex);
        }

        File log = new File(prj.getBasedir(), prj.getLogFileName());
        if (!log.isFile()) {
            sb.append("\nlog file doesn't exist: ").append(log);
        }
        for (String line : Files.readAllLines(log.toPath())) {
            sb.append("\nTEST: ").append(line);
        }
        if (cause instanceof ConnectException) {
            return (IOException) new ConnectException(sb.toString()).initCause(cause);
        }
        return new IOException(sb.toString(), cause);
    }

    private void skipWithoutLanguage(String id) {
        String javaHome = System.getProperty("java.home");
        assertNotNull("java.home property must be available", javaHome);
        File jre = new File(javaHome);
        File node = new File(new File(jre, "bin"), "node");
        assertTrue("Missing " + node + " use -Dgraalvm=... to point to GraalVM 1.0 and newer installations", node.exists());

        StringBuilder sb = new StringBuilder();

        try {
            ProcessBuilder pb = new ProcessBuilder(node.getPath(), "--polyglot", "-e", "print(Polyglot.eval('" + id + "', '42'))");
            Process p = pb.start();
            p.waitFor(10, TimeUnit.SECONDS);
            readFully(p.getErrorStream(), sb);
            readFully(p.getInputStream(), sb);
        } catch (IOException | InterruptedException ex) {
            throw new AssertionError(ex);
        }

        final boolean successful = "42\n".equals(sb.toString());
        if (!successful) {
            for (String lang : System.getProperty("hasLanguages", "").split(",")) {
                if (id.matches(lang)) {
                    fail("Language " + id + " should be present, but:\n" + sb);
                }
            }
        }
        assumeTrue("Evaluation with " + id + " wasn't successful: " + sb, successful);
    }

    private static void readFully(InputStream in, StringBuilder sb) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        for (;;) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            sb.append(line).append("\n");
        }
    }

    private class Maven extends Verifier {
        Maven(String basedir) throws VerificationException {
            super(basedir);
        }

        @Override
        public void executeGoals(List<String> goals, Map<String, String> envVars) throws VerificationException {
            CONSOLE.log(Level.INFO, "executing {0} at {1}", new Object[]{goals, getBasedir()});
            try {
                super.executeGoals(goals, envVars);
            } catch (VerificationException ex) {
                CONSOLE.log(Level.SEVERE, "failed " + ex.getMessage(), ex);
                throw ex;
            }
            CONSOLE.log(Level.INFO, "OK");
        }
    }

    private static void dumpStackOfNode() throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        final File jdk = new File(javaHome).getParentFile();
        final File bin = new File(jdk, "bin");
        final File jps = new File(bin, "jps");
        final File jstack = new File(bin, "jstack");
        assertTrue("There should be " + jps, jps.exists());
        assertTrue("There should be " + jstack, jstack.exists());
        Process p = new ProcessBuilder().command(jps.getPath()).
            redirectErrorStream(true).
            start();

        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        for (;;) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            String[] twoParts = line.trim().split(" ");
            if (twoParts.length == 1) {
                // node.js process has no Java name right now
                Process stack = new ProcessBuilder().command(jstack.getPath(), twoParts[0]).
                    redirectErrorStream(true).
                    start();
                StringBuilder sb = new StringBuilder();
                readFully(stack.getInputStream(), sb);
                CONSOLE.log(Level.INFO, sb.toString());
                assertEquals("jstack execution for " + twoParts[0], 0, stack.waitFor());
            }
        }
        int err = p.waitFor();
        assertEquals("Execution OK", 0, err);
    }
}
