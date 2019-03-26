package com.marklogic.hub.client.api;

import com.marklogic.hub.client.invoker.ApiClient;

import com.marklogic.hub.client.model.Flow;
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
@Component("com.marklogic.hub.client.api.FlowsApi")
public class FlowsApi {
    private ApiClient apiClient;

    public FlowsApi() {
        this(new ApiClient());
    }

    @Autowired
    public FlowsApi(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Create flow
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * @param body Flow to create
     * @return Flow
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Flow createFlow(Flow body) throws RestClientException {
        Object postBody = body;
        
        // verify the required parameter 'body' is set
        if (body == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'body' when calling createFlow");
        }
        
        String path = UriComponentsBuilder.fromPath("/flows").build().toUriString();
        
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

        ParameterizedTypeReference<Flow> returnType = new ParameterizedTypeReference<Flow>() {};
        return apiClient.invokeAPI(path, HttpMethod.POST, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Delete flow by Id
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * <p><b>404</b> - Flow not found
     * @param flowId Id of flow to be fetched
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public void deleteFlow(String flowId) throws RestClientException {
        Object postBody = null;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling deleteFlow");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}").buildAndExpand(uriVariables).toUriString();
        
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
     * Find flow by Id
     * ....
     * <p><b>200</b> - successful operation
     * @param flowId Id of flow to be fetched
     * @return Flow
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Flow getFlow(String flowId) throws RestClientException {
        Object postBody = null;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling getFlow");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}").buildAndExpand(uriVariables).toUriString();
        
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

        ParameterizedTypeReference<Flow> returnType = new ParameterizedTypeReference<Flow>() {};
        return apiClient.invokeAPI(path, HttpMethod.GET, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Returns all flows
     * Returns all flows
     * <p><b>200</b> - successful operation
     * @return List&lt;Flow&gt;
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public List<Flow> getFlows() throws RestClientException {
        Object postBody = null;
        
        String path = UriComponentsBuilder.fromPath("/flows").build().toUriString();
        
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

        ParameterizedTypeReference<List<Flow>> returnType = new ParameterizedTypeReference<List<Flow>>() {};
        return apiClient.invokeAPI(path, HttpMethod.GET, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Run a Flow
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * <p><b>404</b> - Step not found
     * @param flowId Id of flow to run
     * @return Step
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Step runFlow(String flowId) throws RestClientException {
        Object postBody = null;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling runFlow");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}/run").buildAndExpand(uriVariables).toUriString();
        
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
     * Stop a Flow
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * <p><b>404</b> - Flow not found
     * @param flowId Id of flow to stop
     * @return Flow
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public Flow stopFlow(String flowId) throws RestClientException {
        Object postBody = null;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling stopFlow");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}/stop").buildAndExpand(uriVariables).toUriString();
        
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

        ParameterizedTypeReference<Flow> returnType = new ParameterizedTypeReference<Flow>() {};
        return apiClient.invokeAPI(path, HttpMethod.POST, queryParams, postBody, headerParams, formParams, accept, contentType, authNames, returnType);
    }
    /**
     * Update flow by Id
     * 
     * <p><b>200</b> - successful operation
     * <p><b>400</b> - Invalid parameter supplied
     * <p><b>404</b> - flow not found
     * @param flowId Id of flow to be updated
     * @param body Updated flow
     * @throws RestClientException if an error occurs while attempting to invoke the API
     */
    public void updateFlow(String flowId, Flow body) throws RestClientException {
        Object postBody = body;
        
        // verify the required parameter 'flowId' is set
        if (flowId == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'flowId' when calling updateFlow");
        }
        
        // verify the required parameter 'body' is set
        if (body == null) {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Missing the required parameter 'body' when calling updateFlow");
        }
        
        // create path and map variables
        final Map<String, Object> uriVariables = new HashMap<String, Object>();
        uriVariables.put("flowId", flowId);
        String path = UriComponentsBuilder.fromPath("/flows/{flowId}").buildAndExpand(uriVariables).toUriString();
        
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
