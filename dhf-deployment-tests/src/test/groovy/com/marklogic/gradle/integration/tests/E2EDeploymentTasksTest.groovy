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

package com.marklogic.gradle.integration.tests

import java.nio.file.Path
import java.nio.file.Paths

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.gradle.testkit.runner.UnexpectedBuildSuccess

import com.fasterxml.jackson.databind.JsonNode
import com.marklogic.gradle.tests.helper.BaseTest
import com.marklogic.hub.HubConfig
import com.marklogic.mgmt.api.API
import com.marklogic.mgmt.api.database.Database
import com.marklogic.mgmt.api.server.Server

import spock.lang.Ignore
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class E2EDeploymentTasksTest extends BaseTest {

    @Shared def result
    @Shared File mlConfigDir = Paths.get(projectDir.toString(), HubConfig.USER_CONFIG_DIR).toFile()
    @Shared File mlConfigDbDir = Paths.get(mlConfigDir.toString(), "databases").toFile()
    @Shared File mlConfigServerDir = Paths.get(mlConfigDir.toString(), "servers").toFile()

    @Shared File hubConfigDir = Paths.get(projectDir.toString(), HubConfig.HUB_CONFIG_DIR).toFile()
    @Shared File hubConfigDbDir = Paths.get(hubConfigDir.toString(), "databases").toFile()
    @Shared File hubConfigServerDir = Paths.get(hubConfigDir.toString(), "servers").toFile()

    @Shared API api

    def "test deploy a new database from ml-config" () {
        given:
        api = new API(getManageClient())
        File newDbConfig = Paths.get(mlConfigDbDir.toString(), "new-database-1.json").toFile()
        Database newDb = api.db(getPropertyFromPropertiesFile("mlNewDb1Name"))
        copyResourceToFile("ml-config/databases/new-database-1.json", newDbConfig)

        when:
        assert (newDb.getCollectionLexicon() == null)
        result = runTask('mlDeployDatabases')
        newDb = api.db(getPropertyFromPropertiesFile("mlNewDb1Name"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (newDb.getCollectionLexicon() == false)
    }

    def "test deploy staging database from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File custStagingDbCongig = Paths.get(mlConfigDbDir.toString(), "staging-database.json").toFile()
        Database custStagingDb = api.db(getPropertyFromPropertiesFile("mlStagingDbName"))
        String stgSchemaDbName = getPropertyFromPropertiesFile("mlNewDb1Name")
        copyResourceToFile("ml-config/databases/staging-database.json", custStagingDbCongig)

        when:
        assert (custStagingDb.schemaDatabase == getPropertyFromPropertiesFile("mlStagingSchemasDbName"))
        result = runTask('mlDeployDatabases')
        custStagingDb = api.db(getPropertyFromPropertiesFile("mlStagingDbName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (custStagingDb.getSchemaDatabase().equals(stgSchemaDbName))
    }

    // TODO: Deploy new database from hub-internal-config and ml-config

    def "test deploy a new database from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File hubConfigNewDbDir = Paths.get(hubConfigDbDir.toString(), "new-database.json").toFile()

        Database newDb = api.db(getPropertyFromPropertiesFile("hubNewDb1Name"))
        String newDbName = getPropertyFromPropertiesFile("hubNewDb1Name")
        String newDbSchemaDbName = getPropertyFromPropertiesFile("mlNewDb1Name")

        copyResourceToFile("hub-internal-config/databases/new-database-1.json", hubConfigNewDbDir)

        when:
        assert (newDb.getSchemaDatabase() == null)
        result = runTask('mlDeployDatabases')
        newDb = api.db(getPropertyFromPropertiesFile("hubNewDb1Name"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (newDb.databaseName.equals(newDbName))
        assert (newDb.getSchemaDatabase().equals(newDbSchemaDbName))
    }

    def "test deploy a new server from ml-config" () {
        given:
        api = new API(getManageClient())
        File newServerConfig = Paths.get(mlConfigServerDir.toString(), "new-server-1.json").toFile()
        Server newServer = api.server(getPropertyFromPropertiesFile("mlNewAppserverName"))
        copyResourceToFile("ml-config/servers/new-server-1.json", newServerConfig)

        when:
        assert (newServer.address == null)
        result = runTask('mlDeployServers')
        newServer = api.server(getPropertyFromPropertiesFile("mlNewAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (newServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("mlNewAppserverPort")))
        assert (newServer.serverName.equals(getPropertyFromPropertiesFile("mlNewAppserverName")))
        assert (newServer.groupName.equals("Default"))
    }

    def "test deploy staging server from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File custStagingServerConfig = Paths.get(mlConfigServerDir.toString(), "staging-server.json").toFile()
        Server custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))
        copyResourceToFile("ml-config/servers/staging-server.json", custStagingServerConfig)

        when:
        assert (custStagingServer.port == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingPort")))
        result = runTask('mlDeployServers')
        custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (custStagingServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingCustomPort")))
        assert (custStagingServer.serverName.equals(getPropertyFromPropertiesFile("mlStagingAppserverName")))
        assert (custStagingServer.contentDatabase.equals(getPropertyFromPropertiesFile("hubNewDb1Name")))
        assert (custStagingServer.groupName.equals("Default"))
    }

    def "test deploy staging server from hub-internal-config and ml-config without resource dbs in ml-config" () {
        given:
        api = new API(getManageClient())
        File custStagingServerConfig = Paths.get(mlConfigServerDir.toString(), "staging-server.json").toFile()
        Server custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))
        copyResourceToFile("ml-config/servers/staging-server-2.json", custStagingServerConfig)

        when:
        assert (custStagingServer.port == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingCustomPort")))
        result = runTask('mlDeployServers')
        custStagingServer = api.server(getPropertyFromPropertiesFile("mlStagingAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (custStagingServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("mlStagingPort")))
        assert (custStagingServer.serverName.equals(getPropertyFromPropertiesFile("mlStagingAppserverName")))
        assert (custStagingServer.contentDatabase.equals(getPropertyFromPropertiesFile("mlStagingDbName")))
        assert (custStagingServer.groupName.equals("Default"))
    }

    def "test deploy a new server from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File newStagingServerConfig = Paths.get(hubConfigServerDir.toString(), "new-server.json").toFile()
        Server newStagingServer = api.server(getPropertyFromPropertiesFile("hubNewAppserverName"))
        copyResourceToFile("hub-internal-config/servers/new-server-1.json", newStagingServerConfig)

        when:
        assert (newStagingServer.port == null)
        result = runTask('mlDeployServers')
        newStagingServer = api.server(getPropertyFromPropertiesFile("hubNewAppserverName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployServers').outcome == SUCCESS
        assert (newStagingServer.getPort() == Integer.parseInt(getPropertyFromPropertiesFile("hubNewAppserverPort")))
        assert (newStagingServer.serverName.equals(getPropertyFromPropertiesFile("hubNewAppserverName")))
        assert (newStagingServer.contentDatabase.equals(getPropertyFromPropertiesFile("hubNewDb1Name")))
        assert (newStagingServer.groupName.equals("Default"))
    }

    // TODO: mlDeploySecurity to deploy users roles and any certificates
    def "test deploy user from ml-config" () {

    }

    def "test deploy user from hub-internal-config" () {

    }

    def "test deploy user from hub-internal-config and ml-config" () {

    }

    def "test deploy role from ml-config" () {

    }

    def "test deploy role from hub-internal-config" () {

    }

    def "test deploy role from hub-internal-config and ml-config" () {

    }

    // TODO: mlDeployPrivileges

    // TODO: Load Modules tasks

    @Ignore
    def "test server response"() {
        given:
        API api = new API(getManageClient())

        when:
        Server s = api.server("custom-db")
        println("")

        then:
        if(s != null) {
            println(s.getModulesDatabase())
        } else {
            println("null")
        }
    }
}
