package com.marklogic.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marklogic.client.FailedRequestException;
import com.marklogic.client.document.GenericDocumentManager;
import com.marklogic.client.eval.EvalResult;
import com.marklogic.client.eval.EvalResultIterator;
import com.marklogic.client.eval.ServerEvaluationCall;
import com.marklogic.hub.ApplicationConfig;
import com.marklogic.hub.HubConfig;
import com.marklogic.hub.error.DataHubConfigurationException;
import com.marklogic.hub.flow.RunFlowResponse;
import com.marklogic.hub.impl.DataHubImpl;
import com.marklogic.hub.impl.HubConfigImpl;
import com.marklogic.hub.legacy.flow.DataFormat;
import com.marklogic.hub.step.RunStepResponse;
import org.apache.commons.io.FileUtils;
import org.custommonkey.xmlunit.XMLUnit;
import org.gradle.internal.impldep.com.google.gson.Gson;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class TestsHelper {

    private static Logger logger = LoggerFactory.getLogger(TestsHelper.class);

    public String projectDir = new File("").getAbsolutePath();
    private HubConfigImpl _hubConfig;
    private HubConfigImpl _adminHubConfig;
    private DataHubImpl _dataHub;
    private DataHubImpl _adminDataHub;
    public GenericDocumentManager stagingDocMgr;
    public GenericDocumentManager finalDocMgr;
    private Properties props;
    protected String optionsPath = "src/test/resources/options";
    protected String outputOrdersPath = "src/test/resources/output/orders";

    protected void initializeProject() {
        XMLUnit.setIgnoreWhitespace(true);
        // initialize hub config
        setUpSpecs();

        // Clear modules in data-hub-STAGING, data-hub-FINAL, data-hub-JOBS
        // and data-hub-MODULES
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME,
                HubConfig.DEFAULT_FINAL_NAME, HubConfig.DEFAULT_JOB_NAME);

        // clear docs in flows, steps, mappings, entities directories
        deleteResourceDocs();

        // copy docs in src/test/resources to flows/steps/mappings/entities dirs
        copyRunFlowResourceDocs();
    }

    protected void allCombos(ComboListener listener) {
        DataFormat[] dataFormats = {DataFormat.JSON, DataFormat.XML};
//        DataFormat[] outputFormats = {DataFormat.JSON, DataFormat.XML};
        for (DataFormat dataFormat : dataFormats) {
            listener.onCombo(dataFormat.toString(), dataFormat.toString());
        }
    }

    protected void e2eFlowCombos(FlowComboListener listener) {
        boolean[] cmdLineOptions = {true, false};
        for (boolean option : cmdLineOptions) {
            listener.onCombo(option);
        }
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
            logger.error(result.next().getString());
        }
    }

    protected void deleteResourceDocs() {
        File entitiesPath = new File(Paths.get(projectDir, "entities").toString());
        File mappingsPath = new File(Paths.get(projectDir, "entities").toString());
        try {
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "flows").toString()));
            FileUtils.cleanDirectory(new File(Paths.get(projectDir, "step-definitions").toString()));
            if (entitiesPath.isFile())
                FileUtils.cleanDirectory(new File(Paths.get(projectDir, "entities").toString()));
            if (mappingsPath.isFile())
                FileUtils.cleanDirectory(new File(Paths.get(projectDir, "mappings").toString()));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected void copyRunFlowResourceDocs() {
        try {
            FileUtils.copyDirectory(new File(Paths.get(projectDir, "src/test/resources/plugins/").toString()),
                    new File(Paths.get(projectDir).toString()));
            FileUtils.copyDirectory(new File(Paths.get(projectDir, "src/test/resources/flows").toString()),
                    new File(Paths.get(projectDir, "flows").toString()));
//            FileUtils.copyDirectory(new File(Paths.get(projectDir,"src/test/resources/steps").toString()),
//                    new File(Paths.get(projectDir,"steps").toString()));
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    protected void createTemporalConfig() {
        try {
            FileUtils.copyDirectory(new File(Paths.get(projectDir, "src/test/resources/temporal-config/temporal").toString()),
                    new File(Paths.get(projectDir, "src", "main", "ml-config", "databases", "data-hub-STAGING",
                            "temporal").toString()));
            FileUtils.copyDirectory(new File(Paths.get(projectDir, "src/test/resources/temporal-config/temporal").toString()),
                    new File(Paths.get(projectDir, "src", "main", "ml-config", "databases", "temporal").toString()));

            FileUtils.copyFile(new File(Paths.get(projectDir, "src/test/resources/temporal-config/databases" +
                            "/staging-database.json").toString()),
                    new File(Paths.get(projectDir, "src/main/hub-internal-config/databases/staging-database.json").toString()));
            FileUtils.copyFile(new File(Paths.get(projectDir, "src/test/resources/temporal-config/databases" +
                            "/final-database.json").toString()),
                    new File(Paths.get(projectDir, "src/main/ml-config/databases/final-database.json").toString()));
        } catch (IOException ioe) {
            ioe.printStackTrace();
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

    protected int getBatchSizeFromOptionsFile(String filePath) {
        JsonNode fileContent = getJsonResource(filePath);
        if (fileContent.get("batchSize") != null) {
            return fileContent.get("batchSize").intValue();
        }
        return 100;
    }

    protected String getCollectionFromIdentifierFromOptionsFile(String filePath) {
        String collection = "default-ingestion";
        JsonNode fileContent = getJsonResource(filePath);
        if (fileContent.get("sourceQuery") != null) {
            String identifier = fileContent.get("sourceQuery").toString();
            String collQueryPart = identifier.split("collectionQuery")[1];
            int startIndex = collQueryPart.indexOf("'");
            int endIndex = collQueryPart.lastIndexOf("'");
            collection = collQueryPart.substring(startIndex + 1, endIndex);
        }
        return collection;
    }

    protected String getMappingVersionFromOptionsFile(String filePath) {
        JsonNode fileContent = getJsonResource(filePath);
        String version = null;
        if (fileContent.get("mapping") != null) {
            JsonNode mapping = fileContent.get("mapping");
            version = mapping.get("version") != null ? mapping.get("version").toString() : null;
        }
        return version;
    }

    protected void assertJsonEqual(String expected, String actual) {
        try {
            JSONAssert.assertEquals(expected, actual, false);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    protected void debugOutput(Document xmldoc, OutputStream os) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.transform(new DOMSource(xmldoc), new StreamResult(os));
        } catch (TransformerException e) {
            throw new DataHubConfigurationException(e);
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

    protected JsonNode getFinalOptions(String optionsFilePath, String flowPath, int stepNumber) {
        JsonNode cmdLineOptions = getJsonResource(optionsFilePath);
        JsonNode flowOptions = getJsonResource(flowPath).get("options");
        JsonNode stepOptions = getJsonResource(flowPath).get("steps").get(Integer.toString(stepNumber)).get("options");

        JsonNode finalOptions = combineOptions(stepOptions, flowOptions);
        finalOptions = combineOptions(cmdLineOptions, finalOptions);
        return finalOptions;
    }

    protected JsonNode getFinalOptions(String flowPath, int stepNumber) {
        JsonNode flowOptions = getJsonResource(flowPath).get("options");
        JsonNode stepOptions = getJsonResource(flowPath).get("steps").get(Integer.toString(stepNumber)).get("options");
        JsonNode finalOptions = combineOptions(stepOptions, flowOptions);
        return finalOptions;
    }

    private JsonNode combineOptions(JsonNode highRankOptions, JsonNode lowRankOptions) {
        Set<String> keys = getNodeKeySet(highRankOptions);
        for (String keyNode : keys) {
            if (lowRankOptions.get(keyNode) != null) {
                if (highRankOptions.get(keyNode).isObject()) {
                    combineOptions(highRankOptions.get(keyNode), lowRankOptions.get(keyNode));
                } else {
                    if (highRankOptions.get(keyNode) != null) {
                        ((ObjectNode) lowRankOptions).set(keyNode, highRankOptions.get(keyNode));
                    }
                }
            } else {
                ((ObjectNode) lowRankOptions).set(keyNode, highRankOptions.get(keyNode));
            }
        }
        return lowRankOptions;
    }

    private Set<String> getNodeKeySet(JsonNode optionsNode) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> optionsMap = mapper.convertValue(optionsNode, Map.class);
        return optionsMap.keySet();
    }

    protected boolean parseAndVerifyRunFlowStatus(String taskOutput) {
        Gson g = new Gson();
        RunFlowResponse runFlowResponse = null;
        boolean runFlowStatus = true;
        int jsonStartIndex = taskOutput.indexOf('{');
        int jsonEndIndex = taskOutput.lastIndexOf('}');
        String jsonString = taskOutput.substring(jsonStartIndex, jsonEndIndex + 1);
        runFlowResponse = g.fromJson(jsonString, RunFlowResponse.class);
        Map<String, RunStepResponse> stepResponses = runFlowResponse.getStepResponses();
        for (String stepId : stepResponses.keySet()) {
            RunStepResponse stepJob = stepResponses.get(stepId);
            if (!stepJob.isSuccess()) {
                runFlowStatus = false;
                break;
            }
        }
        return runFlowStatus;
    }

    protected String getPropertyFromRunFlowStatus(String taskOutput, String propertyName, int stepId) {
        Gson g = new Gson();
        ObjectMapper mapper = new ObjectMapper();

        RunFlowResponse runFlowResponse = null;
        int jsonStartIndex = taskOutput.indexOf('{');
        int jsonEndIndex = taskOutput.lastIndexOf('}');
        String jsonString = taskOutput.substring(jsonStartIndex, jsonEndIndex + 1);
        runFlowResponse = g.fromJson(jsonString, RunFlowResponse.class);

        Map<String, RunStepResponse> stepResponses = runFlowResponse.getStepResponses();
        RunStepResponse stepJob = stepResponses.get(Integer.toString(stepId));

        JsonNode jsonNode = mapper.convertValue(stepJob, JsonNode.class);
        return jsonNode.get(propertyName).asText();
    }

    protected JsonNode getPropertyFromArtifacts(String propertyName, String flowName, int stepNum) {
        // TODO: Adding Step Definition Artifact logic here
        JsonNode flowData = getJsonResource("flows" + "/" + flowName + ".flow.json");
        JsonNode stepData = flowData.get("steps").get(Integer.toString(stepNum));
        JsonNode flowPropertyVal = null;
        JsonNode stepPropertyVal = null;
        // check if flow artifact has property
        if (flowData.get(propertyName) != null) {
            flowPropertyVal = flowData.get(propertyName);
        }

        // check if step artifact has the property
        if (stepData.get(propertyName) != null) {
            stepPropertyVal = stepData.get(propertyName);
        }

        if (stepPropertyVal != null && stepPropertyVal.isInt() && stepPropertyVal.intValue() == 0) {
            stepPropertyVal = flowPropertyVal;
        }
        return stepPropertyVal;
    }

    protected void configureHubConfig() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(ApplicationConfig.class);
        ctx.refresh();
        _hubConfig = ctx.getBean(HubConfigImpl.class);
        _hubConfig.createProject(projectDir);
        _dataHub = ctx.getBean(DataHubImpl.class);
        _hubConfig.refreshProject();
        stagingDocMgr = _hubConfig.newStagingClient().newDocumentManager();
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
        stagingDocMgr = _hubConfig.newStagingClient().newDocumentManager();
        finalDocMgr = _adminHubConfig.newFinalClient().newDocumentManager();
    }

    public String prettyPrintJsonString(JsonNode jsonNode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(jsonNode.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return "Sorry, pretty print didn't work";
        }
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
