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

    @Shared File hubConfigDir = Paths.get(projectDir.toString(), HubConfig.HUB_CONFIG_DIR).toFile()
    @Shared File hubConfigDbDir = Paths.get(hubConfigDir.toString(), "databases").toFile()

    @Shared API api

    def "test deploy a new database with config from ml-config" () {
        given:
        api = new API(getManageClient())
        File newStagingSchemasDbDir = Paths.get(mlConfigDbDir.toString(), "staging-schemas-database-1.json").toFile()
        Database schDatabase = api.db(getPropertyFromPropertiesFile("mlStagingSchemasDbName1"))
        copyResourceToFile("ml-config/databases/staging-schemas-database.json", newStagingSchemasDbDir)

        when:
        assert (schDatabase.getCollectionLexicon() == null)
        result = runTask('mlDeployDatabases')
        schDatabase = api.db(getPropertyFromPropertiesFile("mlStagingSchemasDbName1"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (schDatabase.getCollectionLexicon() == false)
    }

    def "test deploy staging database with config from hub-internal-config and ml-config" () {
        given:
        api = new API(getManageClient())
        File mlConfigStagingDbDir = Paths.get(mlConfigDbDir.toString(), "staging-database.json").toFile()
        Database stgDatabase = api.db(getPropertyFromPropertiesFile("mlStagingDbName"))
        String stgSchemaDb = getPropertyFromPropertiesFile("mlStagingSchemasDbName1")
        copyResourceToFile("ml-config/databases/staging-database.json", mlConfigStagingDbDir)

        when:
        result = runTask('mlDeployDatabases')
        stgDatabase = api.db(getPropertyFromPropertiesFile("mlStagingDbName"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (stgDatabase.getSchemaDatabase().equals(stgSchemaDb))
    }

    def "test deploy a new database with config from hub-internal-config" () {
        given:
        api = new API(getManageClient())
        File hubConfigNewDbDir = Paths.get(hubConfigDbDir.toString(), "new-database.json").toFile()

        Database newDb = api.db(getPropertyFromPropertiesFile("mlNewDb"))
        String newDbSchemaDbName = getPropertyFromPropertiesFile("mlStagingSchemasDbName1")
        String newDbName = getPropertyFromPropertiesFile("mlNewDb")

        copyResourceToFile("hub-internal-config/databases/new-database.json", hubConfigNewDbDir)

        when:
        result = runTask('mlDeployDatabases')
        newDb = api.db(getPropertyFromPropertiesFile("mlNewDb"))

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlDeployDatabases').outcome == SUCCESS
        assert (newDb.databaseName.equals(newDbName))
        assert (newDb.getSchemaDatabase().equals(newDbSchemaDbName))
    }
}
