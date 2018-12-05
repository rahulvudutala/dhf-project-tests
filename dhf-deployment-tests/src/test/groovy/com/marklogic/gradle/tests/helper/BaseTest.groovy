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
import com.marklogic.hub.impl.DataHubImpl
import com.marklogic.hub.impl.HubConfigImpl
import com.marklogic.mgmt.ManageClient
import com.marklogic.mgmt.resource.databases.DatabaseManager
import com.marklogic.rest.util.Fragment
import com.marklogic.rest.util.JsonNodeUtil
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

@EnableAutoConfiguration
class BaseTest extends Specification {

    static final String projectDir = new File("").getAbsolutePath()
    static File buildFile
    static File propertiesFile

    private ManageClient _manageClient;
    private DatabaseManager _databaseManager;

    static private HubConfigImpl _hubConfig
    static private DataHubImpl _datahub

    static final protected Logger logger = LoggerFactory.getLogger(BaseTest.class)

    public HubConfigImpl hubConfig() {
        return _hubConfig
    }

    static BuildResult runTask(String... task) {
        return GradleRunner.create()
                .withProjectDir(new File(projectDir.toString()))
                .withArguments(task)
                .withDebug(true)
                .withPluginClasspath()
                .build()
    }

    BuildResult runFailTask(String... task) {
        return GradleRunner.create()
                .withProjectDir(new File(projectDir.toString()))
                .withArguments(task)
                .withDebug(true)
                .withPluginClasspath().buildAndFail()
    }

    void clearDatabases(String... databases) {
        ServerEvaluationCall eval = _hubConfig.newStagingClient().newServerEval();
        String installer = '''
            declare variable $databases external;
            for $database in fn:tokenize($databases, ",")
             return
               xdmp:eval(
                 'cts:uris() ! xdmp:document-delete(.)',
                 (),
                 map:entry("database", xdmp:database($database))
               )
        '''
        eval.addVariable("databases", String.join(",", databases));
        EvalResultIterator result = eval.xquery(installer).eval();
    }

