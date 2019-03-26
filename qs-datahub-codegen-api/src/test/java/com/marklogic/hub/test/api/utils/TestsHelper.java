package com.marklogic.hub.test.api.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.hub.client.api.FlowsApi;
import com.marklogic.hub.client.api.StepsApi;
import com.marklogic.hub.client.model.Flow;
import com.marklogic.hub.client.model.Step;

public class TestsHelper {

    protected final FlowsApi flowApi = new FlowsApi();
    protected final StepsApi stepApi = new StepsApi();

    // update flow and test declarations
    private boolean flowIdChanged = false;
    String changedFlowId = null;
    
    // update step and test declarations
    private boolean stepIdChanged = false;
    String changedStepId = null;

    protected void allCombos(ComboListener listener) {
        String[] responseCodes = new String[] { "2xx", "4xx", "5xx" };
        for (String responseCode : responseCodes) {
            listener.onCombo(responseCode);
        }
    }

    public String createFlowTestResponse(String fileName, String testType) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Flow body = mapper.readValue(new File(getPath(fileName, testType).toString()), Flow.class);
            String flowId = body.getId();
            String flowName = body.getName();

            Flow response = flowApi.createFlow(body);
            if (response != null) {
                if (flowId != null) {
                    assertFalse(response.getId().equals(flowId));
                } else {
                    assert (response.getId() != null);
                }

                if (flowName != null) {
                    assert (response.getName().equals(flowName));
                } else {
                    assert (response.getName() != null);
                }
                return "200 OK";
            }
            return "400 error";
        } catch (IOException e) {
            return "400 error";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String getFlowsTestResponse() {
        try {
            List<Flow> flows = flowApi.getFlows();
            int noOfFlows = flows.size();
            createFlow();
            flows = flowApi.getFlows();
            assert (flows.size() == noOfFlows + 1);
            for (Flow flow : flows) {
                assert (flow.getId() != null);
                assert (flow.getName() != null);
                assert (flow.getThreadCount() != null);
                assert (flow.getBatchSize() != null);
            }
            return "200 OK";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String getFlowTestResponse(String flowId) {
        try {
            Flow flow = flowApi.getFlow(flowId);
            assert (flow.getId() != null);
            assert (flow.getName() != null);
            assert (flow.getThreadCount() != null);
            assert (flow.getBatchSize() != null);
            return "200 OK";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String deleteFlowTestResponse(String flowId) {
        try {
            int noOfFlows = getFlowIds().size();
            flowApi.deleteFlow(flowId);

            List<String> remFlowIds = getFlowIds();
            int remFlows = remFlowIds.size();

            assert (remFlows == noOfFlows - 1);
            assertFalse(remFlowIds.contains(flowId));
            return "200 OK";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String updateFlowTestResponse(String flowId, String body)
            throws JsonParseException, JsonMappingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            if (flowIdChanged) {
                flowId = changedFlowId;
            }
            Flow updateFlowBody = mapper.readValue(body, Flow.class);
            Flow currFlow = flowApi.getFlow(flowId);
            flowApi.updateFlow(flowId, updateFlowBody);

            if (!updateFlowBody.getId().equals(currFlow.getId())) {
                flowIdChanged = true;
                changedFlowId = updateFlowBody.getId();
                flowId = updateFlowBody.getId();
            }

            Flow updatedFlow = flowApi.getFlow(flowId);
            assertFalse(updatedFlow.toString().equals(currFlow.toString()));
//            assert (updatedFlow.toString().equals(updateFlowBody.toString()));
            return "200 OK";
        } catch (IOException e) {
            return "400 error";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String updateFlowTestResponse4xx(String fileName, String testType, String flowId) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Flow body = mapper.readValue(new File(getPath(fileName, testType).toString()), Flow.class);
            flowApi.updateFlow(flowId, body);
            return "200 OK";
        } catch (IOException e) {
            return "400 error";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String createFlowStepTestResponse(String flowId, String fileName, String testType) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Step request = mapper.readValue(new File(getPath(fileName, testType).toString()), Step.class);
            String stepType = request.getType() == null ? null : request.getType().getValue();
            Step response = stepApi.createFlowStep(flowId, request);

            assert (response != null);
            assert (response.getId() != null);
            if (request.getId() != null) {
                assertFalse(response.getId().equals(request.getId()));
            }
            
            System.out.println(response.toString());
            
            assert (response.getName() != null);
//            assert (response.getType().getValue().equals(stepType));
//            assert (response.getTargetDatabase() != null);
//            assert (response.getVersion() != null);

            return "200 OK";
        } catch (IOException e) {
            // can be changed to e.getStatusCode().value()
            return "400 error";
        } catch (HttpClientErrorException e) {
            // can be changed to e.getStatusCode().value()
            return "400 error";
        } catch (RestClientException e) {
            // can be changed to e.getStatusCode().value()
            return "400 error";
        }
    }

    public String getFlowStepsTestResponse(String flowId) {
        try {
            List<Step> steps = stepApi.getFlowSteps(flowId);
            int noOfSteps = steps.size();
            createStep(flowId);
            steps = stepApi.getFlowSteps(flowId);
            assert (steps.size() == noOfSteps + 1);
            for (Step step : steps) {
                assert (step.getId() != null);
                assert (step.getName() != null);
                assert (step.getTargetDatabase() != null);
                assert (step.getType() != null);
            }
            return "200 OK";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String deleteFlowStepTestResponse(String flowId, String stepId) {
        try {
            int noOfSteps = getFlowStepIds(flowId).size();
            stepApi.deleteFlowStep(flowId, stepId);

            List<String> remFlowStepIds = getFlowStepIds(flowId);
            int remSteps = remFlowStepIds.size();

            assert (remSteps == noOfSteps - 1);
            assertFalse(remFlowStepIds.contains(stepId));
            return "200 OK";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String updateFlowStepTestResponse(String flowId, String stepId, String stepBody) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            if (stepIdChanged) {
                stepId = changedStepId;
            }
            Step updateFlowStepBody = mapper.readValue(stepBody, Step.class);
            Step currStep = getFlowStep(flowId, stepId);
            stepApi.updateFlowStep(flowId, stepId, updateFlowStepBody);
            if (!updateFlowStepBody.getId().equals(currStep.getId())) {
                stepIdChanged = true;
                changedStepId = updateFlowStepBody.getId();
                stepId = updateFlowStepBody.getId();
            }
            Step updatedFlowStep = getFlowStep(flowId, stepId);
            assertFalse(updatedFlowStep.toString().equals(currStep.toString()));
            assert (updatedFlowStep.toString().equals(updateFlowStepBody.toString()));
            return "200 OK";
        } catch (IOException e) {
            return "400 error";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public String updateFlowStepTestResponse4xx(String fileName, String testType, String flowId, String stepId) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Step body = mapper.readValue(new File(getPath(fileName, testType).toString()), Step.class);
            stepApi.updateFlowStep(flowId, stepId, body);
            return "200 OK";
        } catch (IOException e) {
            return "400 error";
        } catch (HttpClientErrorException e) {
            return "400 error";
        } catch (RestClientException e) {
            return "400 error";
        }
    }

    public Path getPath(String fileName, String testType) {
        return Paths.get("src/test/resources/input", testType, fileName);
    }

    private void createFlow() {
        ObjectMapper mapper = new ObjectMapper();
        Flow input = null;
        try {
            input = mapper.readValue(new File(getPath("create-2xx-allvalues.json", "flow").toString()), Flow.class);
            flowApi.createFlow(input);
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createStep(String flowId) {
        ObjectMapper mapper = new ObjectMapper();
        Step input = null;
        try {
            input = mapper.readValue(new File(getPath("create-2xx-allvalues.json", "step").toString()), Step.class);
            stepApi.createFlowStep(flowId, input);
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected List<String> getFlowIds() {
        List<String> flowIds = new ArrayList<>();
        List<Flow> flows = flowApi.getFlows();
        for (Flow flow : flows) {
            flowIds.add(flow.getId());
        }
        return flowIds;
    }

    protected List<String> getFlowStepIds(String flowId) {
        List<String> flowStepIds = new ArrayList<>();
        List<Step> steps = stepApi.getFlowSteps(flowId);
        for (Step step : steps) {
            flowStepIds.add(step.getId());
        }
        return flowStepIds;
    }

    protected Step getFlowStep(String flowId, String stepId) {
        List<Step> flowSteps = stepApi.getFlowSteps(flowId);
        for (Step step : flowSteps) {
            if (step.getId().equals(stepId)) {
                return step;
            }
        }
        return null;
    }

    protected String getJsonResource(String resourceName, String testType) {
        String jsonString = null;
        try {
            String absPath = getPath(resourceName, testType).toString();
            InputStream is = new FileInputStream(new File(absPath).getAbsolutePath());
            jsonString = IOUtils.toString(is, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonString;
    }
}
