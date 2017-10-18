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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public abstract class AbstractChecker {
    protected abstract String serverCode();

    private Verifier createAndExec(
        String projectName, CountDownLatch cdl, VerificationException[] error,
        int[] port,
        boolean java, boolean js, boolean ruby, boolean r, boolean unitTest
    ) throws IOException, VerificationException {
        File basedir = new File(System.getProperty("basedir"));
        assertTrue("Basedir is dir", basedir.isDirectory());

        File workdir = new File(new File(basedir, "target"), "archetypes");
        workdir.mkdirs();
        assertTrue("workdir is a dir", workdir.isDirectory());

        String workdirName = serverCode() + "X" + projectName;
        Verifier maven = new Verifier(workdir.getPath());
        maven.deleteDirectory(workdirName);

        maven.getSystemProperties().put("archetypeGroupId", "com.oracle.graal-js");
        maven.getSystemProperties().put("archetypeArtifactId", "nodejs-archetype");
        maven.getSystemProperties().put("archetypeVersion", "1.0-SNAPSHOT");
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
        port[0] = assignFreePort(projectDir);

        Verifier mvnProject = new Verifier(projectDir.getPath());
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                mvnProject.executeGoal("package");
                mvnProject.verifyErrorFreeLog();
                try {
                    mvnProject.executeGoals(Arrays.asList("package", "exec:exec"));
                } catch (VerificationException ignoredForNow) {
                    error[1] = ignoredForNow;
                }
            } catch (VerificationException ex) {
                error[0] = ex;
            } finally {
                cdl.countDown();
            }
        });
        return mvnProject;
    }


    @Test
    public void allArchetypes() throws Exception {
        VerificationException[] error = { null, null };
        int[] prefix = { 0 };
        CountDownLatch cdl = new CountDownLatch(1);


        Verifier mvnProject = createAndExec("allArchetypes", cdl, error, prefix, true, true, true, true, true);

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "120\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "720\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "24\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "3628800", true, cdl, mvnProject);

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

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "120\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "720\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "24\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "3628800", true, cdl, mvnProject);

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

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "120\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "Received: /r/10\n", cdl, mvnProject);

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

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "720\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "Received: /r/10\n", cdl, mvnProject);
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

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "24\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "Received: /r/10\n", cdl, mvnProject);

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

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "3628800", true, cdl, mvnProject);

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

        assertUrl(prefix[0], "/HelloMaven!", "Received: /HelloMaven!\n", cdl, mvnProject);
        assertUrl(prefix[0], "/java/5", "Received: /java/5\n", cdl, mvnProject);
        assertUrl(prefix[0], "/js/6", "Received: /js/6\n", cdl, mvnProject);
        assertUrl(prefix[0], "/ruby/4", "Received: /ruby/4\n", cdl, mvnProject);
        assertUrl(prefix[0], "/r/10", "Received: /r/10\n", cdl, mvnProject);

        assertQuit(cdl, error, prefix, mvnProject);
    }

    private static void assertQuit(CountDownLatch cdl, VerificationException[] error, int[] prefix, Verifier mvnProject)
    throws IOException, VerificationException, InterruptedException {
        assertUrl(prefix[0], "/quit", "Quiting...\n", cdl, mvnProject);
        cdl.await();
        if (error[0] != null) {
            throw error[0];
        }
        assertNull("Exit is fine", error[1]);

        mvnProject.verifyTextInLog("Listening on http://localhost:" + prefix[0]);
    }

    private static void assertUrl(int port, String file, String msg, CountDownLatch waitFor, Verifier prj) throws IOException, InterruptedException {
        assertUrl(port, file, msg, false, waitFor, prj);
    }
    private static void assertUrl(int port, String file, String msg, boolean subString, CountDownLatch waitFor, Verifier prj) throws IOException, InterruptedException {
        IOException last = null;
        int failures = 0;
        String[] addresses = findAddresses();
        StringBuilder log = new StringBuilder();
        while (failures++ < 500) {
            URL u = new URL("http", addresses[failures % addresses.length], port, file);
            log.append("Attempt ").append(failures).append(". Connecting to ").append(u).append("\n");
            try (BufferedReader b = openReader(u)) {
                StringBuilder sb = new StringBuilder();
                for (;;) {
                    String line = b.readLine();
                    if (line == null) {
                        if (subString && sb.toString().contains(msg)) {
                            return;
                        }
                        assertEquals(msg, sb.toString());
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


    private static int assignFreePort(File projectDir) throws IOException {
        int[] freePort = { -1 };
        Files.walkFileTree(projectDir.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (freePort[0] >= 0) {
                    return FileVisitResult.SKIP_SIBLINGS;
                }
                String text = new String(Files.readAllBytes(file), "UTF-8");
                final String portDefinition = "PORT = 8080";
                int port = text.indexOf(portDefinition);
                if (port == -1) {
                    return FileVisitResult.CONTINUE;
                }
                int free;
                try (ServerSocket ss = new ServerSocket(0)) {
                    free = ss.getLocalPort();
                    assertTrue("Free port found: " + free, free > 1024);
                }

                String newText = text.substring(0, port + 7) + free + text.substring(port + portDefinition.length());
                try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.WRITE)) {
                    w.write(newText);
                }
                freePort[0] = free;
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
        return freePort[0];
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
}
