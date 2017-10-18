Here is a sample command line execution of Maven to create archetype
described in [README](README.md). The execution is interactive. Sometimes
it is possible to press enter accepting default answer:

```bash
$ mvn -DarchetypeCatalog=local \
>       -DarchetypeGroupId=com.oracle.graal-js \
>       -DarchetypeArtifactId=nodejs-archetype \
>       -DarchetypeVersion=1.0-SNAPSHOT \
>       archetype:generate \
>       -DgraalvmPath=$HOME/bin/graalvm-0.28-dev/
[INFO] Scanning for projects...
[INFO]                                                                         
[INFO] ------------------------------------------------------------------------
[INFO] Building Maven Stub Project (No POM) 1
[INFO] ------------------------------------------------------------------------
[INFO] 
[INFO] >>> maven-archetype-plugin:3.0.1:generate (default-cli) > generate-sources @ standalone-pom >>>
[INFO] 
[INFO] <<< maven-archetype-plugin:3.0.1:generate (default-cli) < generate-sources @ standalone-pom <<<
[INFO] 
[INFO] --- maven-archetype-plugin:3.0.1:generate (default-cli) @ standalone-pom ---
[INFO] Generating project in Interactive mode
[WARNING] Archetype not found in any catalog. Falling back to central repository.
[WARNING] Add a repsoitory with id 'archetype' in your settings.xml if archetype's repository is elsewhere.
Define value for property 'groupId': org.apidesign.demo
Define value for property 'artifactId': nodejsdemo
Define value for property 'version' 1.0-SNAPSHOT: : 
Define value for property 'package' org.apidesign.demo: : org.apidesign.demo.nodejsdemo
[INFO] Using property: algorithmJS = true
[INFO] Using property: algorithmJava = true
[INFO] Using property: algorithmR = false
[INFO] Using property: algorithmRuby = false
[INFO] Using property: graalvmPath = /home/devel/bin/graalvm-0.28-dev/
[INFO] Using property: unitTest = true
Confirm properties configuration:
groupId: org.apidesign.demo
artifactId: nodejsdemo
version: 1.0-SNAPSHOT
package: org.apidesign.demo.nodejsdemo
algorithmJS: true
algorithmJava: true
algorithmR: false
algorithmRuby: false
graalvmPath: /home/devel/bin/graalvm-0.28-dev/
unitTest: true
 Y: : 
....
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
$ cd nodejsdemo/
$ mvn package exec:exec
...
[INFO] --- exec-maven-plugin:1.2.1:exec (default-cli) @ nodejsdemo ---
Listening on 8080
```
and now you should be able to connect to the server and perform all the
`curl` queries as described in the [README](README.md).
