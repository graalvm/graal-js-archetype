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

package com.oracle.graaljs.nodewizard;

import com.oracle.graaljs.nodewizard.NodeJsJava.ServerCode;
import java.awt.EventQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import net.java.html.json.ComputedProperty;
import net.java.html.json.Function;
import net.java.html.json.Model;
import net.java.html.json.OnPropertyChange;
import net.java.html.json.OnReceive;
import net.java.html.json.Property;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.platform.JavaPlatformManager;
import org.netbeans.api.java.platform.PlatformsCustomizer;
import org.netbeans.api.templates.TemplateRegistration;
import org.openide.awt.HtmlBrowser.URLDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

@Model(className = "NodeJsJavaModel", instance = true, properties = {
    @Property(name = "current", type = String.class),
    @Property(name = "ok", type = boolean.class),
    @Property(name = "msg", type = String.class),
    @Property(name = "serverCode", type = ServerCode.class),
    @Property(name = "algJava", type = boolean.class),
    @Property(name = "algJS", type = boolean.class),
    @Property(name = "algRuby", type = boolean.class),
    @Property(name = "algR", type = boolean.class),
    @Property(name = "unitTesting", type = boolean.class),
    @Property(name = "graalvmPath", type = String.class),
    @Property(name = "graalvmCheck", type = String.class),
    @Property(name = "archetypeVersions", type = String.class, array = true),
    @Property(name = "archetypeVersion", type = String.class),
})
public class NodeJsJava {
    private static final String ARCH_JAR_NAME = "nodejs-archetype.jar";
    private ScheduledExecutorService background;

    @TemplateRegistration(
            position = 133,
            page = "nodeJsJavaWizard.html",
            content = "nodeJsJava.archetype",
            folder = "Project/ClientSide",
            displayName = "#nodeJsJavaWizard",
            iconBase = "com/oracle/graaljs/nodewizard/nodeJava.png",
            description = "nodeJsJavaDescription.html"
    )
    @Messages("nodeJsJavaWizard=Node.js+Java Application")
    public static NodeJsJavaModel nodejsJavaAppWizard() throws IOException {
        NodeJsJavaModel data = new NodeJsJavaModel();
        findGraalVM(data);
        data.setUnitTesting(true);
        data.setServerCode(ServerCode.js);
        final String localVersion = findArchetypeVersion();
        data.setArchetypeVersion(localVersion);
        data.getArchetypeVersions().add(localVersion);
        data.searchArtifact("com.oracle.graal-js", "nodejs-archetype");
        return data;
    }

    private static void findGraalVM(NodeJsJavaModel data) {
        for (JavaPlatform p : JavaPlatformManager.getDefault().getInstalledPlatforms()) {
            FileObject fo = p.findTool("node");
            if (fo != null) {
                data.setGraalvmPath(fo.getParent().getParent().getPath());
                break;
            }
        }
    }
    private ScheduledFuture<?> graalVMCheck;


    @Function
    public void chooseJDK(NodeJsJavaModel model) {
        if ("summary".equals(model.getCurrent())) {
            return;
        }
        EventQueue.invokeLater(() -> {
            PlatformsCustomizer.showCustomizer(null);
            findGraalVM(model);
        });
    }

    @Function
    public void download(NodeJsJavaModel model) throws MalformedURLException {
        if ("summary".equals(model.getCurrent())) {
            return;
        }
        final URL url = new URL("http://www.oracle.com/technetwork/oracle-labs/program-languages/");
        URLDisplayer.getDefault().showURL(url);
    }

    @ComputedProperty
    static int errorCode(String graalvmPath, String graalvmCheck) {
        if (graalvmPath == null || !new File(new File(new File(graalvmPath), "bin"), "node").exists()) {
            return 1;
        }
        if ("pending".equals(graalvmCheck)) {
            return 4;
        }
        if (!"function".equals(graalvmCheck)) {
            return 3;
        }
        return 0;
    }

    @OnPropertyChange("graalvmPath")
    void checkGraalVM(NodeJsJavaModel model) {
        model.setGraalvmCheck("pending");
        ScheduledFuture<?> previous = graalVMCheck;
        if (previous != null) {
            previous.cancel(true);
        }
        graalVMCheck = background().schedule(() -> {
            String status;
            try {
                status = testGraalVMVersion(model.getGraalvmPath());
            } catch (IOException | InterruptedException ex) {
                status = ex.getMessage();
            }
            model.setGraalvmCheck(status);
        }, 1, TimeUnit.SECONDS);
    }

    private ScheduledExecutorService background() {
        if (background == null) {
            background = Executors.newSingleThreadScheduledExecutor();
        }
        return background;
    }

