package com.webank.wecross.stub.jdchain;

import com.jd.blockchain.ledger.OperationResult;
import com.jd.blockchain.ledger.PreparedTransaction;
import com.jd.blockchain.ledger.TransactionTemplate;
import com.jd.blockchain.sdk.BlockchainService;
import com.webank.wecross.resource.EventCallback;
import com.webank.wecross.resource.GetDataRequest;
import com.webank.wecross.resource.GetDataResponse;
import com.webank.wecross.resource.Path;
import com.webank.wecross.resource.SetDataRequest;
import com.webank.wecross.resource.SetDataResponse;
import com.webank.wecross.resource.TransactionRequest;
import com.webank.wecross.resource.TransactionResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JDChainContractResource extends JDChainResource {

    private Logger logger = LoggerFactory.getLogger(JDChainContractResource.class);
    private Boolean isInit = false;
    private String contractAddress;

    public String getContractAddress() {
        return contractAddress;
    }

    public void setContractAddress(String contractAddress) {
        this.contractAddress = contractAddress;
    }

    @Override
    public Path getPath() {
        return null;
    }

    @Override
    public GetDataResponse getData(GetDataRequest request) {
        return null;
    }

    @Override
    public SetDataResponse setData(SetDataRequest request) {
        return null;
    }

    public com.jd.blockchain.ledger.TransactionResponse commit(TransactionTemplate txTpl) {
        PreparedTransaction ptx = txTpl.prepare();
        ptx.sign(adminKey);
        return ptx.commit();
    }

    private CtClass dynamicGenerateClass(TransactionRequest request) {
        ClassPool pool = ClassPool.getDefault();
        String classname =
                "com.jd.chain.contract.Class" + UUID.randomUUID().toString().replaceAll("-", "");
        CtClass ctClass = pool.makeInterface(classname);
        CtClass[] parameterType = null;
        if (request.getArgs().length == 0) {
            parameterType = new CtClass[] {};
        } else {
            parameterType = new CtClass[request.getArgs().length];

            for (int i = 0; i < request.getArgs().length; i++) {

                String name = request.getArgs()[i].getClass().getName();
                try {
                    parameterType[i] = pool.getCtClass(name);
                } catch (NotFoundException e) {
                    logger.error("get class exception:{}", e);
                    return null;
                }
            }
        }
        String returnType = "java.lang.String";
        CtMethod ctMethod = null;
        try {
            ctMethod =
                    new CtMethod(
                            pool.getCtClass(returnType),
                            request.getMethod(),
                            parameterType,
                            ctClass);
        } catch (NotFoundException e) {
            logger.error("create method:{} failed:{}", request.getMethod(), e);
            return null;
        }
        ClassFile ccFile = ctClass.getClassFile();
        ConstPool constpool = ccFile.getConstPool();
        AnnotationsAttribute attrForClass =
                new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
        Annotation annotForClass = new Annotation("com.jd.blockchain.contract.Contract", constpool);
        attrForClass.addAnnotation(annotForClass);
        ccFile.addAttribute(attrForClass);

        AnnotationsAttribute attrForMethod =
                new AnnotationsAttribute(constpool, AnnotationsAttribute.visibleTag);
        Annotation annotForMethod =
                new Annotation("com.jd.blockchain.contract.ContractEvent", constpool);
        annotForMethod.addMemberValue(
                "name", new StringMemberValue(request.getMethod(), constpool));
        attrForMethod.addAnnotation(annotForMethod);
        ctMethod.getMethodInfo().addAttribute(attrForMethod);
        try {
            ctClass.addMethod(ctMethod);
        } catch (CannotCompileException e) {
            logger.error("add method:{} failed:{}", request.getMethod(), e);
            return null;
        }
        return ctClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public TransactionResponse call(TransactionRequest request) {

        logger.debug("request parameter:{}", request.toString());
        JDChainResponse response = new JDChainResponse();
        int channelCount = this.blockchainService.size();
        if (channelCount == 0) {
            response.setErrorCode(-1);
            response.setErrorMessage("has no gate way to connect");
            return response;
        }

        CtClass ctClass = this.dynamicGenerateClass(request);
        if (ctClass == null) {
            response.setErrorCode(-2);
            response.setErrorMessage("generate class failed");
            return response;
        }
        logger.debug("request method:{} dynamic generate class success ", request.getMethod());
        SecureRandom secureRandom = new SecureRandom();
        Integer randNum = secureRandom.nextInt(channelCount);
        for (int index = 0; index < channelCount; ++index) {

            Integer useIndex = (randNum + index) % channelCount;
            BlockchainService blockChainService = blockchainService.get(useIndex);
            logger.debug(
                    "request method:{} get block chanin service id:{} success ",
                    request.getMethod(),
                    useIndex);
            TransactionTemplate txTpl = blockChainService.newTransaction(ledgerHash);
            logger.debug(
                    "request method:{}  new transaction  success ledgerhash:{}",
                    request.getMethod(),
                    ledgerHash);
            Object object;
            try {
                object = txTpl.contract(contractAddress, ctClass.toClass());
                Method[] methods = object.getClass().getMethods();
                for (int i = 0; i < methods.length; i++) {
                    Method method = methods[i];
                    if (method != null && request.getMethod().equals(method.getName())) {
                        try {
                            logger.debug("invoke  method:{}", request.getMethod());
                            method.invoke(object, request.getArgs());

                        } catch (IllegalAccessException e) {
                            response.setErrorCode(-3);
                            logger.error("invoke method failed:{}", request.getMethod());
                            response.setErrorMessage("invoke method failed");
                            return response;

                        } catch (IllegalArgumentException e) {
                            response.setErrorCode(-3);
                            logger.error("invoke method failed:{}", request.getMethod());
                            response.setErrorMessage("invoke method failed");
                            return response;
                        } catch (InvocationTargetException e) {
                            response.setErrorCode(-3);
                            logger.error("invoke method failed:{}", request.getMethod());
                            response.setErrorMessage("invoke method failed");
                            return response;
                        }

                        logger.debug(
                                "invoke  method:{} success begin to commit", request.getMethod());
                        com.jd.blockchain.ledger.TransactionResponse txResponse = commit(txTpl);
                        logger.debug(
                                "invoke  method:{} success end to commit", request.getMethod());
                        if (txResponse.isSuccess()) {

                            logger.debug(
                                    "request method:{} parameter:{} invoke success hash:{} result size:{}",
                                    request.getMethod(),
                                    request.getArgs().toString(),
                                    txResponse.getBlockHash().toBase58(),
                                    txResponse.getOperationResults().length);
                            response.setErrorCode(0);
                            response.setErrorMessage("");
                            response.setHash(txResponse.getBlockHash().toBase58());
                            List<Object> resultList = new ArrayList<Object>();
                            OperationResult[] operationResult = txResponse.getOperationResults();
                            for (int j = 0; j < operationResult.length; j++) {
                                OperationResult result = operationResult[j];
                                byte[] value = result.getResult().getValue().toBytes();
                                resultList.add(new String(value));
                            }
                            response.setResult(resultList.toArray());

                            logger.debug(
                                    "request parameter:{} response:{}",
                                    request.toString(),
                                    response.toString());

                            return response;
                        } else {
                            response.setErrorCode(-4);
                            logger.error("invoke method failed:{}", request.getMethod());
                            response.setErrorMessage("invoke method failed");
                            return response;
                        }
                    }
                }
            } catch (CannotCompileException e) {
                response.setErrorCode(-5);
                response.setErrorMessage("can not cpmpile");
                return response;
            }
        }
        return response;
    }

    @Override
    public TransactionResponse sendTransaction(TransactionRequest request) {
        return this.call(request);
    }

    @Override
    public void registerEventHandler(EventCallback callback) {}

    @Override
    public TransactionRequest createRequest() {
        return new JDChainRequest();
    }
}
