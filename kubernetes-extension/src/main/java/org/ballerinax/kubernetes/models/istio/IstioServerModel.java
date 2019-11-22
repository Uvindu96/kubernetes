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

package org.ballerinax.kubernetes.models.istio;

import java.util.LinkedList;
import java.util.List;

/**
 * Istio gateway server annotation model class.
 *
 * @since 0.985.0
 */
public class IstioServerModel {
    private IstioPortModel port;
    private List<String> hosts;
    private TLSOptions tls;
    
    public IstioPortModel getPort() {
        return port;
    }
    
    public void setPort(IstioPortModel port) {
        this.port = port;
    }
    
    public TLSOptions getTls() {
        return tls;
    }
    
    public void setTls(TLSOptions tls) {
        this.tls = tls;
    }
    
    public List<String> getHosts() {
        return hosts;
    }
    
    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }
    
    /**
     * Istio gateway server TLS option annotation model class.
     *
     * @since 0.985.0
     */
    public static class TLSOptions {
        private boolean httpsRedirect = false;
        private String mode = "PASSTHROUGH";
        private String serverCertificate;
        private String privateKey;
        private String caCertificates;
        private List<String> subjectAltNames = new LinkedList<>();
    
        public boolean isHttpsRedirect() {
            return httpsRedirect;
        }
    
        public void setHttpsRedirect(boolean httpsRedirect) {
            this.httpsRedirect = httpsRedirect;
        }
    
        public String getMode() {
            return mode;
        }
    
        public void setMode(String mode) {
            this.mode = mode;
        }
    
        public String getServerCertificate() {
            return serverCertificate;
        }
    
        public void setServerCertificate(String serverCertificate) {
            this.serverCertificate = serverCertificate;
        }
    
        public String getPrivateKey() {
            return privateKey;
        }
    
        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }
    
        public String getCaCertificates() {
            return caCertificates;
        }
    
        public void setCaCertificates(String caCertificates) {
            this.caCertificates = caCertificates;
        }
    
        public List<String> getSubjectAltNames() {
            return subjectAltNames;
        }
    
        public void setSubjectAltNames(List<String> subjectAltNames) {
            this.subjectAltNames = subjectAltNames;
        }
    }
}
