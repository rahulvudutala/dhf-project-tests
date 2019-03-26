package com.marklogic.hub.client.api;

import com.marklogic.hub.client.invoker.ApiClient;

import com.marklogic.hub.client.model.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaClientCodegen", date = "2019-03-26T12:04:43.950-07:00")
@Component("com.marklogic.hub.client.api.StepsApi")
public class StepsApi {
    private ApiClient apiClient;

    public StepsApi() {
        this(new ApiClient());
    }

    @Autowired
    public StepsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Create a step within a Flow
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * @param flowId Id of flow
     * @param body Step to create
     * @return Step
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Step createFlowStep(String flowId, Step body) throws RestClientException {
        Object postBody = body;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling createFlowStep");
        }
        
        // verify the required parameter 'body' is set
        if (body == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'body' when calling createFlowStep");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}/steps").buildAndExpand(uriVariables).toUriString();
        
        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] accepts = { 
            "application/json"
        };
        final List<MediaType> accept = apiClient.selectHeaderAccept(accepts);
        final String[] contentTypes = { };
        final MediaType contentType = apiClient.selectHeaderContentType(contentTypes);

        String[] authNames = new String[] {  };

        ParameterizedTypeReference<Step> returnType = new ParameterizedTypeReference<Step>() {};
        return apiClient.invokeAPI(path, HttpMethod.POST, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Delete step by Id
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * <p><b>404</b> - Flow not found
     * @param flowId Id of flow to be fetched
     * @param stepId Id of step to be fetched
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public void deleteFlowStep(String flowId, String stepId) throws RestClientException {
        Object postBody = null;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling deleteFlowStep");
        }
        
        // verify the required parameter 'stepId' is set
        if (stepId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'stepId' when calling deleteFlowStep");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        uriVariables.put("stepId", stepId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}/steps/{stepId}").buildAndExpand(uriVariables).toUriString();
        
        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] accepts = { 
            "application/json"
        };
        final List<MediaType> accept = apiClient.selectHeaderAccept(accepts);
        final String[] contentTypes = { };
        final MediaType contentType = apiClient.selectHeaderContentType(contentTypes);

        String[] authNames = new String[] {  };

        ParameterizedTypeReference<Void> returnType = new ParameterizedTypeReference<Void>() {};
        apiClient.invokeAPI(path, HttpMethod.DELETE, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Get all Steps for a Flow
     * ....
     * <p><b>200</b> - successful operation
     * @param flowId Id of flow to be fetched
     * @return List&lt;Step&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public List<Step> getFlowSteps(String flowId) throws RestClientException {
        Object postBody = null;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling getFlowSteps");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}/steps").buildAndExpand(uriVariables).toUriString();
        
        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] accepts = { 
            "application/json"
        };
        final List<MediaType> accept = apiClient.selectHeaderAccept(accepts);
        final String[] contentTypes = { };
        final MediaType contentType = apiClient.selectHeaderContentType(contentTypes);

        String[] authNames = new String[] {  };

        ParameterizedTypeReference<List<Step>> returnType = new ParameterizedTypeReference<List<Step>>() {};
        return apiClient.invokeAPI(path, HttpMethod.GET, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Update step by Id
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * <p><b>404</b> - flow not found
     * @param flowId Id of flow to be updated
     * @param stepId Id of step to be updated
     * @param body Updated step
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public void updateFlowStep(String flowId, String stepId, Step body) throws RestClientException {
        Object postBody = body;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling updateFlowStep");
        }
        
        // verify the required parameter 'stepId' is set
        if (stepId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'stepId' when calling updateFlowStep");
        }
        
        // verify the required parameter 'body' is set
        if (body == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'body' when calling updateFlowStep");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        uriVariables.put("stepId", stepId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}/steps/{stepId}").buildAndExpand(uriVariables).toUriString();
        
        final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
        final HttpHeaders headerParams = new HttpHeaders();
        final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

        final String[] accepts = { 
            "application/json"
        };
        final List<MediaType> accept = apiClient.selectHeaderAccept(accepts);
        final String[] contentTypes = { };
        final MediaType contentType = apiClient.selectHeaderContentType(contentTypes);

        String[] authNames = new String[] {  };

        ParameterizedTypeReference<Void> returnType = new ParameterizedTypeReference<Void>() {};
        apiClient.invokeAPI(path, HttpMethod.PUT, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
}