    protected Document getXmlFromResource(String resourceName) throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setIgnoringElementContentWhitespace(true)
        factory.setNamespaceAware(true)
        DocumentBuilder builder = factory.newDocumentBuilder()
        return builder.parse(new File("src/test/resources/" + resourceName).getAbsoluteFile())
    }

    protected JsonNode getJsonResource(String absoluteFilePath) {
        try {
            InputStream jsonDataStream = new FileInputStream(new File(absoluteFilePath))
            ObjectMapper jsonDataMapper = new ObjectMapper()
            return jsonDataMapper.readTree(jsonDataStream)
        } catch (IOException e) {
            e.printStackTrace()
        }
    }

    static void copyResourceToFile(String resourceName, File dest) {
        def file = new File("src/test/resources/" + resourceName)
        FileUtils.copyFile(file, dest)
    }

    static void writeSSLFiles(File serverFile, File ssl) {
        def files = []
        files << ssl
        files << serverFile
        ObjectNode serverFiles = JsonNodeUtil.mergeJsonFiles(files);
        FileUtils.writeStringToFile(serverFile, serverFiles.toString());
    }

    static int getStagingDocCount() {
        return getStagingDocCount(null)
    }

    static int getStagingDocCount(String collection) {
        return getDocCount(HubConfig.DEFAULT_STAGING_NAME, collection)
    }

    static int getFinalDocCount() {
        return getFinalDocCount(null)
    }

    static int getFinalDocCount(String collection) {
        return getDocCount(HubConfig.DEFAULT_FINAL_NAME, collection)
    }

    static int getModulesDocCount() {
        return getDocCount(HubConfig.DEFAULT_MODULES_DB_NAME, null)
    }

    static int getDocCount(String database, String collection) {
        int count = 0
        String collectionName = ""
        if (collection != null) {
            collectionName = "'" + collection + "'"
        }
        EvalResultIterator resultItr = runInDatabase("xdmp:estimate(fn:collection(" + collectionName + "))", database)
        if (resultItr == null || !resultItr.hasNext()) {
            return count
        }
        EvalResult res = resultItr.next()
        count = Math.toIntExact((long) res.getNumber())
        return count
    }

    static EvalResultIterator runInDatabase(String query, String databaseName) {
        ServerEvaluationCall eval
        switch (databaseName) {
            case HubConfig.DEFAULT_STAGING_NAME:
                eval = _hubConfig.newStagingClient().newServerEval()
                break
            case HubConfig.DEFAULT_FINAL_NAME:
                eval = _hubConfig.newFinalClient().newServerEval()
                break
            case HubConfig.DEFAULT_MODULES_DB_NAME:
                eval = _hubConfig.newModulesDbClient().newServerEval()
                break
            case HubConfig.DEFAULT_JOB_NAME:
                eval = _hubConfig.newJobDbClient().newServerEval()
        }
        try {
            return eval.xquery(query).eval()
        }
        catch (FailedRequestException e) {
            e.printStackTrace()
            throw e
        }
    }

    public DatabaseManager getDatabaseManager() {
        if (_databaseManager == null) {
            _databaseManager = new DatabaseManager(getManageClient());
        }
        return _databaseManager;
    }

    public ManageClient getManageClient() {
        if (_manageClient == null) {
            _manageClient = _hubConfig.getManageClient();
        }
        return _manageClient;
    }

    public int getStagingRangePathIndexSize() {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(_hubConfig.getDbName(DatabaseKind.STAGING));
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    public int getFinalRangePathIndexSize() {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(_hubConfig.getDbName(DatabaseKind.FINAL));
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    public int getJobsRangePathIndexSize() {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(_hubConfig.getDbName(DatabaseKind.JOB));
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    void getPropertiesFile() {
        propertiesFile = new File(Paths.get(".").resolve("gradle.properties").toString())
    }

    String getPropertyFromPropertiesFile(String key) {
        Properties p = new Properties()
        p.load(new FileInputStream("gradle.properties"))
        return p.getProperty(key)
    }

    void deleteRangePathIndexes(String databaseName) {
        String databaseFragment = getDatabaseManager().getPropertiesAsJson(databaseName)
        ObjectMapper mapper = new ObjectMapper()
        JsonNode dbObject = mapper.readTree(databaseFragment)
        JsonNode rangePathIndexes = dbObject.get("range-path-index")
        for(JsonNode rangePathIndex : rangePathIndexes) {
            String type = rangePathIndex.get("scalar-type").asText()
            String collation = rangePathIndex.get("collation").asText()
            String path = rangePathIndex.get("path-expression").asText()
            String pos = rangePathIndex.get("range-value-positions").asText()
            String val = rangePathIndex.get("invalid-values").asText()
            deleteRangePathIndexes(databaseName, type, collation, path, pos, val)
        }
    }

    void deleteRangePathIndexes(String databaseName, String type, String collation, String pathSeq,
            String pos, String val) {
        ServerEvaluationCall eval = _hubConfig.newStagingClient().newServerEval()

        if(pos.equals("false")) {
            pos = "fn:false()";
        } else {
            pos = "fn:true()";
        }

        String installer ='''
                import module namespace admin = \"http://marklogic.com/xdmp/admin\" at \"/MarkLogic/admin.xqy\";
                let $dbname := "''' + databaseName + '''"
                let $type := "'''+ type + '''"
                let $pathSeq := "''' + pathSeq + '''"
                let $coll := "'''+ collation + '''"
                let $pos := '''+ pos + '''
                let $val := "'''+ val + '''"
                let $config := admin:get-configuration()
                let $dbid := xdmp:database($dbname)
                let $rangespec := admin:database-range-path-index($dbid,$type,$pathSeq,$coll,$pos,$val)
                let $applyConfig:= admin:database-delete-range-path-index($config, $dbid, $rangespec)
                return admin:save-configuration($applyConfig)
        '''
        EvalResultIterator result = eval.xquery(installer).eval()
        if (result.hasNext()) {
            logger.error(result.next().getString())
        }
    }

    def addIndexInfo(String entityName) {
        ObjectMapper mapper = new ObjectMapper();
        File entitiesDir = Paths.get(projectDir.toString(), "plugins", "entities").toFile();
        File prodEntityDir = Paths.get(entitiesDir.toString(), entityName).toFile();
        File destDir = Paths.get(prodEntityDir.toString(), entityName + ".entity.json").toFile();
        JsonNode entity = getJsonResource(destDir.getAbsolutePath());
        JsonNode entityNode = entity.get("definitions").get(entityName);
        ((ObjectNode) entityNode).putArray("rangeIndex").add("ProductID");
        mapper.writerWithDefaultPrettyPrinter().writeValue(destDir, entity);
    }

    def deleteFilesOnFileSystem() {
        FileUtils.forceDelete(Paths.get(projectDir, "gradle.properties").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "gradle-local.properties").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "plugins").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "src", "main", "entity-config").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "src", "main", "hub-internal-config").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "src", "main", "ml-config").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "src", "main", "ml-modules").toFile())
        FileUtils.forceDelete(Paths.get(projectDir, "src", "main", "ml-schemas").toFile())
    }

    def cleanUpProjectDir() {
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "databases",
                "staging-schemas-database-1.json"))
    }

    def setupSpec() {
        // TODO: Check for existing dhf and delete if exists
        // TODO: Deploy DHF
        XMLUnit.setIgnoreWhitespace(true)
        getPropertiesFile()

        // Initializing hubconfig
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        ctx.register(ApplicationConfig.class)
        ctx.refresh()
        _hubConfig = ctx.getBean(HubConfigImpl.class)
        _hubConfig.createProject(projectDir)
        _datahub = ctx.getBean(DataHubImpl.class)
        _hubConfig.refreshProject()

        println(_datahub.isInstalled().isInstalled())

        if(_datahub.isInstalled().isInstalled()) {
            runTask('mlUndeploy', '-Pconfirm=true')
        }
        runTask('mlDeploy')
    }

    def cleanupSpec() {
        // TODO: do mlUndeploy
    }
}