    static String testGraalVMVersion(String path) throws IOException, InterruptedException {
        File nodeFile = new File(new File(new File(path), "bin"), "node");
        if (!nodeFile.isFile()) {
            return nodeFile + " not found";
        }
        ProcessBuilder b = new ProcessBuilder(
            nodeFile.getPath(),
            "--polyglot",
            "--use-classpath-env-var",
            "--jvm",
            "-e",
            "print(typeof Java === 'object' && typeof Java.Worker);"
        );
        b.redirectErrorStream(true);
        Process p = b.start();
        InputStream is = p.getInputStream();
        byte[] arr = new byte[128];
        StringBuilder sb = new StringBuilder();
        for (;;) {
            int len = is.read(arr);
            if (len == -1) {
                break;
            }
            sb.append(new String(arr, 0, len, "UTF-8"));
            p.waitFor(100, TimeUnit.MILLISECONDS);
        }
        return sb.toString().trim();
    }

    @ComputedProperty
    static String algorithmJava(boolean algJava) {
        return algJava ? "true" : "false";
    }

    @ComputedProperty
    static String algorithmJS(boolean algJS) {
        return algJS ? "true" : "false";
    }

    @ComputedProperty
    static String algorithmRuby(boolean algRuby) {
        return algRuby ? "true" : "false";
    }

    @ComputedProperty
    static String algorithmR(boolean algR) {
        return algR ? "true" : "false";
    }

    @ComputedProperty
    static String unitTest(boolean unitTesting) {
        return unitTesting ? "true" : "false";
    }

    @ComputedProperty
    static boolean anySample(boolean algJava, boolean algJS, boolean algRuby, boolean algR) {
        return algJava || algJS || algRuby || algR;
    }

    @ComputedProperty
    static String archetypeCatalog() {
        final String userHome = System.getProperty("user.home");
        return verifyArchetypeExists(userHome) ? "local" : "remote";
    }

    @OnReceive(url = "http://search.maven.org/solrsearch/select?q=g:{group}%20AND%20a:{artifact}&wt=json")
    static void searchArtifact(NodeJsJavaModel model, QueryResult result) {
        if (result != null && result.getResponse() != null) {
            for (QueryArtifact doc : result.getResponse().getDocs()) {
                if (
                    doc.getA().equals("nodejs-archetype") &&
                    doc.getG().equals("com.oracle.graal-js") &&
                    doc.getLatestVersion() != null
                ) {
                    final List<String> knownVersions = model.getArchetypeVersions();
                    final String lastest = doc.getLatestVersion();
                    model.setArchetypeVersion(lastest);
                    knownVersions.clear();
                    knownVersions.add(lastest);
                    try {
                        knownVersions.add(findArchetypeVersion());
                    } catch (IOException ex) {
                        // ignore
                    }
                    break;
                }
            }
        }
    }

    static boolean verifyArchetypeExists(final String userHome) {
        if (userHome == null) {
            return false;
        }
        File m2Repo = new File(new File(userHome, ".m2"), "repository");
        if (!m2Repo.isDirectory()) {
            return false;
        }
        final String version;
        try {
            version = findArchetypeVersion();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
        final String baseName = "nodejs-archetype-" + version;
        final String jarName = baseName + ".jar";
        File archetype = new File(new File(new File(new File(new File(
            new File(m2Repo, "com"), "oracle"), "graal-js"), "nodejs-archetype"),
            version), jarName);
        File pom = new File(archetype.getParentFile(), baseName + ".pom");
        if (archetype.isFile() && pom.isFile()) {
            return true;
        }
        if (!archetype.getParentFile().mkdirs()) {
            return false;
        }
        InputStream is = NodeJsJavaModel.class.getResourceAsStream(ARCH_JAR_NAME);
        if (is == null) {
            return false;
        }
        try (FileOutputStream os = new FileOutputStream(archetype)) {
            copyStreams(is, os);
            is.close();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        try (
            JarInputStream jar = new JarInputStream(NodeJsJavaModel.class.getResourceAsStream(ARCH_JAR_NAME));
            FileOutputStream os = new FileOutputStream(pom)
        ) {
            for (;;) {
                ZipEntry entry = jar.getNextEntry();
                if (entry == null) {
                    break;
                }
                if (entry.getName().endsWith("pom.xml")) {
                    copyStreams(jar, os);
                    break;
                }
                jar.closeEntry();
            }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return archetype.isFile() && pom.isFile();
    }

    static String findArchetypeVersion() throws IOException {
        try (
            JarInputStream jar = new JarInputStream(NodeJsJavaModel.class.getResourceAsStream(ARCH_JAR_NAME));
        ) {
            for (;;) {
                ZipEntry entry = jar.getNextEntry();
                if (entry == null) {
                    break;
                }
                if (entry.getName().endsWith("pom.properties")) {
                    Properties p = new Properties();
                    p.load(jar);
                    return p.getProperty("version");
                }
                jar.closeEntry();
            }
            throw new FileNotFoundException("pom.properties not found");
        }
    }

    private static void copyStreams(InputStream is, final OutputStream os) throws IOException {
        byte[] arr = new byte[4096];
        for (;;) {
            int len = is.read(arr);
            if (len == -1) {
                break;
            }
            os.write(arr, 0, len);
        }
    }

    enum ServerCode {
        js, java
    }
}
