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

import spock.lang.Ignore
import spock.lang.Shared

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.FAILED

class EndToEndTasksTest extends BaseTest {

    @Shared def result

    def setup() {
        copyResourceToFile("gradle_properties", new File(projectDir, "gradle.properties"))
        getPropertiesFile()
    }
    @Ignore
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
        File orderEntityDir = Paths.get(entitiesDir.toString(), "my-new-unique-order-test-entity-1").toFile()
        File custEntityDir = Paths.get(entitiesDir.toString(), "my-new-unique-customer-test-entity-1").toFile()
        File destDir = Paths.get(prodEntityDir.toString(), "my-new-unique-product-test-entity-1.entity.json").toFile()
        File orderDestDir = Paths.get(prodEntityDir.toString(), "my-new-unique-order-test-entity-1.entity.json").toFile()
        File custDestDir = Paths.get(prodEntityDir.toString(), "my-new-unique-customer-test-entity-1.entity.json").toFile()


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
        propertiesFile << """
            ext {
                entityName=my-new-unique-order-test-entity-1
            }
        """
        runTask('hubCreateEntity')
        propertiesFile << """
            ext {
                entityName=my-new-unique-customer-test-entity-1
            }
        """
        runTask('hubCreateEntity')

        then:
        orderEntityDir.exists() == true
        custEntityDir.exists() == true
        copyResourceToFile("my-new-unique-order-test-entity-1.entity.json", orderDestDir)
        copyResourceToFile("my-new-unique-customer-test-entity-1.entity.json", custDestDir)
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

