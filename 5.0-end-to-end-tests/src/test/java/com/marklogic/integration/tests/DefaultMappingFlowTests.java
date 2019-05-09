package com.marklogic.integration.tests;

import com.marklogic.client.io.DOMHandle;
import com.marklogic.client.io.StringHandle;
import com.marklogic.hub.HubConfig;
import com.marklogic.utils.TestsHelper;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.w3c.dom.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DefaultMappingFlowTests extends TestsHelper {

    private static final Logger log =
            Logger.getLogger(DefaultMappingFlowTests.class.getName());

    @BeforeAll
    public void init() {
        initializeProject();
    }

    public void setUpDocs(String dataFormat) {
        // clear documents to make assertion easy for each test
        clearDatabases(HubConfig.DEFAULT_STAGING_NAME, HubConfig.DEFAULT_FINAL_NAME);

        // Load user modules into the datahub
        BuildResult result = runTask(":5.0-end-to-end-tests:mlLoadModules");
        assert (result.task(":5.0-end-to-end-tests:mlLoadModules").getOutcome().toString().equals("SUCCESS"));

        // run mlcp flow to ingest documents
        if (dataFormat.equals("json")) {
            result = runTask(":5.0-end-to-end-tests:importData");
            assert (result.task(":5.0-end-to-end-tests:importData").getOutcome().toString().equals("SUCCESS"));
            assert (getDocCount("data-hub-STAGING", "default-ingest") == 6);
        } else {
            result = runTask(":5.0-end-to-end-tests:importDataAsXML");
            assert (result.task(":5.0-end-to-end-tests:importDataAsXML").getOutcome().toString().equals("SUCCESS"));
        }

        // clearing jobs database here to count job docs generated after mapping
        clearDatabases(HubConfig.DEFAULT_JOB_NAME);
    }

    @TestFactory
    public List<DynamicTest> generateRunFlowTests() {
        List<DynamicTest> tests = new ArrayList<>();

        // default-mapping flows
        allCombos((dataFormat, outputFormat) -> {
            String flowName = "default-mapping", flowType = "default-mapping", testType = "positive";
            File optionDir = new File(optionsPath + "/" + outputFormat + "/" + flowType + "/" + testType);
            File[] listOfFiles = optionDir.listFiles();

            String inputDocsLoc = "input/orders/simple-docs";
            if (dataFormat.equals("json")) {
                inputDocsLoc = inputDocsLoc.concat("/json/");
            } else {
                inputDocsLoc = inputDocsLoc.concat("/xml/");
            }
            File inputDocsDir = new File(inputDocsLoc);
            File[] inputFiles = inputDocsDir.listFiles();
            String inputFileNameJson = inputFiles[0].getName();
//            String inputFileNameXml = inputFiles[1].getName();

            for (File optionsFile : listOfFiles) {
                String optionsFileName = optionsFile.getName();
                String optionsFilePath = optionDir + "/" + optionsFileName;
                String collection = getCollectionFromIdentifierFromOptionsFile(optionsFilePath);
                tests.add(DynamicTest.dynamicTest("run flow " + "- " + flowName + "with options as file: " + optionsFileName,
                        () -> {
                            setUpDocs(dataFormat);
                            int finalDbDocsCount = getDocCount("data-hub-FINAL", null);
                            BuildResult result = runTask(":5.0-end-to-end-tests" + ":hubRunFlow",
                                    "-PflowName=" + flowName,
                                    "-PoptionsFile=" + optionsFilePath);
                            BuildTask taskResult = result.task(":5.0-end-to-end-tests:hubRunFlow");

                            // verify the taskoutcome to be true
                            assert (taskResult.getOutcome().toString().equals("SUCCESS"));

                            // verify the step status
                            String taskOutput = result.getOutput();
                            boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                            assert (runFlowStatus == true);

                            // verify the docs count in final database
                            int docsInStagingIngestColl = getDocCount("data-hub-STAGING", collection);
                            int docsInFinalIngestColl = getDocCount("data-hub-FINAL", collection);
                            assert (finalDbDocsCount + docsInStagingIngestColl == getDocCount("data-hub-FINAL", null));

                            // verify the harmonized doc
                            if (docsInFinalIngestColl != 0) {
                                if (dataFormat.equals("json")) {
                                    getAndVerifyDocumentsFromDatabase(inputFileNameJson, optionsFilePath, outputFormat,
                                            dataFormat);
                                } else {
                                    getAndVerifyDocumentsFromDatabase(inputFileNameJson, optionsFilePath, outputFormat,
                                            dataFormat);
                                }
                            }

                            // verify the job and provenance count
                            int batchSize = getBatchSizeFromOptionsFile(optionsFilePath);
                            double batches = Math.ceil((double) docsInStagingIngestColl / batchSize);
                            int finalBatches = (int) batches;
                            assert (1 + finalBatches == getDocCount("data-hub-JOBS", "Jobs"));

                            // TODO: verify the provenance doc
                        }));
            }


            String flowNameN = "default-mapping", flowTypeN = "default-mapping", testTypeN = "negative";
            File optionDirN = new File(optionsPath + "/" + outputFormat + "/" + flowTypeN + "/" + testTypeN);
            File[] listOfFilesN = optionDirN.listFiles();

            for (File optionsFileN : listOfFilesN) {
                String optionsFileNameN = optionsFileN.getName();
                String optionsFilePathN = optionDirN + "/" + optionsFileNameN;
                String collection = getCollectionFromIdentifierFromOptionsFile(optionsFilePathN);
                tests.add(DynamicTest.dynamicTest("run flow " + "- " + flowNameN + "with optionsFile: " + optionsFileNameN, () -> {
                    setUpDocs(outputFormat);
                    BuildResult resultN = runTask(":5.0-end-to-end-tests" + ":hubRunFlow", "-PflowName=" + flowNameN,
                            "-PoptionsFile=" + optionsFilePathN);
                    assert (resultN.task(":5.0-end-to-end-tests:hubRunFlow").getOutcome().toString().equals("SUCCESS"));

                    // verify the step status
                    String taskOutput = resultN.getOutput();
                    boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                    assert (runFlowStatus == false);

                    // verify doc count
                    assert (0 == getDocCount("data-hub-FINAL", "default-ingest"));

                    // verify the job count
                    int batchSize = getBatchSizeFromOptionsFile(optionsFilePathN);
                    int docsInStagingIngestColl = getDocCount("data-hub-STAGING", collection);
                    double batches = Math.ceil((double) docsInStagingIngestColl / batchSize);
                    int finalBatches = (int) batches;
                    assert (1 + finalBatches == getDocCount("data-hub-JOBS", "Jobs"));
                }));
            }
        });


        allCombos((dataFormat, outputFormat) -> {
            String flowName = "default-mapping", flowType = "default-mapping", testType = "positive";
            File optionDir = new File(optionsPath + "/" + outputFormat + "/" + flowType + "/" + testType);
            File[] listOfFiles = optionDir.listFiles();

            String inputDocsLoc = "input/orders/simple-docs";
            if (dataFormat.equals("json")) {
                inputDocsLoc = inputDocsLoc.concat("/json/");
            } else {
                inputDocsLoc = inputDocsLoc.concat("/xml/");
            }
            File inputDocsDir = new File(inputDocsLoc);

            File[] inputFiles = inputDocsDir.listFiles();
            String inputFileNameJson = inputFiles[0].getName();
//            String inputFileNameXml = inputFiles[1].getName();

            for (File optionsFile : listOfFiles) {
                String optionsFileName = optionsFile.getName();
                String optionsFilePath = optionDir + "/" + optionsFileName;
                String options = getJsonResource(optionsFilePath).toString();
                String collection = getCollectionFromIdentifierFromOptionsFile(optionsFilePath);
                tests.add(DynamicTest.dynamicTest("run flow " + "- " + flowName + "with options as string: " + optionsFileName,
                        () -> {
                            setUpDocs(dataFormat);
                            int finalDbDocsCount = getDocCount("data-hub-FINAL", null);
                            BuildResult result = runTask(":5.0-end-to-end-tests" + ":hubRunFlow",
                                    "-PflowName=" + flowName,
                                    "-Poptions=" + options);
                            BuildTask taskResult = result.task(":5.0-end-to-end-tests:hubRunFlow");

                            // verify the taskoutcome to be true
                            assert (taskResult.getOutcome().toString().equals("SUCCESS"));

                            // verify the step status
                            String taskOutput = result.getOutput();
                            boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                            assert (runFlowStatus == true);

                            // verify the docs count
                            int docsInStagingIngestColl = getDocCount("data-hub-STAGING", collection);
                            int docsInFinalIngestColl = getDocCount("data-hub-FINAL", collection);
                            assert (finalDbDocsCount + docsInStagingIngestColl == getDocCount("data-hub-FINAL", null));

                            // verify the harmonized doc
                            if (docsInFinalIngestColl != 0) {
                                if (dataFormat.equals("json")) {
                                    getAndVerifyDocumentsFromDatabase(inputFileNameJson, optionsFilePath, outputFormat,
                                            dataFormat);
                                } else {
                                    getAndVerifyDocumentsFromDatabase(inputFileNameJson, optionsFilePath, outputFormat,
                                            dataFormat);
                                }
                            }

                            // verify the job and provenance count
                            int batchSize = getBatchSizeFromOptionsFile(optionsFilePath);
                            double batches = Math.ceil((double) docsInStagingIngestColl / 100);
                            int finalBatches = (int) batches;
                            assert (1 + finalBatches == getDocCount("data-hub-JOBS", "Jobs"));

                            // TODO: verify the provenance doc
                        }));
            }


            String flowNameN = "default-mapping", flowTypeN = "default-mapping", testTypeN = "negative";
            File optionDirN = new File(optionsPath + "/" + outputFormat + "/" + flowTypeN + "/" + testTypeN);
            File[] listOfFilesN = optionDirN.listFiles();

            for (File optionsFileN : listOfFilesN) {
                String optionsFileNameN = optionsFileN.getName();
                String optionsFilePathN = optionDirN + "/" + optionsFileNameN;
                String optionsN = getJsonResource(optionsFilePathN).toString();
                String collection = getCollectionFromIdentifierFromOptionsFile(optionsFilePathN);
                tests.add(DynamicTest.dynamicTest("run flow " + "- " + flowNameN + "with options as string: " + optionsFileNameN, () -> {
                    setUpDocs(outputFormat);
                    BuildResult resultN = runTask(":5.0-end-to-end-tests" + ":hubRunFlow", "-PflowName=" + flowNameN,
                            "-Poptions=" + optionsN);
                    assert (resultN.task(":5.0-end-to-end-tests:hubRunFlow").getOutcome().toString().equals("SUCCESS"));

                    // verify the step status
                    String taskOutput = resultN.getOutput();
                    boolean runFlowStatus = parseAndVerifyRunFlowStatus(taskOutput);
                    assert (runFlowStatus == false);

                    // verify doc count
                    assert (0 == getDocCount("data-hub-FINAL", "default-ingest"));

                    // verify the job count
                    int batchSize = getBatchSizeFromOptionsFile(optionsFilePathN);
                    int docsInStagingIngestColl = getDocCount("data-hub-STAGING", collection);
                    double batches = Math.ceil((double) docsInStagingIngestColl / 100);
                    int finalBatches = (int) batches;
                    assert (1 + finalBatches == getDocCount("data-hub-JOBS", "Jobs"));
                }));
            }
        });
        return tests;
    }

    private void getAndVerifyDocumentsFromDatabase(String docName, String optionsFileLoc,
                                                   String outputFormat, String dataFormat) {
        String mappingVersion = getMappingVersionFromOptionsFile(optionsFileLoc);
        String filePath = "src/test/resources/output/orders/";
        String fileName = null;

        // actual and expected output declarations
        String expected = null;
        String actual = null;
        Document actualDoc = null;
        Document expectedDoc = null;

        if (optionsFileLoc.contains("wrong-mapping-options.json")) {
            fileName = "10248-null-data";
        } else if (mappingVersion == null || mappingVersion.equals("2")) {
            mappingVersion = "2";
            fileName = "10248-" + mappingVersion;
        } else if (mappingVersion != null && mappingVersion.equals("1")) {
            fileName = "10248-" + mappingVersion;
        }

        if (dataFormat.equals("json")) {
            if (outputFormat.equals("json")) {
                actual = finalDocMgr.read("/json/" + docName).next().getContent(new StringHandle()).get();
                if (docName.contains(".json")) {
                    expected = getJsonResource(filePath + "jsonTojson/" + fileName + "-json" + ".json").toString();
                } else {
                    expected = getJsonResource(filePath + "jsonTojson/" + fileName + "-xml" + ".json").toString();
                }
                assertJsonEqual(expected, actual);
            } else {
                actualDoc = finalDocMgr.read("/json/" + docName).next().getContent(new DOMHandle()).get();
                if (docName.contains(".json")) {
                    expectedDoc = getXmlResource(filePath + "jsonToxml/" + fileName + "-json" + ".xml");
                } else {
                    expectedDoc = getXmlResource(filePath + "jsonToxml/" + fileName + "-xml" + ".xml");
                }
                debugOutput(expectedDoc, System.out);
                debugOutput(actualDoc, System.out);
                assertXMLEqual(expectedDoc, actualDoc);
            }
        } else {
            if (outputFormat.equals("json")) {
                actual = finalDocMgr.read("/xml/" + docName).next().getContent(new StringHandle()).get();
                if (docName.contains(".json")) {
                    expected = getJsonResource(filePath + "xmlTojson/" + fileName + "-json" + ".json").toString();
                } else {
                    expected = getJsonResource(filePath + "xmlTojson/" + fileName + "-xml" + ".json").toString();
                }
                assertJsonEqual(expected, actual);
            } else {
                actualDoc = finalDocMgr.read("/xml/" + docName).next().getContent(new DOMHandle()).get();
                if (docName.contains(".json")) {
                    expectedDoc = getXmlResource(filePath + "xmlToxml/" + fileName + "-json" + ".xml");
                } else {
                    expectedDoc = getXmlResource(filePath + "xmlToxml/" + fileName + "-xml" + ".xml");
                }
                debugOutput(expectedDoc, System.out);
                debugOutput(actualDoc, System.out);
                assertXMLEqual(expectedDoc, actualDoc);
            }
        }
    }
}
