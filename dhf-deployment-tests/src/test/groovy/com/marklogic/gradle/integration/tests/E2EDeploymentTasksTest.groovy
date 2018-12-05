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
    @Shared API api
    
    def "test deploy a new database with config from ml-config" () {
         given:
         File newStagingSchemasDbDir = Paths.get(mlConfigDbDir.toString(), "staging-schemas-database-1.json").toFile()
         Database schDatabase = api.db(getPropertyFromPropertiesFile("mlStagingSchemasDbName1")) 
         copyResourceToFile("ml-config/databases/staging-schemas-database.json", newStagingSchemasDbDir)
         api = new API(getManageClient())
         
         when:
         assert (schDatabase.getCollectionLexicon() == true)
         result = runTask('mlDeployDatabases')
         schDatabase = api.db(getPropertyFromPropertiesFile("mlStagingSchemasDbName1"))
         
         then:
         notThrown(UnexpectedBuildFailure)
         result.task(':mlDeployDatabases').outcome == SUCCESS
         assert (schDatabase.getCollectionLexicon() == false)
    }
    
    @Ignore
    def "test deploy staging database with config from hub-internal-config and ml-config" () {
        
    }
    
    @Ignore
    def "test deploy a new database with config from hub-internal-config" () {
    
    }
    
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
    
    def cleanup() {
        cleanUpProjectDir()
    }
    
}