    def "mlUpdateIndexes task test"() {
        given:
        File stagingFile = Paths.get(projectDir.toString(), HubConfig.ENTITY_CONFIG_DIR, "databases",
                HubConfig.STAGING_ENTITY_DATABASE_FILE).toFile()
        File finalFile = Paths.get(projectDir.toString(), HubConfig.ENTITY_CONFIG_DIR, "databases",
                HubConfig.FINAL_ENTITY_DATABASE_FILE).toFile()
        int stagingIndexCount = getStagingRangePathIndexSize()
        int finalIndexCount = getFinalRangePathIndexSize()
        int jobIndexCount = getJobsRangePathIndexSize()


        when: "mlUpdateIndexes is run and stagingFile and finalFile ar empty, no indexes are deployed"
        FileUtils.forceDelete(stagingFile)
        FileUtils.forceDelete(finalFile)
        result = runTask('mlUpdateIndexes')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlUpdateIndexes').outcome == SUCCESS
        getStagingRangePathIndexSize() == stagingIndexCount
        getFinalRangePathIndexSize() == finalIndexCount


        when: "mlUpdateIndexes is run when indexes are saved in stagingFile and finalFile"
        runTask('hubSaveIndexes')
        result = runTask('mlUpdateIndexes')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlUpdateIndexes').outcome == SUCCESS
        getStagingRangePathIndexSize() == stagingIndexCount + 1
        getFinalRangePathIndexSize() == finalIndexCount + 1


        when: "trying to do mlUpdateIndexes again"
        result = runTask('mlUpdateIndexes')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':mlUpdateIndexes').outcome == SUCCESS
        getStagingRangePathIndexSize() == stagingIndexCount + 1
        getFinalRangePathIndexSize() == finalIndexCount + 1
        deleteRangePathIndexes("data-hub-STAGING")
        deleteRangePathIndexes("data-hub-FINAL")
    }

    def "hubDeployUserModules task test for deploying entities"() {
        given:
        int stagingDbCount = getStagingDocCount()
        int finalDbCount = getFinalDocCount()

        when:
        result = runTask('hubDeployUserModules')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubDeployUserModules').outcome == SUCCESS
        getStagingDocCount() == stagingDbCount + 3
        getFinalDocCount() == finalDbCount + 3
    }

    def "hubCreateInputFlow task test with an entity not in database, it creates an input flow"() {
        given:
        // clearing databases to clear entities
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME, HubConfig.DEFAULT_FINAL_NAME)
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
                flowName=input-flow-1
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodInputDir = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1", "input").toFile()
        File flowDir = Paths.get(prodInputDir.toString(), "input-flow-1").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.sjs").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.sjs").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.sjs").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.sjs").toFile()

        when: "creating an input flow with an entity not in database"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubCreateInputFlow').outcome == SUCCESS
        prodInputDir.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    def "hubCreateInputFlow task test with an entity not in filesystem, it still creates an input flow"() {
        given:
        propertiesFile << """
            ext {
                entityName=non-existent-entity
                flowName=non-existent-entity-input-flow
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File nonExistentEntityInputDir = Paths.get(entitiesDir.toString(), "non-existent-entity", "input").toFile()
        File flowDir = Paths.get(nonExistentEntityInputDir.toString(), "non-existent-entity-input-flow").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.sjs").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.sjs").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.sjs").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.sjs").toFile()


        when: "creating an input flow with an entity not in filesystem, it creates an entity folder"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(':hubCreateInputFlow').outcome == SUCCESS
        nonExistentEntityInputDir.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    def "hubCreateInputFlow task test when input params are missing "() {
        given:
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File defaultValueTestEntity = Paths.get(entitiesDir.toString(), "default-value-test-entity", "input").toFile()
        File flowDir = Paths.get(defaultValueTestEntity.toString(), "default-value-test-input-flow").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.sjs").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.sjs").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.sjs").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.sjs").toFile()


        when: "entityName parameter is missing when running the task"
        propertiesFile << """
            ext {
                flowName=non-existent-entity-input-flow
                useES=false
            }
        """
        result = runFailTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildSuccess)
        result.output.contains('entityName property is required')
        result.task(":hubCreateInputFlow").outcome == FAILED


        when: "flowName parameter is missing when running the task"
        copyResourceToFile("gradle_properties", new File(projectDir, "gradle.properties"))
        getPropertiesFile()
        propertiesFile << """
            ext {
                entityName=non-existent-entity-input-flow
                useES=false
            }
        """
        result = runFailTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildSuccess)
        result.output.contains('flowName property is required')
        result.task(":hubCreateInputFlow").outcome == FAILED


        when: "any of dataFormat/pluginformat are missing, default values are set to json/sjs"
        copyResourceToFile("gradle_properties", new File(projectDir, "gradle.properties"))
        getPropertiesFile()
        propertiesFile << """
            ext {
                entityName=default-value-test-entity
                flowName=default-value-test-input-flow
                useES=false
            }
        """
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":hubCreateInputFlow").outcome == SUCCESS
        defaultValueTestEntity.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    // ToDo: Also check the contents of the files created.
    def "hubCreateInputFlow task test with Json and Sjs combination"() {
        given:
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
                flowName=my-new-unique-product-JS-input-flow-1
                dataFormat=json
                pluginFormat=sjs
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodEntity = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1", "input")
                .toFile()
        File flowDir = Paths.get(prodEntity.toString(), "my-new-unique-product-JS-input-flow-1").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.sjs").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.sjs").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.sjs").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.sjs").toFile()

        when: "input flow is run json/sjs combination"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":hubCreateInputFlow").outcome == SUCCESS
        prodEntity.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    // ToDo: Also check the contents of the files created.
    def "hubCreateInputFlow task test with Xml and Sjs combination"() {
        given:
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
                flowName=my-new-unique-product-XS-input-flow-1
                dataFormat=xml
                pluginFormat=sjs
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodEntity = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1", "input")
                .toFile()
        File flowDir = Paths.get(prodEntity.toString(), "my-new-unique-product-XS-input-flow-1").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.sjs").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.sjs").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.sjs").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.sjs").toFile()

        when: "input flow is run xml/sjs combination"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":hubCreateInputFlow").outcome == SUCCESS
        prodEntity.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    // TODO: Also check the contents of the files created.
    def "hubCreateInputFlow task test with Json and Xqy combination"() {
        given:
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
                flowName=my-new-unique-product-JX-input-flow-1
                dataFormat=json
                pluginFormat=xqy
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodEntity = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1", "input")
                .toFile()
        File flowDir = Paths.get(prodEntity.toString(), "my-new-unique-product-JX-input-flow-1").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.xqy").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.xqy").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.xqy").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.xqy").toFile()

        when: "input flow is run json/xqy combination"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":hubCreateInputFlow").outcome == SUCCESS
        prodEntity.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    // ToDo: Also check the contents of the files created.
    def "hubCreateInputFlow task test with Xml and Xqy combination"() {
        given:
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
                flowName=my-new-unique-product-XX-input-flow-1
                dataFormat=xml
                pluginFormat=xqy
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodEntity = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1", "input")
                .toFile()
        File flowDir = Paths.get(prodEntity.toString(), "my-new-unique-product-XX-input-flow-1").toFile()
        File contentFile = Paths.get(flowDir.toString(), "content.xqy").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.xqy").toFile()
        File mainFile = Paths.get(flowDir.toString(), "main.xqy").toFile()
        File triplesFile = Paths.get(flowDir.toString(), "triples.xqy").toFile()

        when: "input flow is run xml/xqy combination"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":hubCreateInputFlow").outcome == SUCCESS
        prodEntity.isDirectory() == true
        flowDir.isDirectory() == true
        contentFile.isFile() == true
        headersFile.isFile() == true
        mainFile.isFile() == true
        triplesFile.isFile() == true
    }

    def "hubCreateInputFlow task test creating a duplicate flow"() {
        given:
        propertiesFile << """
            ext {
                entityName=my-new-unique-product-test-entity-1
                flowName=my-new-unique-product-JS-input-flow-1
                dataFormat=json
                pluginFormat=sjs
                useES=false
            }
        """
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile()
        File prodEntity = Paths.get(entitiesDir.toString(), "my-new-unique-product-test-entity-1", "input")
                .toFile()
        File flowDir = Paths.get(prodEntity.toString(), "my-new-unique-product-JS-input-flow-1").toFile()
        File headersFile = Paths.get(flowDir.toString(), "headers.sjs").toFile()
        File oldHeadersFile = Paths.get("src/main/resources/headers-old.sjs").toFile()
        copyResourceToFile("headers.sjs", headersFile)


        when: "creating a duplicate input flow, the existing flows shouldn't be overridden"
        result = runTask('hubCreateInputFlow')

        then:
        notThrown(UnexpectedBuildFailure)
        result.task(":hubCreateInputFlow").outcome == SUCCESS
        FileUtils.contentEquals(headersFile, oldHeadersFile) == false
    }
    //
    //    def "hubCreateInputFlow task positive test flow with combinations of sjs/xqy and json/xml"() {
    //    }
    //
    //    def "hubCreateInputFlow task test to create duplicate flows"() {
    //    }
}
