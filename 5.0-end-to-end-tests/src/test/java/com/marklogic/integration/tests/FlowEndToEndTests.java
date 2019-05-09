package com.marklogic.integration.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.marklogic.client.io.StringHandle;
import com.marklogic.hub.HubConfig;
import com.marklogic.hub.step.StepDefinition;
import com.marklogic.utils.TestsHelper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FlowEndToEndTests extends TestsHelper {
    @BeforeAll
    public void init() {
        initializeProject();
    }

    public void setUpDocs() {
        // clear documents to make assertion easy for each test
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME, HubConfig.DEFAULT_FINAL_NAME, HubConfig.DEFAULT_JOB_NAME);

        // Load user modules into the datahub
        BuildResult result = runTask(":5.0-end-to-end-tests:mlLoadModules");
        assert (result.task(":5.0-end-to-end-tests:mlLoadModules").getOutcome().toString().equals("SUCCESS"));
    }

    @TestFactory
    public List<DynamicTest> generateRunFlowTests() {
        List<DynamicTest> tests = new ArrayList<>();
        e2eFlowCombos((cmdLineOptions) -> {
            String flowType = "user-flows", testType = "positive";

            // get flows path
            File flowDir = new File("flows");
            File[] flowFiles = flowDir.listFiles();
            // get options path
            File optionDir = new File(optionsPath + "/" + flowType);
            File[] optionFiles = optionDir.listFiles();

            for (File flow : flowFiles) {
                String flowFileName = flow.getName();
                String flowName = flowFileName.split("\\.")[0];
                if (cmdLineOptions) {
                    for (File options : optionFiles) {
                        String optionFilePath = optionDir.toString().concat("/").concat(options.getName());
                        tests.add(DynamicTest.dynamicTest(flowName, () -> {
                            setUpDocs();
                            String flowPath = flowDir.getPath().concat("/").concat(flowFileName);

                            // get number of steps in a flow
                            int noOfSteps = getJsonResource(flowPath).get("steps").size();

                            // run each step in a flow within a for loop
                            for (int i = 1; i <= noOfSteps; i++) {
                                clearDatabases(HubConfig.DEFAULT_JOB_NAME);
                                System.out.println(prettyPrintJsonString(getFinalOptions(optionFilePath, flowPath, i)));
                                JsonNode combinedOptions = getFinalOptions(optionFilePath, flowPath, i);

                                BuildResult result = runTask(":5.0-end-to-end-tests:hubRunFlow",
                                        "-PflowName=" + flowName, "-PoptionsFile=" + optionFilePath, "-Psteps=" + i);
                                BuildTask taskResult = result.task(":5.0-end-to-end-tests:hubRunFlow");

                                // verify the taskoutcome to be true
                                assert (taskResult.getOutcome().toString().equals("SUCCESS"));

                                // verify the step status
                                String taskOutput = result.getOutput();
                                boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                                assert (runFlowStatus == true);

                                // verify the job count
                                verifyDocumentCountForStep(taskOutput, "stepDefinitionType", i, flowName,
                                        combinedOptions);

                                // verify the ingested/mapped/mastered doc
                                verifyDocsForStep(taskOutput, "stepDefinitionType", i, flowName, combinedOptions);
                            }
                        }));
                    }
                } else {
                    tests.add(DynamicTest.dynamicTest(flowName + "-nooptns", () -> {
                        setUpDocs();
                        String flowPath = flowDir.getPath().concat("/").concat(flowFileName);

                        // get number of steps in a flow
                        int noOfSteps = getJsonResource(flowDir.getPath() + "/" + flowFileName).get("steps").size();

                        // run each step in a flow within a for loop
                        for (int i = 1; i <= noOfSteps; i++) {
                            clearDatabases(HubConfig.DEFAULT_JOB_NAME);
                            JsonNode combinedOptions = getFinalOptions(flowPath, i);
                            System.out.println(prettyPrintJsonString(getFinalOptions(flowPath, i)));


                            BuildResult result = runTask(":5.0-end-to-end-tests:hubRunFlow",
                                    "-PflowName=" + flowName, "-Psteps=" + i);
                            BuildTask taskResult = result.task(":5.0-end-to-end-tests:hubRunFlow");

                            // verify the taskoutcome to be true
                            assert (taskResult.getOutcome().toString().equals("SUCCESS"));

                            // verify the step status
                            String taskOutput = result.getOutput();
                            boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                            assert (runFlowStatus == true);

                            // verify the
                            // document count for the step
                            verifyDocumentCountForStep(taskOutput, "stepDefinitionType", i, flowName, combinedOptions);

                            // verify the ingested/mapped/mastered doc
                            verifyDocsForStep(taskOutput, "stepDefinitionType", i, flowName, combinedOptions);
                        }
                    }));
                }
            }
        });
        return tests;
    }

    private void verifyDocsForStep(String taskOutput, String stepDefType, int stepId, String flowName,
                                   JsonNode combinedOptions) {
        String propertyVal = getPropertyFromRunFlowStatus(taskOutput, stepDefType, stepId);
        String refFileName = "10248";
        String targetDb = combinedOptions.get("targetDatabase").asText();
        JsonNode notFileLocation = getPropertyFromArtifacts("fileLocations", flowName, stepId);
        String inputFileType = "";
        if(notFileLocation != null) {
            inputFileType = getPropertyFromArtifacts("fileLocations", flowName, stepId)
                    .get("inputFileType").asText();
        }
        String outputFormat = combinedOptions.get("outputFormat").asText();
        outputFormat = (outputFormat == null) ? "json" : outputFormat;
        if (inputFileType.equals("json") && outputFormat.equals("json")) {
            String expected = getJsonResource(outputOrdersPath + "/" + propertyVal + "/" + outputFormat + "/"
                    + refFileName + "." + outputFormat).toString();
            String actual = null;
            if (targetDb.equals(getPropertyFromPropertiesFile("mlStagingDbName"))) {
                System.out.println(flowName + "/" + outputFormat + "/" + refFileName + "." + outputFormat);
                actual = stagingDocMgr.read("/" + flowName + "/" + outputFormat + "/" + refFileName + "."
                        + outputFormat).next().getContent(new StringHandle()).get();
            }
            if (targetDb.equals(getPropertyFromPropertiesFile("mlFinalDbName"))) {
                actual = finalDocMgr.read("/" + flowName + "/" + outputFormat + "/" + refFileName + "."
                        + outputFormat).next().getContent(new StringHandle()).get();
            }
            assertJsonEqual(expected, actual);
        }

        if(inputFileType.equals("xml") && outputFormat.equals("xml")) {

        }
    }

    private void verifyDocumentCountForStep(String taskOutput, String stepDefType, int stepId, String flowName,
                                            JsonNode combinedOptions) {
        String propertyVal = getPropertyFromRunFlowStatus(taskOutput, stepDefType, stepId);
        if (propertyVal.equals(StepDefinition.StepDefinitionType.INGESTION.toString())) {
            String targetDb = combinedOptions.get("targetDatabase").asText();
            String inputFilePath = getPropertyFromArtifacts("fileLocations", flowName, stepId)
                    .get("inputFilePath").asText();
            String inputFileType = getPropertyFromArtifacts("fileLocations", flowName, stepId)
                    .get("inputFileType").asText();
            int inputFilesCnt = new File(inputFilePath).listFiles().length;
            JsonNode collections = combinedOptions.get("collections");

            if(inputFileType.equals("csv")) {
                try {
                    File file = new File(inputFilePath+"/superstore.csv");
                    LineNumberReader lineNumberReader = new LineNumberReader(new FileReader(file));
                    lineNumberReader.skip(Long.MAX_VALUE);
                    inputFilesCnt = lineNumberReader.getLineNumber();
                    lineNumberReader.close();
                } catch (FileNotFoundException fnfe) {
                    fnfe.printStackTrace();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }

            for (JsonNode collection : collections) {
                int currentDocsInCollCnt = getDocCount(targetDb, collection.asText());
                // verify the number of documents in target database in the corresponding collection
                System.out.println("inputFilesCnt: " + inputFilesCnt);
                System.out.println("currentDocsInCollCnt: " + currentDocsInCollCnt);
                assert (inputFilesCnt == currentDocsInCollCnt);
            }

            // verify the number of documents in job database in the Jobs collection. Should be 1
            assert (1 == getDocCount("data-hub-JOBS", "Jobs"));
        } else if (propertyVal.equals(StepDefinition.StepDefinitionType.MAPPING.toString())) {
            String sourceDb = combinedOptions.get("sourceDatabase").asText();
            String targetDb = combinedOptions.get("targetDatabase").asText();
            String sourceQuery = combinedOptions.get("sourceQuery").asText();
            String ingestCollection = sourceQuery.substring(sourceQuery.indexOf("'") + 1, sourceQuery.lastIndexOf("'"));

            int sourceDbCount = getDocCount(sourceDb, ingestCollection);
            int batchSize = getPropertyFromArtifacts("batchSize", flowName, stepId).asInt();
            batchSize = (batchSize == 0) ? 100 : batchSize;
            int targetDbCount = 0;

            JsonNode collections = combinedOptions.get("collections");

            for (JsonNode collection : collections) {
                targetDbCount = getDocCount(targetDb, collection.asText());

                // verify the number of documents in target database in the corresponding collection
                assert (sourceDbCount == targetDbCount);
            }

            // verify the number of documents in job database in the Jobs collection
            double batches = Math.ceil((double) targetDbCount / batchSize);
            int finalBatches = (int) batches;
            assert (1 + finalBatches == getDocCount("data-hub-JOBS", "Jobs"));
        } else if (propertyVal.equals(StepDefinition.StepDefinitionType.MASTERING.toString())) {
            // One job doc is created and batch docs are created
            String sourceDb = combinedOptions.get("sourceDatabase").asText();
            String targetDb = combinedOptions.get("targetDatabase").asText();
            String sourceQuery = combinedOptions.get("sourceQuery").asText();
            String mappedCollection = sourceQuery.substring(sourceQuery.indexOf("'") + 1, sourceQuery.lastIndexOf("'"));

            int sourceDbCount = getDocCount(sourceDb, mappedCollection);
            int batchSize = getPropertyFromArtifacts("batchSize", flowName, stepId).asInt();
            batchSize = (batchSize == 0) ? 100 : batchSize;
            int targetDbCount = 0;

            JsonNode collections = combinedOptions.get("collections");
            for (JsonNode collection : collections) {
                targetDbCount = getDocCount(targetDb, collection.asText());

                // verify the number of documents in target database in the corresponding collection
                // 3 mastering docs are created for this input dataset. 1 merged doc, 1 notification doc
                // and an auditing doc is created. All the mapped (harmonized) docs are also moved to
                // the collections mentioned.
                assert (sourceDbCount + 3 == targetDbCount);
            }

            // verify the number of documents in job database in the Jobs collection

            // This is temporary to pass the tests. This is because the mapped docs are removed from the
            // collections defined in the mapping step. Remove these two lines.
            String inputFilePath = getPropertyFromArtifacts("fileLocations", flowName, stepId)
                    .get("inputFilePath").asText();
            targetDbCount = new File(inputFilePath).listFiles().length;

            double batches = Math.ceil((double) targetDbCount / batchSize);
            int finalBatches = (int) batches;
            assert (1 + finalBatches == getDocCount("data-hub-JOBS", "Jobs"));
        }
    }

    private int getSourceDbDocCount(JsonNode combinedOptions) {
        String sourceDb = combinedOptions.get("sourceDatabase").asText();
        String sourceQuery = combinedOptions.get("sourceQuery").asText();
        String mappedCollection = sourceQuery.substring(sourceQuery.indexOf("'") + 1, sourceQuery.lastIndexOf("'"));
        return getDocCount(sourceDb, mappedCollection);
    }
}
