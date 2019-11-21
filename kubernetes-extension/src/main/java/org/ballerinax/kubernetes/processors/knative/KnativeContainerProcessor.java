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
package org.ballerinax.kubernetes.processors.knative;

import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.ballerinax.kubernetes.KubernetesConstants;
import org.ballerinax.kubernetes.exceptions.KubernetesPluginException;
import org.ballerinax.kubernetes.models.knative.KnativeContainerModel;
import org.ballerinax.kubernetes.models.knative.KnativeContext;
import org.ballerinax.kubernetes.processors.AbstractAnnotationProcessor;
import org.wso2.ballerinalang.compiler.tree.BLangAnnotationAttachment;
import org.wso2.ballerinalang.compiler.tree.BLangIdentifier;
import org.wso2.ballerinalang.compiler.tree.BLangService;
import org.wso2.ballerinalang.compiler.tree.BLangSimpleVariable;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangExpression;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.tree.types.BLangUserDefinedType;

import java.util.List;

import static org.ballerinax.kubernetes.KubernetesConstants.SVC_POSTFIX;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getIntValue;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getMap;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getStringValue;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getValidName;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.isBlank;

/**
 *  Knative ConfigMap annotation processor.
 */
public class KnativeContainerProcessor extends AbstractAnnotationProcessor {

    @Override
    public void processAnnotation(ServiceNode serviceNode, AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        BLangService bService = (BLangService) serviceNode;
        for (BLangExpression attachedExpr : bService.getAttachedExprs()) {
            // If not anonymous endpoint throw error.
            if (attachedExpr instanceof BLangSimpleVarRef) {
                throw new KubernetesPluginException("adding @kubernetes:Service{} annotation to a service is only " +
                        "supported when the service has an anonymous listener");
            }

        }
        KnativeContainerModel serviceModel = getServiceModelFromAnnotation(attachmentNode);
        if (isBlank(serviceModel.getName())) {
            serviceModel.setName(getValidName(serviceNode.getName().getValue()) + SVC_POSTFIX);
        }

        // If service annotation port is not empty, then listener port is used for the k8s svc target port while
        // service annotation port is used for k8s port.
        // If service annotation port is empty, then listener port is used for both port and target port of the k8s
        // svc.
        BLangTypeInit bListener = (BLangTypeInit) bService.getAttachedExprs().get(0);
        if (serviceModel.getPort() == -1) {
            serviceModel.setPort(extractPort(bListener));
        }

        if (serviceModel.getTargetPort() == -1) {
            serviceModel.setTargetPort(extractPort(bListener));
        }

        setServiceProtocol(serviceModel, bListener);

        KnativeContext.getInstance().getDataHolder().addBListenerToK8sServiceMap(serviceNode.getName().getValue(),
                serviceModel);
    }
    @Override
    public void processAnnotation(SimpleVariableNode variableNode, AnnotationAttachmentNode attachmentNode)
            throws KubernetesPluginException {
        KnativeContainerModel serviceModel = getServiceModelFromAnnotation(attachmentNode);
        if (isBlank(serviceModel.getName())) {
            serviceModel.setName(getValidName(variableNode.getName().getValue()) + SVC_POSTFIX);
        }

        // If service annotation port is not empty, then listener port is used for the k8s svc target port while
        // service annotation port is used for k8s port.
        // If service annotation port is empty, then listener port is used for both port and target port of the k8s
        // svc.
        BLangTypeInit bListener = (BLangTypeInit) ((BLangSimpleVariable) variableNode).expr;
        if (serviceModel.getPort() == -1) {
            serviceModel.setPort(extractPort(bListener));
        }

        if (serviceModel.getTargetPort() == -1) {
            serviceModel.setTargetPort(extractPort(bListener));
        }

        setServiceProtocol(serviceModel, bListener);

        KnativeContext.getInstance().getDataHolder().addBListenerToK8sServiceMap(variableNode.getName().getValue()
                , serviceModel);
    }
    private int extractPort(BLangTypeInit bListener) throws KubernetesPluginException {
        try {
            return Integer.parseInt(bListener.argsExpr.get(0).toString());
        } catch (NumberFormatException e) {
            throw new KubernetesPluginException("unable to parse port/targetPort for the service: " +
                    bListener.argsExpr.get(0).toString());
        }
    }
    private void setServiceProtocol(KnativeContainerModel serviceModel, BLangTypeInit bListener) {
        if (null != bListener.userDefinedType) {
            serviceModel.setProtocol(bListener.userDefinedType.getPackageAlias().getValue());
        } else {
            BLangIdentifier packageAlias =
                    ((BLangUserDefinedType) ((BLangSimpleVariable) bListener.parent).typeNode).getPackageAlias();
            serviceModel.setProtocol(packageAlias.getValue());
        }
        if ("http".equals(serviceModel.getProtocol())) {
            // Add http config
            if (bListener.argsExpr.size() == 2) {
                if (bListener.argsExpr.get(1) instanceof BLangRecordLiteral) {
                    BLangRecordLiteral bConfigRecordLiteral = (BLangRecordLiteral) bListener.argsExpr.get(1);
                    List<BLangRecordLiteral.BLangRecordKeyValue> listenerConfig =
                            bConfigRecordLiteral.getKeyValuePairs();
                    serviceModel.setProtocol(isHTTPS(listenerConfig) ? "https" : "http");
                }
            }
        }
    }
    private boolean isHTTPS(List<BLangRecordLiteral.BLangRecordKeyValue> listenerConfig) {
        for (BLangRecordLiteral.BLangRecordKeyValue keyValue : listenerConfig) {
            String key = keyValue.getKey().toString();
            if ("secureSocket".equals(key)) {
                return true;
            }
        }

        return false;
    }
    private KnativeContainerModel getServiceModelFromAnnotation(AnnotationAttachmentNode attachmentNode) throws
            KubernetesPluginException {
        KnativeContainerModel serviceModel = new KnativeContainerModel();
        List<BLangRecordLiteral.BLangRecordKeyValue> keyValues =
                ((BLangRecordLiteral) ((BLangAnnotationAttachment) attachmentNode).expr).getKeyValuePairs();
        for (BLangRecordLiteral.BLangRecordKeyValue keyValue : keyValues) {
            KnativeContainerProcessor.ServiceConfiguration serviceConfiguration =
                    KnativeContainerProcessor.ServiceConfiguration.valueOf(keyValue.getKey().toString());
            switch (serviceConfiguration) {
                case name:
                    serviceModel.setName(getValidName(getStringValue(keyValue.getValue())));
                    break;
                case labels:
                    serviceModel.setLabels(getMap(keyValue.getValue()));
                    break;
                case annotations:
                    serviceModel.setAnnotations(getMap(keyValue.getValue()));
                    break;
                case serviceType:
                    serviceModel.setServiceType(KubernetesConstants.ServiceType.valueOf(
                            getStringValue(keyValue.getValue())).name());
                    break;
                case portName:
                    serviceModel.setPortName(getStringValue(keyValue.getValue()));
                    break;
                case port:
                    serviceModel.setPort(getIntValue(keyValue.getValue()));
                    break;
                case targetPort:
                    serviceModel.setTargetPort(getIntValue(keyValue.getValue()));
                    break;
                case sessionAffinity:
                    serviceModel.setSessionAffinity(getStringValue(keyValue.getValue()));
                    break;
                default:
                    break;
            }
        }
        return serviceModel;
    }
    private enum ServiceConfiguration {
        name,
        labels,
        annotations,
        serviceType,
        portName,
        port,
        targetPort,
        sessionAffinity
    }

}
