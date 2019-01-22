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
import com.marklogic.client.DatabaseClient
import com.marklogic.client.FailedRequestException
import com.marklogic.client.document.DocumentManager
import com.marklogic.client.document.DocumentPage
import com.marklogic.client.document.DocumentRecord
import com.marklogic.client.eval.EvalResult
import com.marklogic.client.eval.EvalResultIterator
import com.marklogic.client.eval.ServerEvaluationCall
import com.marklogic.client.io.DocumentMetadataHandle
import com.marklogic.client.io.Format
import com.marklogic.client.io.InputStreamHandle
import com.marklogic.client.io.JacksonParserHandle
import com.marklogic.client.io.StringHandle
import com.marklogic.client.query.QueryDefinition
import com.marklogic.client.query.StructuredQueryBuilder
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

    protected static final String projectDir = new File("").getAbsolutePath()
    static File buildFile
    static File propertiesFile
    static Properties p

    private ManageClient _manageClient;
    private DatabaseManager _databaseManager;

    static public HubConfigImpl _hubConfig
    static public HubConfigImpl _adminhubConfig
    static private DataHubImpl _datahub
    static private DataHubImpl _admindatahub

    static final int hubCoreModCount = 109
    static final protected Logger logger = LoggerFactory.getLogger(BaseTest.class)
    static String environmentName

    public HubConfigImpl hubConfig() {
        return _hubConfig
    }

    public HubConfigImpl adminHubConfig() {
        return _adminhubConfig
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

    static void clearDatabases(String... databases) {
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
        return getDocCount(getPropertyFromPropertiesFile("mlStagingDbName"), collection)
    }

    static int getFinalDocCount() {
        return getFinalDocCount(null)
    }

    static int getFinalDocCount(String collection) {
        return getDocCount(getPropertyFromPropertiesFile("mlFinalDbName"), collection)
    }

    static int getModulesDocCount() {
        return getModulesDocCount(null)
    }

    static int getModulesDocCount(String collection) {
        return getDocCount(getPropertyFromPropertiesFile("mlModulesDbName"), collection)
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
            case getPropertyFromPropertiesFile("mlStagingDbName"):
                eval = _hubConfig.newStagingClient().newServerEval()
                break
            case getPropertyFromPropertiesFile("mlFinalDbName"):
                eval = _hubConfig.newFinalClient().newServerEval()
                _hubConfig.newFinalClient()
                break
            case getPropertyFromPropertiesFile("mlModulesDbName"):
                eval = _adminhubConfig.newModulesDbClient().newServerEval()
                break
            case getPropertyFromPropertiesFile("mlJobDbName"):
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

    public int getCustomDbRangePathIndexSize(String dbName) {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(dbName)
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    public int getStagingRangePathIndexSize() {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(_hubConfig.getDbName(DatabaseKind.STAGING))
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    public int getFinalRangePathIndexSize() {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(_hubConfig.getDbName(DatabaseKind.FINAL))
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    public int getJobsRangePathIndexSize() {
        Fragment databseFragment = getDatabaseManager().getPropertiesAsXml(_hubConfig.getDbName(DatabaseKind.JOB))
        return databseFragment.getElementValues("//m:range-path-index").size()
    }

    void getPropertiesFile() {
        propertiesFile = new File(Paths.get(".").resolve("gradle.properties").toString())
    }

    static String getPropertyFromPropertiesFile(String key) {
        return p.getProperty(key)
    }

    static void loadPropertiesFile() {
        p = new Properties()
        p.load(new FileInputStream("gradle.properties"))
    }

    static void updatePropertiesFile(String key, String value) {
        FileOutputStream out = new FileOutputStream("gradle.properties");
        p.setProperty(key, value);
        p.store(out, null);
        out.close();
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

    def cleanUpProjectDir() {
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
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "security",
                "privileges", "privilege-2.json"))
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
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "query-rolesets", "pii-reader.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "security",
                "query-rolesets", "manage-reader.json"))

        // cleaning trigger files
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "triggers",
                "my-trigger-ml-config.json"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.HUB_CONFIG_DIR, "triggers",
                "my-trigger.json"))
        FileUtils.deleteDirectory(Paths.get(projectDir, HubConfig.USER_CONFIG_DIR, "databases",
                getPropertyFromPropertiesFile("mlFinalTriggersDbName")).toFile())

        // cleaning schemas files
        Files.deleteIfExists(Paths.get(projectDir, "src", "main", "ml-schemas", "ml-sch.xsd"))

        // cleaning install modules files
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.ENTITY_CONFIG_DIR, "staging-entity-options.xml"))
        Files.deleteIfExists(Paths.get(projectDir, HubConfig.ENTITY_CONFIG_DIR, "final-entity-options.xml"))
        FileUtils.deleteDirectory(Paths.get(projectDir, "plugins", "entities").toFile())
        FileUtils.deleteDirectory(Paths.get(projectDir, "src", "main", "ml-modules", "ext").toFile())

        // cleaning second modules config directories
        FileUtils.deleteDirectory(Paths.get(projectDir, "src/test/ml-config").toFile())
        FileUtils.deleteDirectory(Paths.get(projectDir, "src/test/ml-modules").toFile())
    }

    void configureHubConfig() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        ctx.register(ApplicationConfig.class)
        ctx.refresh()
        _hubConfig = ctx.getBean(HubConfigImpl.class)
        _hubConfig.createProject(projectDir)
        _datahub = ctx.getBean(DataHubImpl.class)
        _hubConfig.refreshProject()
    }

    void configureAdminHubConfig() {
        AnnotationConfigApplicationContext ctx1 = new AnnotationConfigApplicationContext()
        ctx1.register(ApplicationConfig.class)
        ctx1.refresh()
        _adminhubConfig = ctx1.getBean(HubConfigImpl.class)
        _adminhubConfig.createProject(projectDir)
        _adminhubConfig.setMlUsername(getPropertyFromPropertiesFile("mlSecurityUsername"))
        _adminhubConfig.setMlPassword(getPropertyFromPropertiesFile("mlSecurityPassword"))
        _admindatahub = ctx1.getBean(DataHubImpl.class)
        _adminhubConfig.refreshProject()
        _admindatahub.wireClient()
    }

    def setupSpec() {
        XMLUnit.setIgnoreWhitespace(true)
        environmentName = System.getProperty("environmentName")
        if(environmentName.equals("custom")) {
            File gradleProps = Paths.get(projectDir.toString(), "gradle.properties").toFile()
            copyResourceToFile("gradle-custom.properties", gradleProps)
        }
        
        loadPropertiesFile()
        getPropertiesFile()

        // Initializing hubconfig
        configureHubConfig()
        configureAdminHubConfig()

        try {
            if(_datahub.isInstalled().isInstalled()) {
                runTask('mlUndeploy', '-Pconfirm=true')
            }
        } catch(DataHubSecurityNotInstalledException e) {
            logger.info("No Datahub is installed")
        }
        runTask('mlDeploy')
    }

    def cleanupSpec() {
        runTask('mlUndeploy', '-Pconfirm=true')
        cleanUpProjectDir()
        File gradleProps = Paths.get(projectDir.toString(), "gradle.properties").toFile()
        copyResourceToFile("gradle_properties", gradleProps)
        runTask('mlDeploy')
    }
}