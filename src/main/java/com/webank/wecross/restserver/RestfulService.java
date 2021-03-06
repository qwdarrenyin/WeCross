package com.webank.wecross.restserver;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.core.NetworkManager;
import com.webank.wecross.core.StateRequest;
import com.webank.wecross.core.StateResponse;
import com.webank.wecross.resource.GetDataRequest;
import com.webank.wecross.resource.GetDataResponse;
import com.webank.wecross.resource.Resource;
import com.webank.wecross.resource.SetDataRequest;
import com.webank.wecross.resource.SetDataResponse;
import com.webank.wecross.resource.TransactionRequest;
import com.webank.wecross.resource.TransactionResponse;
import com.webank.wecross.resource.URI;
import com.webank.wecross.restserver.p2p.P2PResponse;
import org.fisco.bcos.web3j.protocol.ObjectMapperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class RestfulService {

    @javax.annotation.Resource private NetworkManager networkManager;

    private Logger logger = LoggerFactory.getLogger(RestfulService.class);
    private ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();

    @RequestMapping("/test")
    public String test() {
        return "OK!";
    }

    @RequestMapping(value = "/state")
    public RestResponse<StateResponse> handleState() {
        RestResponse<StateResponse> restResponse = new RestResponse<StateResponse>();

        StateResponse stateResponse = networkManager.getState(new StateRequest());
        restResponse.setVersion("0.1");
        restResponse.setResult(0);
        restResponse.setData(stateResponse);

        return restResponse;
    }

    @RequestMapping(value = "/{network}/{method}")
    public RestResponse<Object> handleNetwork(
            @PathVariable("network") String network, @PathVariable("method") String method) {
        //
        return null;
    }

    @RequestMapping(value = "/{network}/{stub}/{method}")
    public RestResponse<Object> handleStub(
            @PathVariable("network") String network,
            @PathVariable("stub") String stub,
            @PathVariable("method") String method) {
        // getState
        // getBlockHeader
        return null;
    }

    @RequestMapping(value = "/{network}/{stub}/{resource}/{method}", method = RequestMethod.GET)
    public RestResponse<Object> handleResource(
            @PathVariable("network") String network,
            @PathVariable("stub") String stub,
            @PathVariable("resource") String resource,
            @PathVariable("method") String method) {
        return handleResource(network, stub, resource, method, "");
    }

    @RequestMapping(value = "/{network}/{stub}/{resource}/{method}", method = RequestMethod.POST)
    public RestResponse<Object> handleResource(
            @PathVariable("network") String network,
            @PathVariable("stub") String stub,
            @PathVariable("resource") String resource,
            @PathVariable("method") String method,
            @RequestBody String restRequestString) {
        URI uri = new URI();
        uri.setNetwork(network);
        uri.setChain(stub);
        uri.setResource(resource);

        RestResponse<Object> restResponse = new RestResponse<Object>();
        restResponse.setVersion("0.1");
        restResponse.setResult(0);

        logger.info("request string: {}", restRequestString);

        try {
            Resource resourceObj = networkManager.getResource(uri);
            if (resourceObj == null) {
                logger.warn("Unable to find resource: {}.{}.{}", network, stub, resource);

                throw new Exception("Resource not found");
            }

            switch (method) {
                case "exists":
                    {
                        restResponse.setData("exists!");
                        break;
                    }
                case "getData":
                    {
                        RestRequest<GetDataRequest> restRequest =
                                objectMapper.readValue(
                                        restRequestString,
                                        new TypeReference<RestRequest<GetDataRequest>>() {});

                        GetDataRequest getDataRequest = restRequest.getData();
                        GetDataResponse getDataResponse = resourceObj.getData(getDataRequest);

                        restResponse.setData(getDataResponse);
                        break;
                    }
                case "setData":
                    {
                        RestRequest<SetDataRequest> restRequest =
                                objectMapper.readValue(
                                        restRequestString,
                                        new TypeReference<RestRequest<SetDataRequest>>() {});

                        SetDataRequest setDataRequest = (SetDataRequest) restRequest.getData();
                        SetDataResponse setDataResponse =
                                (SetDataResponse) resourceObj.setData(setDataRequest);

                        restResponse.setData(setDataResponse);
                        break;
                    }
                case "call":
                    {
                        RestRequest<TransactionRequest> restRequest =
                                objectMapper.readValue(
                                        restRequestString,
                                        new TypeReference<RestRequest<TransactionRequest>>() {});

                        TransactionRequest transactionRequest =
                                (TransactionRequest) restRequest.getData();
                        TransactionResponse transactionResponse =
                                (TransactionResponse) resourceObj.call(transactionRequest);

                        restResponse.setData(transactionResponse);
                        break;
                    }
                case "sendTransaction":
                    {
                        RestRequest<TransactionRequest> restRequest =
                                objectMapper.readValue(
                                        restRequestString,
                                        new TypeReference<RestRequest<TransactionRequest>>() {});

                        TransactionRequest transactionRequest =
                                (TransactionRequest) restRequest.getData();
                        TransactionResponse transactionResponse =
                                (TransactionResponse)
                                        resourceObj.sendTransaction(transactionRequest);

                        restResponse.setData(transactionResponse);
                        break;
                    }
                default:
                    {
                        restResponse.setResult(-1);
                        restResponse.setMessage("Unsupport method: " + method);
                        break;
                    }
            }
        } catch (Exception e) {
            logger.warn("Process request error:", e);

            restResponse.setResult(-1);
            restResponse.setMessage(e.getLocalizedMessage());
        }

        return restResponse;
    }

    @RequestMapping(value = "/p2p/{method}", method = RequestMethod.POST)
    public P2PResponse<Object> handleP2pMessage(
            @PathVariable("method") String method, @RequestBody String restRequestString) {

        P2PResponse<Object> response = new P2PResponse<Object>();
        response.setVersion("0.1");
        response.setResult(0);

        logger.info("request string: {}", restRequestString);

        try {

            switch (method) {
                case "peer":
                    {
                        logger.info("request peer method");
                        response.setMessage("request peer method success");
                        break;
                    }

                case "stub":
                    {
                        logger.info("request stub method");
                        response.setMessage("request stub method success");
                        break;
                    }

                case "remoteCall":
                    {
                        logger.info("request remoteCall method");
                        response.setMessage("request remoteCall method success");
                        break;
                    }

                default:
                    {
                        response.setResult(-1);
                        response.setMessage("Unsupport method: " + method);
                        break;
                    }
            }

        } catch (Exception e) {
            logger.warn("Process request error:", e);

            response.setResult(-1);
            response.setMessage(e.getLocalizedMessage());
        }

        return response;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public void setNetworkManager(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }
}
