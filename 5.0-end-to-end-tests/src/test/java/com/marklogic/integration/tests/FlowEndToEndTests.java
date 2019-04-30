package com.marklogic.integration.tests;

import com.marklogic.hub.HubConfig;
import com.marklogic.hub.step.StepDefinition;
import com.marklogic.utils.TestsHelper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
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
        e2eFlowCombos((cmdLineOptions, dataFormat, outputFormat) -> {
            String flowType = "user-flows", testType = "positive";

            // get flows path
            File flowDir = new File("flows");
            File[] flowFiles = flowDir.listFiles();
            // get options path
            File optionDir = new File(optionsPath + "/" + dataFormat + "/" + flowType);
            File[] optionFiles = optionDir.listFiles();

            for (File flow : flowFiles) {
                String flowFileName = flow.getName();
                String flowName = flowFileName.split("\\.")[0];
                if (cmdLineOptions) {
                    for (File options : optionFiles) {
                        String optionFilePath = optionDir.toString().concat("/").concat(options.getName());
                        tests.add(DynamicTest.dynamicTest(flowName+dataFormat, () -> {
                            setUpDocs();
                            // get number of steps in a flow
                            int noOfSteps = getJsonResource(flowDir.getPath() + "/" + flowFileName).get("steps").size();
                            // run each step in a flow within a for loop
                            for (int i = 1; i <= noOfSteps; i++) {
                                clearDatabases(HubConfig.DEFAULT_JOB_NAME);
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
                                verifyJobDocumentsForStep(taskOutput, "stepDefinitionType", i);

                                // verify the ingested/mapped/mastered doc
                                verifyDocsForStep(taskOutput, "stepDefinitionType", i);
                            }
                        }));
                    }
                } else {
                    tests.add(DynamicTest.dynamicTest(flow.getName()+dataFormat+"nooptns", () -> {
                        setUpDocs();
                        // get number of steps in a flow
                        int noOfSteps = getJsonResource(flowDir.getPath() + "/" + flowFileName).get("steps").size();
                        // run each step in a flow within a for loop
                        for (int i = 1; i <= noOfSteps; i++) {
                            clearDatabases(HubConfig.DEFAULT_JOB_NAME);
                            BuildResult result = runTask(":5.0-end-to-end-tests:hubRunFlow",
                                    "-PflowName=" + flowName, "-Psteps=" + i);
                            BuildTask taskResult = result.task(":5.0-end-to-end-tests:hubRunFlow");

                            // verify the taskoutcome to be true
                            assert (taskResult.getOutcome().toString().equals("SUCCESS"));

                            // verify the step status
                            String taskOutput = result.getOutput();
                            boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                            assert (runFlowStatus == true);

                            // verify the the job count

                            // verify the ingested/mapped/mastered doc
                            verifyDocsForStep(taskOutput, "stepDefinitionType", i);
                        }
                    }));
                }
            }
        });
        return tests;
    }

    private void verifyDocsForStep(String taskOutput, String stepDefType, int stepId) {
        String propertyVal = getPropertyFromRunFlowStatus(taskOutput, "stepDefinitionType", stepId);
        if(propertyVal.equals(StepDefinition.StepDefinitionType.INGESTION.toString())) {
            System.out.println("assert ingest");
        } else if(propertyVal.equals(StepDefinition.StepDefinitionType.MAPPING.toString())) {
            System.out.println("assert map");
        } else if(propertyVal.equals(StepDefinition.StepDefinitionType.MASTERING.toString())) {
            System.out.println("assert map");
        }
    }

    private void verifyJobDocumentsForStep(String taskOutput, String stepDefType, int stepId) {
        String propertyVal = getPropertyFromRunFlowStatus(taskOutput, "stepDefinitionType", stepId);
        if(propertyVal.equals(StepDefinition.StepDefinitionType.INGESTION.toString())) {
            System.out.println("ingest docs count");
        } else if(propertyVal.equals(StepDefinition.StepDefinitionType.MAPPING.toString())) {
            System.out.println("map docs count");
        } else if(propertyVal.equals(StepDefinition.StepDefinitionType.MASTERING.toString())) {
            System.out.println("master docs count");
        }
    }
}
