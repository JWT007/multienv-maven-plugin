/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.*
import java.util.*
import java.util.zip.*


t = new IntegrationBase()


def getProjectVersion() {
    def pom = new XmlSlurper().parse(new File(basedir, 'pom.xml'))

    return pom.version
}

def projectVersion = getProjectVersion();

println "Project version: ${projectVersion}"

def classifierList = ['dev-01', 'qa-01' ]

def buildLogFile = new File( basedir, "build.log");

if (!buildLogFile.exists()) {
    throw new FileNotFoundException("build.log does not exists.")
}

def targetFolder = new File (basedir, "target")
if (!targetFolder.exists()) {
    throw new FileNotFoundException("target folder does not exists.")
}

def result = true

classifierList.each { classifier ->
    def tf = new File (targetFolder, "filtering-test-" + projectVersion + "-" + classifier + ".jar")
    println "Checking ${classifier}: " + tf.getAbsolutePath()
    if (!tf.exists()) {
        throw new FileNotFoundException("The file " + tf.getAbsolutePath() + " does not exists.")
    }
    ZipFile zf = new ZipFile(tf);
    def foundClassifier = false
    def foundVersion = false
    try {
      for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
        ZipEntry ze = e.nextElement();
        String name = ze.getName();
        if (name.equals("first.properties")) {
          InputStream is = zf.getInputStream(ze);
          BufferedReader reader = new BufferedReader(new InputStreamReader(is));
          String line;
          def lines = []
          while ((line = reader.readLine()) != null) {
              println "Line: '${line}'"
              lines.push (line)
          }
          is.close();
          println "Lines array:"
          lines.each {
            println " Item -> ${it}"
          }
          if (classifier in lines) {
                println "classifier found."
                foundClassifier = true
          }
          if (projectVersion in lines) {
                println "projectVersion found."
                foundVersion = true
          }

          if (!foundClassifier) {
            println "The first.properties does not contain the classifier."
            result = false
          }
          if (!foundVersion) {
            println "The first.properties does not contain the version."
            result = false
          }
        }
      }
    } finally {
      zf.close();
    } 
}

return result;
