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
import java.util.logging.Logger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EndToEndTests extends TestsHelper {

    private static final Logger log = Logger.getLogger(EndToEndTests.class.getName());

    @BeforeAll
    public void init() {
        // initialize hub config
        log.info("Inside init");
        setUpSpecs();

        // Clear modules in data-hub-STAGING, data-hub-FINAL, data-hub-JOBS and data-hub-MODULES
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME, HubConfig.DEFAULT_FINAL_NAME, HubConfig.DEFAULT_JOB_NAME);

        // clear docs in flows, steps, mappings, entities directories
        deleteResourceDocs();

        // copy docs in src/test/resources to flows/steps/mappings/entities dirs
        copyRunFlowResourceDocs();

        // mlloadmodules to deploy flows, steps, mappings, entities
        BuildResult result = runTask(":5.0-end-to-end-tests:mlLoadModules");
        assert (result.task(":5.0-end-to-end-tests:mlLoadModules").getOutcome().toString().equals("SUCCESS"));

        // run mlcp flow to input documents
        result = runTask(":5.0-end-to-end-tests:importData");
        assert (result.task(":5.0-end-to-end-tests:importData").getOutcome().toString().equals("SUCCESS"));
        // assert to check if documents are deployed

    }

    @TestFactory
    public List<DynamicTest> generateRunFlowTests() {
        List<DynamicTest> tests = new ArrayList<>();
//        File flowDirectory = new File("flows");
//        File[] listOfFiles = flowDirectory.listFiles();
//        int fileCount = listOfFiles.length;

        System.out.println("Inside getrunflows tests");

        allCombos(() -> {
            tests.add(DynamicTest.dynamicTest("run flow " + "- " + "default-mapping", () -> {
                BuildResult result = runTask(":5.0-end-to-end-tests:hubRunFlow", "-PflowName=default-mapping",
                        "-PoptionsFile=src/test/resources/options/options.json");
                assert (result.task(":5.0-end-to-end-tests:hubRunFlow").getOutcome().toString().equals("SUCCESS"));
                // verify doc count
                // verify the job count
                // verify the step status
                // verify the harmonized doc

            }));
//            for (int i = 0; i < fileCount; i++) {
//                String fileName = listOfFiles[i].getName();
//
//                tests.add(DynamicTest.dynamicTest("run flow " + "- " + fileName, () -> {
//                    BuildResult result = runTask(":hubRunFlow");
//                    assert (result.task(":hubRunFlow").getOutcome().equals("SUCCESS"));
//                    // verify doc count
//                    // verify the job count
//                    // verify the step status
//                    // verify the harmonized doc
//
//                }));
//
//                tests.add(DynamicTest.dynamicTest("run flow " + "- " + "with options - " + fileName,
//                        () -> {
//                            BuildResult result = runTask(":hubRunFlow");
//                            assert (result.task(":hubRunFlow").getOutcome().equals("SUCCESS"));
//                            // verify doc count
//                            // verify the job count
//                            // verify the step status
//                            // verify the harmonized doc
//                        }));
//
//                tests.add(DynamicTest.dynamicTest("run flow " + "- " + "with options file - " + fileName,
//                        () -> {
//                            BuildResult result = runTask(":hubRunFlow");
//                            assert (result.task(":hubRunFlow").getOutcome().equals("SUCCESS"));
//                            // verify doc count
//                            // verify the job count
//                            // verify the step status
//                            // verify the harmonized doc
//                        }));
//            }
        });
        return tests;
    }
}
