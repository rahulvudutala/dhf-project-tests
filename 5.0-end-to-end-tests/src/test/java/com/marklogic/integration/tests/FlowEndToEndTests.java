package com.marklogic.integration.tests;

import com.marklogic.hub.HubConfig;
import com.marklogic.utils.TestsHelper;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FlowEndToEndTests extends TestsHelper {
    @BeforeAll
    public void init() {
        initializeProject();
    }

    public void setUpDocs(String dataFormat) {
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
            String hunRunFlowTask = "";
            String flowName = "";
            String optionFilePath = "";
            if (cmdLineOptions) {
                hunRunFlowTask = ":5.0-end-to-end-tests:hubRunFlow -PflowName=" + flowName + " -PoptionsFile="
                        + optionFilePath;

            } else {

            }
            tests.add(DynamicTest.dynamicTest("", () -> {
                // get nuumber of steps in a flow
                int noOfSteps = getJsonResource("").get("steps").size();
                // run each step in a flow within a for loop
                // verify the taskoutcome to be true
            }));
        });
        return tests;
    }
}
