#*
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
 *#

package ${package};

#if ($algorithmJava.equals("true"))
import java.math.BigInteger;
#end
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Services {
#if (!$serverCode.equals("js"))
    private static final int PORT = 8080;
#end
    private static Services INSTANCE;

    private final Worker worker;
    private final Require require;
    private final Global global;

    Algorithms algorithms;

    public Services(Require require, Global global, Worker worker) {
        this.require = require;
        this.global = global;
        this.worker = worker;
        INSTANCE = this;
    }

    public static Services getDefault() {
        return INSTANCE;
    }

    public void postInit(Algorithms newAlgorithms) {
        if (newAlgorithms == null) {
#if (!$serverCode.equals("js"))
            newAlgorithms = new AlgorithmsImpl();
#else
            throw new NullPointerException();
#end
        }
        this.algorithms = newAlgorithms;
#if (!$serverCode.equals("js"))
        final Object rawHttp = require.require("http");
        Http http = global.cast(rawHttp, (Http) null);
        Server server = http.createServer((in, out) -> {
            final String url = in.url();
            if (url.equals("/quit")) {
                out.end("Quiting...\n");
                global.quit();
            }
#if ($algorithmJava.equals("true"))
            if (url.startsWith("/java/")) {
                worker.submit(() -> {
                    final BigInteger result = algorithms.java(Integer.parseInt(url.substring(6)));
                    return result;
                }, (result) -> {
                    out.end(result + "\n");
                });
                return;
            }
#end
#if ($algorithmRuby.equals("true"))
            if (url.startsWith("/ruby/")) {
                out.end(algorithms.ruby(Integer.parseInt(url.substring(6))) + "\n");
                return;
            }
#end
#if ($algorithmJS.equals("true"))
            if (url.startsWith("/js/")) {
                out.end(algorithms.js(Integer.parseInt(url.substring(4))) + "\n");
                return;
            }
#end
#if ($algorithmR.equals("true"))
            if (url.startsWith("/r/")) {
                out.end(algorithms.r(Integer.parseInt(url.substring(3))) + "\n");
                return;
            }
#end
            out.end("Received: " + url + "\n");
        });
        server.listen(PORT);
        System.err.println("Listening on http://localhost:" + PORT + "/");
#end
    }

    @FunctionalInterface
    public interface Require {
        Object require(String module);
    }

    public interface Global {
        public Polyglot Polyglot();
        public void quit();
        public Http cast(Object value, Http prototype);
        public Server cast(Object value, Server prototype);
        public Computation cast(Object value, Computation prototype);
    }

    public interface Polyglot {
        public Object eval(String mimeType, String code);
        public void export(String name, Object obj);
    }

    @FunctionalInterface
    public interface Worker {
        public <T> void submit(Supplier<T> background, Consumer<T> finish);
    }

    public interface Http {
        public Server createServer(Handler handler);
    }

    @FunctionalInterface
    public interface Handler {
        public void call(IncommingMessage in, ServerResponse out);
    }

    public interface Server {
        public void listen(int port);
    }

    public interface IncommingMessage {
        String url();
    }

    public interface ServerResponse {
        void end(String text);
    }

    public static final class TransferablePromiseCompletion {
        private final Object resolve;
        private final Object reject;
        private final Thread ownerThread;

        public TransferablePromiseCompletion(Object resolve, Object reject) {
            this.resolve = resolve;
            this.reject = reject;
            this.ownerThread = Thread.currentThread();
        }

        public Object getPromiseResolve() {
            assert Thread.currentThread() == this.ownerThread : "This object must be accessed from the creating thread";
            return this.resolve;
        }

        public Object getPromiseReject() {
            assert Thread.currentThread() == this.ownerThread : "This object must be accessed from the creating thread";
            return this.reject;
        }        
    }

#if ($algorithmJava.equals("true"))
    public BigInteger factorial(int value) {
        BigInteger one = BigInteger.valueOf(1);
        BigInteger n = BigInteger.valueOf(value);
        BigInteger result = one;
        while (n.compareTo(one) >= 0) {
            result = result.multiply(n);
            n = n.subtract(one);
        }
        return result;
    }
#end

    public interface Algorithms {
#if ($algorithmJava.equals("true"))
        BigInteger java(int n);
#end
#if ($algorithmRuby.equals("true"))
        String ruby(int n);
#end
#if ($algorithmJS.equals("true"))
        Number js(int n);
#end
#if ($algorithmR.equals("true"))
        Number r(int n);
#end
    }

    @FunctionalInterface
    public interface Computation {
        public Object compute(Object value);
    }

#if (!$serverCode.equals("js"))
    private final class AlgorithmsImpl implements Algorithms {
#if ($algorithmRuby.equals("true"))
        private Computation ruby;
#end
#if ($algorithmJS.equals("true"))
        private Computation js;
#end
#if ($algorithmR.equals("true"))
        private Computation r;
#end
#if ($algorithmJava.equals("true"))
        @Override
        public final BigInteger java(int n) {
            return factorial(n);
        }
#end

#if ($algorithmJS.equals("true"))
        @Override
        public final Number js(int n) {
            if (js == null) {
                final String jsCode =
                    "(function fac(n) {\n" +
                    "    if (n <= 1) return 1;\n" +
                    "    return n * fac(n - 1);\n" +
                    "})\n";

                Object fn = global.Polyglot().eval("text/javascript", jsCode);
                js = global.cast(fn, (Computation) null);
            }
            return (Number) js.compute(n);
        }
#end

#if ($algorithmR.equals("true"))
        @Override
        public final Number r(int n) {
            if (r == null) {
                Object fn = global.Polyglot().eval("text/x-r", "factorial");
                r = global.cast(fn, (Computation) null);
            }
            return (Number) r.compute(n);
        }
#end

#if ($algorithmRuby.equals("true"))
        @Override
        public final String ruby(int n) {
            if (ruby == null) {
                String rubyCode =
                    "def fac(n)\n" +
                    "  f = (1..n).reduce(1, :*)\n" +
                    "  f.to_s\n" +
                    "end\n" +
                    "method(:fac)";
                Object fn = global.Polyglot().eval("application/x-ruby", rubyCode);
                ruby = global.cast(fn, (Computation) null);
            }
            return (String) ruby.compute(n);
        }
#end
    }
#end
}
