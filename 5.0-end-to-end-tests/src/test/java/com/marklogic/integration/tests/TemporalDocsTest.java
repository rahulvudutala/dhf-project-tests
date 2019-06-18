package com.marklogic.integration.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.io.StringHandle;
import com.marklogic.utils.TestsHelper;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TemporalDocsTest extends TestsHelper {
    @BeforeAll
    public void init() {
        initializeProject();
        createTemporalConfig();
        setUpTemporalConfig();
    }

    public void setUpTemporalConfig() {
        // Create a new database and Deploy temporal config into the database
        BuildResult result = runTask(":5.0-end-to-end-tests:mlLoadModules");
        assert (result.task(":5.0-end-to-end-tests:mlLoadModules").getOutcome().toString().equals("SUCCESS"));
        result = runTask(":5.0-end-to-end-tests:mlDeployDatabases");
        assert (result.task(":5.0-end-to-end-tests:mlDeployDatabases").getOutcome().toString().equals("SUCCESS"));
        result = runTask(":5.0-end-to-end-tests:mlDeployTemporal");
        assert (result.task(":5.0-end-to-end-tests:mlDeployTemporal").getOutcome().toString().equals("SUCCESS"));
    }

    @TestFactory
    public List<DynamicTest> generateRunFlowTests() {
        List<DynamicTest> tests = new ArrayList<>();

        tests.add(DynamicTest.dynamicTest("temporal-scenario", () -> {
            // Run the ingestion step
            BuildResult result = runTask(":5.0-end-to-end-tests:importTemporalData");
            assert (result.task(":5.0-end-to-end-tests:importTemporalData").getOutcome().toString().equals("SUCCESS"));
            assert (getDocCount("data-hub-STAGING", "temporal-collection-ingest") == 6);
            verifyDocs("ingestion");

            // Run the mapping step
            String optionFilePath = "src/test/resources/options/temporal/default-options.json";
            result = runTask(":5.0-end-to-end-tests:hubRunFlow", "-PflowName=default-mapping",
                    "-PoptionsFile=" + optionFilePath);
            assert (result.task(":5.0-end-to-end-tests:hubRunFlow").getOutcome().toString().equals("SUCCESS"));
            assert (getDocCount("data-hub-FINAL", "temporal-collection-map") == 6);
            verifyDocs("mapping");
        }));
        return tests;
    }

    private void verifyDocs(String stepType) {
        try {
            String actual = null;
            String expected = getJsonResource(outputOrdersPath + "/temporal/" + stepType + "/10248.json").toString();
            ObjectMapper mapper = new ObjectMapper();

            if (stepType.equals("ingestion")) {
                actual = stagingDocMgr.read("/json/10248.json").next().getContent(new StringHandle()).get();
            }
            if (stepType.equals("mapping")) {
                actual = finalDocMgr.read("/json/10248.json").next().getContent(new StringHandle()).get();
            }

            JsonNode expectedObj = mapper.readTree(expected);
            JsonNode actualObj = mapper.readTree(actual);
            validateTimeStamps(expectedObj, actualObj);
            validateInstanceAndAttachments(expectedObj, actualObj);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private void validateTimeStamps(JsonNode expectedObj, JsonNode actualObj) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

            String expSystemStart = expectedObj.get("envelope").get("headers").get("systemStart").asText();
            Date expSystemStartDate = expSystemStart.contains(".") ? sdf.parse(expSystemStart) :
                    sdf1.parse(expSystemStart);
            String actSystemStart = actualObj.get("envelope").get("headers").get("systemStart").asText();
            Date actSystemStartDate = actSystemStart.contains(".") ? sdf.parse(actSystemStart) :
                    sdf1.parse(actSystemStart);

            String expSystemEnd = expectedObj.get("envelope").get("headers").get("systemEnd").asText();
            Date expSystemEndDate = expSystemEnd.contains(".") ? sdf.parse(expSystemEnd) :
                    sdf1.parse(expSystemEnd);
            String actSystemEnd = actualObj.get("envelope").get("headers").get("systemEnd").asText();
            Date actSystemEndDate = actSystemEnd.contains(".") ? sdf.parse(actSystemEnd) :
                    sdf1.parse(actSystemEnd);

            String expValidStart = expectedObj.get("envelope").get("headers").get("validStart").asText();
            Date expValidStartDate = expValidStart.contains(".") ? sdf.parse(expValidStart) :
                    sdf1.parse(expValidStart);
            String actValidStart = actualObj.get("envelope").get("headers").get("validStart").asText();
            Date actValidStartDate = actValidStart.contains(".") ? sdf.parse(actValidStart) :
                    sdf1.parse(actValidStart);

            String expValidEnd = expectedObj.get("envelope").get("headers").get("validEnd").asText();
            Date expValidEndDate = expValidEnd.contains(".") ? sdf.parse(expValidEnd) :
                    sdf1.parse(expValidEnd);
            String actValidEnd = actualObj.get("envelope").get("headers").get("validEnd").asText();
            Date actValidEndDate = actValidEnd.contains(".") ? sdf.parse(actValidEnd) :
                    sdf1.parse(actValidEnd);

            assertTrue(expSystemStartDate.before(actSystemStartDate));
            assertTrue(expSystemEndDate.compareTo(actSystemEndDate) == 0);
            assertTrue(expValidStartDate.compareTo(actValidStartDate) == 0);
            assertTrue(expValidEndDate.compareTo(actValidEndDate) == 0);
        } catch (ParseException pe) {
            pe.printStackTrace();
        }
    }

    private void validateInstanceAndAttachments(JsonNode expectedObj, JsonNode actualObj) {
        String expInstance = expectedObj.get("envelope").get("instance").asText();
        String actualInstance = actualObj.get("envelope").get("instance").asText();

        String expAttachments = expectedObj.get("envelope").get("attachments").asText();
        String actAttachments = actualObj.get("envelope").get("attachments").asText();

        assertJsonEqual(expInstance, actualInstance);
        assertJsonEqual(expAttachments, actAttachments);
    }
}

