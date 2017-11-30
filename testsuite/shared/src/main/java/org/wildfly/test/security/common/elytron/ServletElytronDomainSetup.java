/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.security.common.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.wildfly.test.security.common.elytron.Utils.applyRemoveAllowReload;
import static org.wildfly.test.security.common.elytron.Utils.applyUpdate;

import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.elytron.ElytronExtension;

/**
 * Utility methods to create/remove simple security domains
 *
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class ServletElytronDomainSetup implements ServerSetupTask {

    private static final String DEFAULT_SECURITY_DOMAIN_NAME = "elytron-tests";

    private PathAddress httpAuthenticationAddress;

    private PathAddress undertowDomainAddress;

    private final String securityDomainName;

    public ServletElytronDomainSetup() {
        this(DEFAULT_SECURITY_DOMAIN_NAME);
    }

    public ServletElytronDomainSetup(final String securityDomainName) {
        this.securityDomainName = securityDomainName;
    }

    protected String getSecurityDomainName() {
        return securityDomainName;
    }

    protected String getUndertowDomainName() {
        return getSecurityDomainName();
    }

    protected String getHttpAuthenticationName() {
        return getSecurityDomainName();
    }

    protected String getDeploymentSecurityDomain() {
        return getSecurityDomainName();
    }

    @Override
    public void setup(final ManagementClient managementClient, final String containerId) throws Exception {
        httpAuthenticationAddress = PathAddress.pathAddress()
                .append(SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME)
                .append("http-authentication-factory", getHttpAuthenticationName());

        undertowDomainAddress = PathAddress.pathAddress()
                .append(SUBSYSTEM, "undertow")
                .append("application-security-domain", getUndertowDomainName());

        final ModelNode compositeOp = new ModelNode();
        compositeOp.get(OP).set(ModelDescriptionConstants.COMPOSITE);
        compositeOp.get(OP_ADDR).setEmptyList();

        ModelNode steps = compositeOp.get(STEPS);

        ModelNode addHttpAuthentication = Util.createAddOperation(httpAuthenticationAddress);
        addHttpAuthentication.get("security-domain").set(getSecurityDomainName());
        addHttpAuthentication.get("http-server-mechanism-factory").set("global");
        addHttpAuthentication.get("mechanism-configurations").get(0).get("mechanism-name").set("BASIC");
        addHttpAuthentication.get("mechanism-configurations").get(0).get("mechanism-realm-configurations").get(0).get("realm-name").set("TestingRealm");
        steps.add(addHttpAuthentication);

        ModelNode addUndertowDomain = Util.createAddOperation(undertowDomainAddress);
        addUndertowDomain.get("http-authentication-factory").set(getHttpAuthenticationName());
        steps.add(addUndertowDomain);

        applyUpdate(managementClient.getControllerClient(), compositeOp, false);
        // TODO: add {"allow-resource-service-restart" => true} to ejbRemoteAddress write-attribute operation once WFLY-8793 / JBEAP-10955 is fixed
        //       and remove this reload
        ServerReload.reloadIfRequired(managementClient.getControllerClient());
    }

    @Override
    public void tearDown(final ManagementClient managementClient, final String containerId) {
        // TODO: add {"allow-resource-service-restart" => true} to ejbRemoteAddress write-attribute operation once WFLY-8793 / JBEAP-10955 is fixed
        //       and remove this reload
        try {
            ServerReload.reloadIfRequired(managementClient.getControllerClient());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        applyRemoveAllowReload(managementClient.getControllerClient(), undertowDomainAddress, false);
        applyRemoveAllowReload(managementClient.getControllerClient(), httpAuthenticationAddress, false);
        // TODO: remove this reload once WFLY-8821 / JBEAP-11074 is fixed
        try {
            ServerReload.executeReloadAndWaitForCompletion(managementClient.getControllerClient());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
