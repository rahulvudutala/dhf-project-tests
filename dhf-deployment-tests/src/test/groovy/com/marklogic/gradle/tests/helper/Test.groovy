/*
 * Copyright 2012-2018 MarkLogic Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.marklogic.gradle.tests.helper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.marklogic.client.FailedRequestException
import com.marklogic.client.document.DocumentManager
import com.marklogic.client.eval.EvalResult
import com.marklogic.client.eval.EvalResultIterator
import com.marklogic.client.eval.ServerEvaluationCall
import com.marklogic.client.io.DocumentMetadataHandle
import com.marklogic.client.io.Format
import com.marklogic.client.io.InputStreamHandle
import com.marklogic.client.io.StringHandle
import com.marklogic.hub.ApplicationConfig
import com.marklogic.hub.DatabaseKind
import com.marklogic.hub.HubConfig
import com.marklogic.hub.impl.HubConfigImpl
import com.marklogic.mgmt.ManageClient
import com.marklogic.mgmt.api.API
import com.marklogic.mgmt.api.server.Server
import com.marklogic.mgmt.resource.databases.DatabaseManager
import com.marklogic.rest.util.Fragment
import com.marklogic.rest.util.JsonNodeUtil
import com.marklogic.xcc.impl.ContentSourceImpl.AuthType

import groovy.ui.SystemOutputInterceptor

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.custommonkey.xmlunit.XMLUnit
import org.gradle.internal.impldep.org.apache.ivy.core.module.descriptor.ExtendsDescriptor
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.PropertiesPropertySource
import org.w3c.dom.Document
import org.xml.sax.SAXException
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class Test extends BaseTest {
    
    @Autowired
    private static ManageClient _manageClient
    
    public static void main(String[] args) {
//        CredentialsProvider provider = new BasicCredentialsProvider();
//        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("hub-amdin-user", "hub-amdin-user")
//        provider.setCredentials(AuthScope., credentials)
//        
//        HttpClient hc = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build()
//        
//        HttpGet getR = new HttpGet("http://localhost:8002/manage/v2/servers")
//        
//        HttpResponse resp = hc.execute(getR);
//        println(resp.toString())
        
        boolean x = Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "databases",
            "staging-schemas-database-1.json"))
        println(x)
    }
}