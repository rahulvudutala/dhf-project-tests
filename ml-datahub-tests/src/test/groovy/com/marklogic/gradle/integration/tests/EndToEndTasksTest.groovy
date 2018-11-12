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
import com.fasterxml.jackson.databind.JsonNode
import com.marklogic.gradle.tests.helper.BaseTest
import com.marklogic.hub.HubConfig

import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class EndToEndTasksTest extends BaseTest {

    @Shared def result

    def "hubInit task test"() {
        when: "before running hubInit"
        File pluginsDir = new File(projectDir, "plugins")
        File hubConfigDir = new File(projectDir, HubConfig.HUB_CONFIG_DIR)
        File mlConfigDir = new File(projectDir, HubConfig.USER_CONFIG_DIR)

        then:
        pluginsDir.isDirectory() == false
        hubConfigDir.isDirectory() == false
        mlConfigDir.isDirectory() == false


        when: "hubInit task is run"
        result = runTask('hubInit')

        then:
        result.task(":hubInit").outcome == SUCCESS
        pluginsDir.isDirectory() == true
        hubConfigDir.isDirectory() == true
        mlConfigDir.isDirectory() == true


        when: "hubInit task is run again when hub is initialized"
        result = runTask('hubInit')

        then:
        result.task(":hubInit").outcome == SUCCESS
        pluginsDir.isDirectory() == true
        hubConfigDir.isDirectory() == true
        mlConfigDir.isDirectory() == true
    }

    def "hubCreateEntity task test"() {
        given:
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodEntityDir = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1").toFile()
        File destDir = Paths.get(prodEntityDir.toString(), "my-new-unique-product-test-entity-1.entity.json").toFile()


        when: "entityName parameter is missing in the gradle command"
        result = runFailTask('hubCreateEntity')

        then:
        notThrown(UnexpectedBuildFailure)
        result.output.contains('entityName property is required. Supply the parameter with -PentityName=Yourentity') == true
        result.task(':hubCreateEntity').outcome == FAILED
        entitiesDir.isDirectory() == false


        when: "entityName parameter is provided"
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
            }
        """
        result = runTask('hubCreateEntity')

        then: "test if entity directory is created and add entity.json file is added"
        notThrown(UnexpectedBuildFailure)
        result.task(':hubCreateEntity').outcome == SUCCESS
        entitiesDir.isDirectory() == true
        prodEntityDir.isDirectory() == true
        copyResourceToFile("my-new-unique-product-test-entity-1.entity.json", destDir)


        when: "entityName parameter is provided with duplicate value, the existing folder shouldn't be replaced"
        result = runTask('hubCreateEntity')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubCreateEntity').outcome == SUCCESS
        destDir.exists() == true


        when: "create other entities for the scenario"
        println()

        then:
        println()
    }

    def "hubSaveIndexes task test"() {
        given:
        File entityConfigDatabasesDir = Paths.get(projectDir.toString(), HubConfig.ENTITY_CONFIG_DIR, "databases").toFile()
        File stagingFile = Paths.get(projectDir.toString(), HubConfig.ENTITY_CONFIG_DIR, "databases", 
            HubConfig.STAGING_ENTITY_DATABASE_FILE).toFile()
        File finalFile = Paths.get(projectDir.toString(), HubConfig.ENTITY_CONFIG_DIR, "databases", 
            HubConfig.FINAL_ENTITY_DATABASE_FILE).toFile()
        JsonNode stagingDatabaseIndexObj = null
        JsonNode finalDatabaseIndexObj = null
        JsonNode savedStagingIndexes = null
        JsonNode savedFinalIndexes = null


        when: "there is no index info in entity.json file, src/main/entity-config/databases.json shouldn't be created"
        result = runTask('hubSaveIndexes')
        stagingDatabaseIndexObj = getJsonResource(stagingFile.getPath())
        savedStagingIndexes = stagingDatabaseIndexObj.get("range-path-index")
        finalDatabaseIndexObj = getJsonResource(finalFile.getPath())
        savedFinalIndexes = finalDatabaseIndexObj.get("range-path-index")

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubSaveIndexes').outcome == SUCCESS
        entityConfigDatabasesDir.isDirectory() == true
        stagingFile.exists() == true
        finalFile.exists() == true
        assert (savedStagingIndexes == null)
        assert (savedFinalIndexes == null)
        
        when: "there is index info in entity.json file, indexes should be created in src/main/entity-config/databases"
        addIndexInfo("my-new-unique-product-test-entity-1")
        result = runTask('hubSaveIndexes')
        stagingDatabaseIndexObj = getJsonResource(stagingFile.getPath())
        savedStagingIndexes = stagingDatabaseIndexObj.get("range-path-index")
        finalDatabaseIndexObj = getJsonResource(finalFile.getPath())
        savedFinalIndexes = finalDatabaseIndexObj.get("range-path-index")

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubSaveIndexes').outcome == SUCCESS
        entityConfigDatabasesDir.isDirectory() == true
        stagingFile.exists() == true
        finalFile.exists() == true
        assert (savedStagingIndexes.size() == 1)
        assert (savedFinalIndexes.size() == 1)
        
        
        when: "hubSaveIndexes is run again, duplicate entries shouldn't exist"
        result = runTask('hubSaveIndexes')
        stagingDatabaseIndexObj = getJsonResource(stagingFile.getPath())
        savedStagingIndexes = stagingDatabaseIndexObj.get("range-path-index")
        finalDatabaseIndexObj = getJsonResource(finalFile.getPath())
        savedFinalIndexes = finalDatabaseIndexObj.get("range-path-index")
        
        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubSaveIndexes').outcome == SUCCESS
        entityConfigDatabasesDir.isDirectory() == true
        stagingFile.exists() == true
        finalFile.exists() == true
        assert (savedStagingIndexes.size() == 1)
        assert (savedFinalIndexes.size() == 1)
    }
}
