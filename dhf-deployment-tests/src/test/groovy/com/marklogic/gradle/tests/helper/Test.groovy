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
import com.marklogic.hub.error.DataHubSecurityNotInstalledException
import com.marklogic.hub.impl.DataHubImpl
import com.marklogic.hub.impl.HubConfigImpl
import com.marklogic.mgmt.ManageClient
import com.marklogic.mgmt.resource.databases.DatabaseManager
import com.marklogic.rest.util.Fragment
import com.marklogic.rest.util.JsonNodeUtil
import com.marklogic.rest.util.ResourcesFragment
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.custommonkey.xmlunit.XMLUnit
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    public static void main(String[] args) {
        // cleaning database files
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "databases",
                "new-database-1.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "databases",
                "staging-database.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "databases",
                "new-database.json"))

        // cleaning server files
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "servers",
                "custom-server.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "servers",
                "staging-server.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "servers",
                "new-server-1.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "servers",
                "new-server.json"))

        // cleaning security files
        // privileges
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "privileges", "privilege-1.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "privileges", "privilege-3.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "privileges", "privilege-2.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "privileges", "privilege-3.json"))
        // roles
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "roles", "ml-manager-role.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "roles", "comb-manager-role.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "roles", "hub-manager-role.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "roles", "comb-manager-role.json"))
        // users
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "users", "ml-project-manager.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "users", "comb-project-manager.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "users", "hub-project-manager.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "users", "comb-project-manager.json"))
        // certificate authorities
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "certificate-authorities", "server.crt"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "certificate-authorities", "serverhub.crt"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "certificate-authorities", "server1.crt"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "certificate-authorities", "serverhub1.crt"))
        // certificate templates
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "certificate-templates", "mltemplate.xml"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "certificate-templates", "hubtemplate.xml"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "certificate-templates", "mltemplate1.xml"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "certificate-templates", "hubtemplate1.xml"))
        // protected-paths
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "protected-paths", "01_pii-protected-paths.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "protected-paths", "02_pii-protected-paths.json"))
        // query-rolesets
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "query-rolesets", "pii-reader.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "query-rolesets", "pii-reader-1.json"))

        // cleaning install modules files
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.ENTITY_CONFIG_DIR, "staging-entity-options.xml"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.ENTITY_CONFIG_DIR, "final-entity-options.xml"))
        FileUtils.deleteDirectory(Paths.get(projectDir, "plugins", "entities").toFile())
        FileUtils.deleteDirectory(Paths.get(projectDir, "src", "main", "ml-modules", "ext").toFile())
        FileUtils.deleteDirectory(Paths.get(projectDir, "src/test/ml-config").toFile())
        FileUtils.deleteDirectory(Paths.get(projectDir, "src/test/ml-modules").toFile())
//        ResourcesFragment rf = new ResourcesFragment(getManageClient().getXml("manage/v2/databases/Documents?view=counts&format=xml"))
    }
    
    static void configureHubConfig1() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        ctx.register(ApplicationConfig.class)
        ctx.refresh()
        _hubConfig = ctx.getBean(HubConfigImpl.class)
        _hubConfig.createProject(projectDir)
        _hubConfig.refreshProject()
    }

    static void configureAdminHubConfig1() {
        AnnotationConfigApplicationContext ctx1 = new AnnotationConfigApplicationContext()
        ctx1.register(ApplicationConfig.class)
        ctx1.refresh()
        _adminhubConfig = ctx1.getBean(HubConfigImpl.class)
        _adminhubConfig.createProject(projectDir)
        _adminhubConfig.setMlUsername(getPropertyFromPropertiesFile("mlSecurityUsername"))
        _adminhubConfig.setMlPassword(getPropertyFromPropertiesFile("mlSecurityPassword"))
        _adminhubConfig.refreshProject()
    }

    
}