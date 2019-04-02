package com.marklogic.integration.tests;

import com.marklogic.hub.HubConfig;
import com.marklogic.utils.TestsHelper;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EndToEndTests extends TestsHelper {

    @BeforeAll
    public void init() {
        // initialize hub config
        setUpSpecs();

        // Clear modules in data-hub-STAGING, data-hub-FINAL, data-hub-JOBS and data-hub-MODULES
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME, HubConfig.DEFAULT_FINAL_NAME, HubConfig.DEFAULT_JOB_NAME);

        // clear docs in flows, steps, mappings, entities directories
        deleteResourceDocs();

        // copy docs in src/test/resources to flows/steps/mappings/entities dirs
        copyRunFlowResourceDocs();

        // mlloadmodules to deploy flows, steps, mappings, entities
        runTask(":mlLoadModules");

        // assert to check if documents are deployed
        
    }

    @TestFactory
    public List<DynamicTest> generateRunFlowTests() {
        List<DynamicTest> tests = new ArrayList<>();
        File flowDirectory = new File("src/test/resources/input/flow");
        File[] listOfFiles = flowDirectory.listFiles();
        int fileCount = listOfFiles.length;

        allCombos(() -> {
            for (int i = 0; i < fileCount; i++) {
                String fileName = listOfFiles[i].getName();

                tests.add(DynamicTest.dynamicTest("run flow " + "- " + fileName, () -> {
                    BuildResult result = runTask(":hubRunFlow");
                    assert (result.task(":hubRunFlow").getOutcome().equals("SUCCESS"));
                    // verify doc count
                    // verify the job count
                    // verify the step status
                    // verify the harmonized doc

                }));

                tests.add(DynamicTest.dynamicTest("run flow " + "- " + "with options - " + fileName,
                        () -> {
                            BuildResult result = runTask(":hubRunFlow");
                            assert (result.task(":hubRunFlow").getOutcome().equals("SUCCESS"));
                            // verify doc count
                            // verify the job count
                            // verify the step status
                            // verify the harmonized doc
                        }));

                tests.add(DynamicTest.dynamicTest("run flow " + "- " + "with options file - " + fileName,
                        () -> {
                            BuildResult result = runTask(":hubRunFlow");
                            assert (result.task(":hubRunFlow").getOutcome().equals("SUCCESS"));
                            // verify doc count
                            // verify the job count
                            // verify the step status
                            // verify the harmonized doc
                        }));
            }
        });
        return tests;
    }
}
