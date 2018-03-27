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
/* global Java, Polyglot.*/

if (typeof Java === 'undefined' || !Java.Worker) {
    throw 'Use GraalVM 0.29 or newer with enabled --jvm interop!';
}

var executor = new Java.Worker();
var className = "${package}.Services";
var servicesClass = Java.type(className);
var services = new servicesClass(require, global, async (work, finish) => {
    var r = await executor.submit(work);
    finish(r);
});
global.cast = function(value, prototype) {
    if (prototype != null) {
        throw "Use null as prototype, was: " + prototype;
    }
    return value;
};
#if ($serverCode.equals("js"))
var algorithms = {
#if ($algorithmJava.equals("true"))
    'java' : function(n, worker) {
        return worker ? worker.submit(services.factorial, [ n ]) : services.factorial(n);
    },
#end
#if ($algorithmJS.equals("true"))
    'js' : function fac(n) {
        if (n <= 1)
            return 1;
        return n * fac(n - 1);
    },
#end
#if ($algorithmRuby.equals("true"))
    'ruby' : function (n) {
        algorithms.ruby = Polyglot.eval("application/x-ruby",
            "def fac(n)\n" +
            "  f = (1..n).reduce(1, :*)\n" +
            "  f.to_s\n" +
            "end\n" +
            "method(:fac)"
        );
        return algorithms.ruby(n);
    },
#end
#if ($algorithmR.equals("true"))
    'r' : function r(n) {
        algorithms.r = Polyglot.eval("text/x-r", "factorial");
        return algorithms.r(n);
    },
#end
};
services.postInit(algorithms);

const PORT = 8080;

var http = require("http");
var server = http.createServer(async (request, response) => {
    var url = request.url;
    if (url === "/quit") {
        response.end("Quiting...\n");
        global.quit();
        return;
    }
#if ($algorithmJava.equals("true"))
    if (url.startsWith("/java/")) {
        var res = await algorithms.java(Number.parseInt(url.substring(6)), executor);
        response.end(res.toString() + '\n');
        return;
    }
#end
#if ($algorithmRuby.equals("true"))
    if (url.startsWith("/ruby/")) {
        response.end(algorithms.ruby(Number.parseInt(url.substring(6))) + "\n");
        return;
    }
#end
#if ($algorithmJS.equals("true"))
    if (url.startsWith("/js/")) {
        response.end(algorithms.js(Number.parseInt(url.substring(4))) + "\n");
        return;
    }
#end
#if ($algorithmR.equals("true"))
    if (url.startsWith("/r/")) {
        response.end(algorithms.r(Number.parseInt(url.substring(3))) + "\n");
        return;
    }
#end
    response.end("Received: " + url + "\n");
});
server.listen(PORT);
#else
services.postInit(null);
#end


#if ($unitTest.equals("true"))
if (process.argv.length > 2 && process.argv[2] === "org.apache.maven.surefire.booter.ForkedBooter") {
    // run unit tests
    var clazz = process.argv[2];
    var servicesClass = Java.type(clazz);
    servicesClass.main(process.argv[3], process.argv[4], process.argv[5], process.argv[6]);
#if ($serverCode.equals("js"))
} else {
    console.log("Listening on http://localhost:" + PORT + "/");
#end
}
#else
#if ($serverCode.equals("js"))
console.log("Listening on http://localhost:" + PORT + "/");
#end
#end
