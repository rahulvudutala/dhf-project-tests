package com.marklogic.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.document.GenericDocumentManager;
import com.marklogic.client.eval.EvalResult;
import com.marklogic.client.eval.EvalResultIterator;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.hub.ApplicationConfig;
import com.marklogic.hub.HubConfig;
import com.marklogic.hub.impl.DataHubImpl;
import com.marklogic.hub.impl.HubConfigImpl;
import org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Paths;
import java.util.Properties;

public class TestsHelper {

    String projectDir = new File("").getAbsolutePath();
    private HubConfigImpl _hubConfig;
    private HubConfigImpl _adminHubConfig;
    private DataHubImpl _dataHub;
    private DataHubImpl _adminDataHub;
    public GenericDocumentManager finalDocMgr;
    private Properties props;
    protected String optionsPath = "src/test/resources/options";

    protected void allCombos(ComboListener listener) {
        listener.onCombo();
    }

    protected BuildResult runTask(String... task) {
        return GradleRunner.create()
                .withProjectDir(new File(projectDir))
                .withArguments(task)
                .withDebug(true)
                .build();
    }

    protected void clearDatabases(String... databases) {
        ServerEvaluationCall eval = _adminHubConfig.newStagingClient().newServerEval();
        String installer =
                "declare variable $databases external;\n" +
                        "for $database in fn:tokenize($databases, \",\")\n" +
                        "return\n" +
                        "  xdmp:eval('\n" +
                        "    cts:uris() ! xdmp:document-delete(.)\n" +
                        "  ',\n" +
                        "  (),\n" +
                        "  map:entry(\"database\", xdmp:database($database))\n" +
                        "  )";
        eval.addVariable("databases", String.join(",", databases));
        EvalResultIterator result = eval.xquery(installer).eval();
        if (result.hasNext()) {
//            logger.error(result.next().getString());
            System.out.println(result.next().getString());
        }
    }

    protected void deleteResourceDocs() {
        try {
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "flows").toString()));
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "steps").toString()));
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "plugins").toString()));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected void copyRunFlowResourceDocs() {
        try {
            System.out.println(projectDir);
            FileUtils.copyDirectory(new File(Paths.get("src/test/resources/plugins").toString()),
                    new File(Paths.get("plugins").toString()));
//            FileUtils.copyDirectory(new File(Paths.get(projectDir,"src/test/resources/flows").toString()),
//                    new File(Paths.get(projectDir,"flows").toString()));
//            FileUtils.copyDirectory(new File(Paths.get(projectDir,"src/test/resources/steps").toString()),
//                    new File(Paths.get(projectDir,"steps").toString()));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected int getDocCount(String database, String collection) {
        int count = 0;
        String collectionName = "";
        if (collection != null) {
            collectionName = "'" + collection + "'";
        }
        EvalResultIterator resultItr = runInDatabase("xdmp:estimate(fn:collection(" + collectionName + "))", database);
        if (resultItr == null || !resultItr.hasNext()) {
            return count;
        }
        EvalResult res = resultItr.next();
        count = Math.toIntExact((long) res.getNumber());
        return count;
    }

    protected EvalResultIterator runInDatabase(String query, String databaseName) {
        ServerEvaluationCall eval;
        switch (databaseName) {
            case HubConfig.DEFAULT_STAGING_NAME:
                eval = _hubConfig.newStagingClient().newServerEval();
                break;
            case HubConfig.DEFAULT_FINAL_NAME:
                eval = _hubConfig.newFinalClient().newServerEval();
                break;
            case HubConfig.DEFAULT_MODULES_DB_NAME:
                eval = _hubConfig.newModulesDbClient().newServerEval();
                break;
            case HubConfig.DEFAULT_JOB_NAME:
                eval = _hubConfig.newJobDbClient().newServerEval();
                break;
            default:
                eval = _hubConfig.newStagingClient().newServerEval();
                break;
        }
        try {
            return eval.xquery(query).eval();
        } catch (FailedRequestException e) {
            e.printStackTrace();
            throw e;
        }
    }

    protected JsonNode getJsonResource(String filePath) {
        try {
            InputStream jsonDataStream = new FileInputStream(new File(filePath));
            ObjectMapper jsonDataMapper = new ObjectMapper();
            return jsonDataMapper.readTree(jsonDataStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String getOutputFormat(String filePath) {
        String outputFormat = "json";
        JsonNode fileContent = getJsonResource(filePath);
        if (fileContent.get("outputFormat") != null && fileContent.get("outputFormat").equals("xml")) {
            outputFormat = "xml";
        }
        return outputFormat;
    }

    protected String getCollectionFromIdentifier(String filePath) {
        String collection = "default-ingest";
        JsonNode fileContent = getJsonResource(filePath);
        if (fileContent.get("identifier") != null) {
            String identifier = fileContent.get("identifier").toString();
            String collQueryPart = identifier.split("collectionQuery")[1];
            int startIndex = collQueryPart.indexOf("'");
            int endIndex = collQueryPart.lastIndexOf("'");
            collection = collQueryPart.substring(startIndex + 1, endIndex);
        }
        return collection;
    }

    protected void assertJsonEqual(String expected, String actual) {
        try {
            JSONAssert.assertEquals(expected, actual, false);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    protected Document getXmlResource(String resourceName) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(resourceName);
            return getXmlFromInputStream(inputStream);
        } catch (FileNotFoundException fnf) {
            fnf.printStackTrace();
        }
        return null;
    }

    protected Document getXmlFromInputStream(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringElementContentWhitespace(true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();

            return builder.parse(inputStream);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }


    protected void configureHubConfig() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(ApplicationConfig.class);
        ctx.refresh();
        _hubConfig = ctx.getBean(HubConfigImpl.class);
        _hubConfig.createProject(projectDir);
        _dataHub = ctx.getBean(DataHubImpl.class);
        _hubConfig.refreshProject();
        finalDocMgr = _hubConfig.newFinalClient().newDocumentManager();
    }

    protected void configureAdminHubConfig() {
        AnnotationConfigApplicationContext ctx1 = new AnnotationConfigApplicationContext();
        ctx1.register(ApplicationConfig.class);
        ctx1.refresh();
        _adminHubConfig = ctx1.getBean(HubConfigImpl.class);
        _adminHubConfig.createProject(projectDir);
        _adminHubConfig.setMlUsername(getPropertyFromPropertiesFile("mlSecurityUsername"));
        _adminHubConfig.setMlPassword(getPropertyFromPropertiesFile("mlSecurityPassword"));
        _adminDataHub = ctx1.getBean(DataHubImpl.class);
        _adminHubConfig.refreshProject();
        _adminDataHub.wireClient();
        finalDocMgr = _adminHubConfig.newFinalClient().newDocumentManager();
    }

    protected void loadPropertiesFile() {
        props = new Properties();
        try {
            props.load(new FileInputStream("gradle.properties"));
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected String getPropertyFromPropertiesFile(String key) {
        return props.getProperty(key);
    }

    protected void setUpSpecs() {
        loadPropertiesFile();
        configureHubConfig();
        configureAdminHubConfig();
    }


    public HubConfigImpl hubConfig() {
        return _hubConfig;
    }

    public HubConfigImpl adminHubConfig() {
        return _adminHubConfig;
    }
}
