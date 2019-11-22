/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinax.kubernetes.processors;

import org.apache.commons.codec.binary.Base64;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.ballerinax.kubernetes.exceptions.KubernetesPluginException;
import org.ballerinax.kubernetes.models.IngressModel;
import org.ballerinax.kubernetes.models.KubernetesContext;
import org.ballerinax.kubernetes.models.SecretModel;
import org.ballerinax.kubernetes.utils.KubernetesUtils;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangNamedArgsExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ballerinax.kubernetes.KubernetesConstants.ANONYMOUS_POSTFIX;
import static org.ballerinax.kubernetes.KubernetesConstants.BALLERINA_HOME;
import static org.ballerinax.kubernetes.KubernetesConstants.INGRESS_HOSTNAME_POSTFIX;
import static org.ballerinax.kubernetes.KubernetesConstants.INGRESS_POSTFIX;
import static org.ballerinax.kubernetes.KubernetesConstants.LISTENER_PATH_VARIABLE;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getBooleanValue;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getMap;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getStringValue;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getValidName;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.isBlank;

/**
 * Ingress annotation processor.
 */
public class IngressAnnotationProcessor extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(SimpleVariableNode variableNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        IngressModel ingressModel = getIngressModelFromAnnotation(attachmentNode);
        String listenerName = variableNode.getName().getValue();
        if (isBlank(ingressModel.getName())) {
            ingressModel.setName(getValidName(listenerName) + INGRESS_POSTFIX);
        }
        if (isBlank(ingressModel.getHostname())) {
            ingressModel.setHostname(getValidName(listenerName) + INGRESS_HOSTNAME_POSTFIX);
        }
        ingressModel.setListenerName(listenerName);

        BLangTypeInit bListener = (BLangTypeInit) ((BLangSimpleVariable) variableNode).expr;
        if (bListener.argsExpr.size() == 2) {
            if (bListener.argsExpr.get(1) instanceof BLangRecordLiteral) {
                BLangRecordLiteral bConfigRecordLiteral = (BLangRecordLiteral) bListener.argsExpr.get(1);
                List<BLangRecordLiteral.BLangRecordKeyValue> listenerConfig = bConfigRecordLiteral.getKeyValuePairs();
                processListener(listenerName, listenerConfig);
            } else if (bListener.argsExpr.get(1) instanceof BLangNamedArgsExpression) {
                // expression is in config = {} format.
                List<BLangRecordLiteral.BLangRecordKeyValue> listenerConfig =
                        ((BLangRecordLiteral) ((BLangNamedArgsExpression) bListener.argsExpr.get(1)).expr)
                                .getKeyValuePairs();
                processListener(listenerName, listenerConfig);
            }
        }

        KubernetesContext.getInstance().getDataHolder().addIngressModel(ingressModel);
    }

    /**
     * Extract key-store/trust-store file location from listener.
     *
     * @param listenerName          Listener name
     * @param secureSocketKeyValues secureSocket annotation struct
     * @return List of @{@link SecretModel} objects
     */
    private Set<SecretModel> processSecureSocketAnnotation(String listenerName, List<BLangRecordLiteral
            .BLangRecordKeyValue> secureSocketKeyValues) throws KubernetesPluginException {
        Set<SecretModel> secrets = new HashSet<>();
        String keyStoreFile = null;
        String trustStoreFile = null;
        for (BLangRecordLiteral.BLangRecordKeyValue keyValue : secureSocketKeyValues) {
            //extract file paths.
            String key = keyValue.getKey().toString();
            if ("keyStore".equals(key)) {
                keyStoreFile = extractFilePath(keyValue);
            } else if ("trustStore".equals(key)) {
                trustStoreFile = extractFilePath(keyValue);
            }
        }
        if (keyStoreFile != null && trustStoreFile != null) {
            if (getMountPath(keyStoreFile).equals(getMountPath(trustStoreFile))) {
                // trust-store and key-store mount to same path
                String keyStoreContent = readSecretFile(keyStoreFile);
                String trustStoreContent = readSecretFile(trustStoreFile);
                SecretModel secretModel = new SecretModel();
                secretModel.setName(getValidName(listenerName) + "-secure-socket");
                secretModel.setMountPath(getMountPath(keyStoreFile));
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put(String.valueOf(Paths.get(keyStoreFile).getFileName()), keyStoreContent);
                dataMap.put(String.valueOf(Paths.get(trustStoreFile).getFileName()), trustStoreContent);
                secretModel.setData(dataMap);
                secrets.add(secretModel);
                return secrets;
            }
        }
        if (keyStoreFile != null) {
            String keyStoreContent = readSecretFile(keyStoreFile);
            SecretModel secretModel = new SecretModel();
            secretModel.setName(getValidName(listenerName) + "-keystore");
            secretModel.setMountPath(getMountPath(keyStoreFile));
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(String.valueOf(Paths.get(keyStoreFile).getFileName()), keyStoreContent);
            secretModel.setData(dataMap);
            secrets.add(secretModel);
        }
        if (trustStoreFile != null) {
            String trustStoreContent = readSecretFile(trustStoreFile);
            SecretModel secretModel = new SecretModel();
            secretModel.setName(getValidName(listenerName) + "-truststore");
            secretModel.setMountPath(getMountPath(trustStoreFile));
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(String.valueOf(Paths.get(trustStoreFile).getFileName()), trustStoreContent);
            secretModel.setData(dataMap);
            secrets.add(secretModel);
        }
        return secrets;
    }

    private String readSecretFile(String filePath) throws KubernetesPluginException {
        if (filePath.contains("${ballerina.home}")) {
            // Resolve variable locally before reading file.
            String ballerinaHome = System.getProperty("ballerina.home");
            filePath = filePath.replace("${ballerina.home}", ballerinaHome);
        }
        Path dataFilePath = Paths.get(filePath);
        return Base64.encodeBase64String(KubernetesUtils.readFileContent(dataFilePath));
    }

    private String getMountPath(String mountPath) throws KubernetesPluginException {
        Path parentPath = Paths.get(mountPath).getParent();
        if (parentPath != null && ".".equals(parentPath.toString())) {
            // Mounts to the same path overriding the source file.
            throw new KubernetesPluginException("Invalid path: " + mountPath + ". " +
                    "Providing relative path in the same level as source file is not supported with " +
                    "@kubernetes:Ingress annotations. Please create a subfolder and provide the relative path. " +
                    "eg: './security/ballerinaKeystore.p12'");
        }
        if (!Paths.get(mountPath).isAbsolute()) {
            mountPath = BALLERINA_HOME + File.separator + mountPath;
        }
        return String.valueOf(Paths.get(mountPath).getParent());
    }

    private String extractFilePath(BLangRecordLiteral.BLangRecordKeyValue keyValue) {
        List<BLangRecordLiteral.BLangRecordKeyValue> keyStoreConfigs = ((BLangRecordLiteral) keyValue
                .valueExpr).getKeyValuePairs();
        for (BLangRecordLiteral.BLangRecordKeyValue keyStoreConfig : keyStoreConfigs) {
            String configKey = keyStoreConfig.getKey().toString();
            if (LISTENER_PATH_VARIABLE.equals(configKey)) {
                return keyStoreConfig.getValue().toString();
            }
        }
        return null;
    }

    private IngressModel getIngressModelFromAnnotation(AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        IngressModel ingressModel = new IngressModel();
        List<BLangRecordLiteral.BLangRecordKeyValue> keyValues =
                ((BLangRecordLiteral) ((BLangAnnotationAttachment) attachmentNode).expr).getKeyValuePairs();
        for (BLangRecordLiteral.BLangRecordKeyValue keyValue : keyValues) {
            IngressConfiguration ingressConfiguration =
                    IngressConfiguration.valueOf(keyValue.getKey().toString());
            switch (ingressConfiguration) {
                case name:
                    ingressModel.setName(getValidName(getStringValue(keyValue.getValue())));
                    break;
                case labels:
                    ingressModel.setLabels(getMap(keyValue.getValue()));
                    break;
                case annotations:
                    ingressModel.setAnnotations(getMap(keyValue.getValue()));
                    break;
                case hostname:
                    ingressModel.setHostname(getStringValue(keyValue.getValue()));
                    break;
                case path:
                    ingressModel.setPath(getStringValue(keyValue.getValue()));
                    break;
                case targetPath:
                    ingressModel.setTargetPath(getStringValue(keyValue.getValue()));
                    break;
                case ingressClass:
                    ingressModel.setIngressClass(getStringValue(keyValue.getValue()));
                    break;
                case enableTLS:
                    ingressModel.setEnableTLS(getBooleanValue(keyValue.getValue()));
                    break;
                default:
                    break;
            }
        }
        return ingressModel;
    }

    private void processListener(String listenerName, List<BLangRecordLiteral.BLangRecordKeyValue> listenerConfig)
            throws KubernetesPluginException {
        for (BLangRecordLiteral.BLangRecordKeyValue keyValue : listenerConfig) {
            String key = keyValue.getKey().toString();
            if ("secureSocket".equals(key)) {
                List<BLangRecordLiteral.BLangRecordKeyValue> sslKeyValues =
                        ((BLangRecordLiteral) keyValue.valueExpr).getKeyValuePairs();
                Set<SecretModel> secretModels = processSecureSocketAnnotation(listenerName, sslKeyValues);
                KubernetesContext.getInstance().getDataHolder().addListenerSecret(listenerName, secretModels);
                KubernetesContext.getInstance().getDataHolder().addSecrets(secretModels);
            }
        }
    }

    @Override
    public void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        BLangService bService = (BLangService) serviceNode;
        for (BLangExpression attachedExpr : bService.getAttachedExprs()) {
            if (attachedExpr instanceof BLangTypeInit) {
                throw new KubernetesPluginException("adding @kubernetes:Ingress{} annotation to a service is only " +
                        "supported when service is bind to an anonymous listener");
            }
        }
        IngressModel ingressModel = getIngressModelFromAnnotation(attachmentNode);

        //processing anonymous listener
        String listenerName = serviceNode.getName().getValue();
        if (isBlank(ingressModel.getName())) {
            ingressModel.setName(getValidName(listenerName) + ANONYMOUS_POSTFIX + INGRESS_POSTFIX);
        }
        if (isBlank(ingressModel.getHostname())) {
            ingressModel.setHostname(getValidName(listenerName) + INGRESS_HOSTNAME_POSTFIX);
        }
        ingressModel.setListenerName(listenerName);

        // Add http config
        for (BLangExpression attachedExpr : bService.getAttachedExprs()) {
            if (attachedExpr instanceof BLangTypeInit) {
                BLangTypeInit bListener = (BLangTypeInit) attachedExpr;
                if (bListener.argsExpr.size() == 2) {
                    if (bListener.argsExpr.get(1) instanceof BLangRecordLiteral) {
                        BLangRecordLiteral bConfigRecordLiteral = (BLangRecordLiteral) bListener.argsExpr.get(1);
                        List<BLangRecordLiteral.BLangRecordKeyValue> listenerConfig =
                                bConfigRecordLiteral.getKeyValuePairs();
                        processListener(listenerName, listenerConfig);
                    }
                }
            }
        }

        KubernetesContext.getInstance().getDataHolder().addIngressModel(ingressModel);

    }

    /**
     * Enum  for ingress configurations.
     */
    private enum IngressConfiguration {
        name,
        labels,
        annotations,
        hostname,
        path,
        targetPath,
        ingressClass,
        enableTLS,
    }
}

