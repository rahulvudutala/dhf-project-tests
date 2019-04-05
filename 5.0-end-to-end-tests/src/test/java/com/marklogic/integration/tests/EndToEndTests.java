package com.marklogic.integration.tests;

import com.marklogic.client.io.StringHandle;
import com.marklogic.hub.HubConfig;
import com.marklogic.hub.flow.RunFlowResponse;
import com.marklogic.hub.job.Job;
import com.marklogic.utils.TestsHelper;
import org.gradle.internal.impldep.com.google.gson.Gson;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EndToEndTests extends TestsHelper {

    private static final Logger log =
            Logger.getLogger(EndToEndTests.class.getName());

    @BeforeAll
    public void init() {
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

    public void setUpDocs() {
        System.out.println("inside setUpDocs ");
        // clear documents to make assertion easy for each test
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME, HubConfig.DEFAULT_FINAL_NAME, HubConfig.DEFAULT_JOB_NAME);

        // Load user modules into the datahub
        BuildResult result = runTask(":5.0-end-to-end-tests:mlLoadModules");
        assert (result.task(":5.0-end-to-end-tests:mlLoadModules").getOutcome().toString().equals("SUCCESS"));

        // run mlcp flow to ingest documents
        result = runTask(":5.0-end-to-end-tests:importData");
        assert (result.task(":5.0-end-to-end-tests:importData").getOutcome().toString().equals("SUCCESS"));
    }

    @TestFactory
    public List<DynamicTest> generateRunFlowTests() {
        List<DynamicTest> tests = new ArrayList<>();

        // default-mapping flows
        allCombos(() -> {
            String flowName = "default-mapping", flowType = "default-mapping", testType = "positive";
            File optionDir = new File(optionsPath + "/" + flowType + "/" + testType);
            File[] listOfFiles = optionDir.listFiles();

            File inputXmlDocsDir = new File("input/orders/xml");
            File[] inputXmlFiles = inputXmlDocsDir.listFiles();
            String inputXmlFileName = inputXmlFiles[0].getName();

            for (File optionsFile : listOfFiles) {
                String optionsFileName = optionsFile.getName();
                tests.add(DynamicTest.dynamicTest("run flow " + "- " + flowName + "with options: " + optionsFileName,
                        () -> {
                            setUpDocs();
                            int finalDbDocsCount = getDocCount("data-hub-FINAL", null);
                            BuildResult result = runTask(":5.0-end-to-end-tests" + ":hubRunFlow",
                                    "-PflowName=" + flowName,
                                    "-PoptionsFile=" + optionsPath + "/" + flowType + "/" + testType + "/" + optionsFileName);
                            BuildTask taskResult = result.task(":5.0-end-to-end-tests:hubRunFlow");

                            // verify the taskoutcome to be true
                            assert (taskResult.getOutcome().toString().equals("SUCCESS"));

                            // verify the step status
                            String taskOutput = result.getOutput();
                            boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                            assert (runFlowStatus == true);

                            // verify the docs count
                            int docsInIngestColl = getDocCount("data-hub-FINAL", "default-ingest");
                            assert(finalDbDocsCount + docsInIngestColl == getDocCount("data-hub-FINAL", null));
                            System.out.println(getDocCount("data-hub-FINAL", null));

                            // verify the job and provenance count
                            System.out.println(getDocCount("data-hub-JOBS", null));

                            // verify the harmonized doc
                            if (docsInIngestColl != 0) {
                                getAndVerifyDocumentsFromDatabase(inputXmlFileName, optionsFileName);
                            }

                            // TODO: verify the provenance doc
                        }));
            }


            String flowNameN = "default-mapping", flowTypeN = "default-mapping", testTypeN = "negative";
            File optionDirN = new File(optionsPath + "/" + flowTypeN + "/" + testTypeN);
            File[] listOfFilesN = optionDirN.listFiles();

            for (File optionsFileN : listOfFilesN) {
                String optionsFileNameN = optionsFileN.getName();
                tests.add(DynamicTest.dynamicTest("run flow " + "- " + flowNameN + "with options: " + optionsFileNameN, () -> {
                    setUpDocs();
                    BuildResult resultN = runTask(":5.0-end-to-end-tests" + ":hubRunFlow", "-PflowName=" + flowNameN,
                            "-PoptionsFile=" + optionsPath + "/" + flowTypeN + "/" + testTypeN + "/" + optionsFileNameN);
                    assert (resultN.task(":5.0-end-to-end-tests:hubRunFlow").getOutcome().toString().equals("SUCCESS"));

                    // verify the step status
                    String taskOutput = resultN.getOutput();
                    boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                    assert (runFlowStatus == false);

                    // verify doc count
                    assert (0 == getDocCount("data-hub-FINAL", "default-ingest"));
                    System.out.println(getDocCount("data-hub-FINAL", null));

                    // verify the job count
                    System.out.println(getDocCount("data-hub-JOBS", null));
                }));
            }
        });
        return tests;
    }

    private boolean parseAndVerifyRunFlowStatus(String taskOutput) {
        System.out.println(taskOutput);
        Gson g = new Gson();
        RunFlowResponse runFlowResponse = null;
        boolean runFlowStatus = true;
        int jsonStartIndex = taskOutput.indexOf('{');
        int jsonEndIndex = taskOutput.lastIndexOf('}');
        String jsonString = taskOutput.substring(jsonStartIndex, jsonEndIndex + 1);
//        System.out.println(jsonString);
        runFlowResponse = g.fromJson(jsonString, RunFlowResponse.class);
//        runFlowResponse = new ObjectMapper().readValue(jsonString, RunFlowResponse.class);
        Map<String, Job> stepResponses = runFlowResponse.getStepResponses();
        for (String stepId : stepResponses.keySet()) {
            Job stepJob = stepResponses.get(stepId);
            if (!stepJob.isSuccess()) {
                runFlowStatus = false;
                break;
            }
        }
        return runFlowStatus;
    }

    private void getAndVerifyDocumentsFromDatabase(String docName, String optionsFile) {
        System.out.println(docName);
        String actual = finalDocMgr.read(docName).next().getContent(new StringHandle()).get();
//        Document actual = finalDocMgr.read(docName).next().getContent(new DOMHandle()).get();
        String expected = getJsonResource("src/test/resources/output/orders/10248.json");
        assertJsonEqual(expected, actual, true);
        System.out.println(actual);
        System.out.println(expected);
    }
}
