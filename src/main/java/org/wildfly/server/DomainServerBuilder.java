/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.launcher.CommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainServerBuilder {

    private final CommandBuilder commandBuilder;

    private InetAddress managementAddress;
    private int managementPort;
    private String username;
    private String password;
    private OutputStream stdout;

    protected DomainServerBuilder(final CommandBuilder commandBuilder) {
        this.commandBuilder = commandBuilder;
        try {
            managementAddress = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        managementPort = 9990;
    }

    public DomainServerBuilder setManagementAddress(final String address) throws UnknownHostException {
        return setManagementAddress(InetAddress.getByName(address));
    }

    public DomainServerBuilder setManagementAddress(final InetAddress address) {
        managementAddress = address;
        return this;
    }

    public DomainServerBuilder setManagementPort(final int port) {
        managementPort = port;
        return this;
    }

    public DomainServerBuilder setStandardOut(final OutputStream stdout) {
        this.stdout = stdout;
        return this;
    }

    public DomainServer build() {
        final ModelControllerClient client = ModelControllerClient.Factory.create(managementAddress, managementPort);
        return new DomainServerImpl(commandBuilder, client, stdout);
    }

    private static class DomainServerImpl extends AbstractServer implements DomainServer {
        private final DomainClient domainClient;

        protected DomainServerImpl(final CommandBuilder commandBuilder, final ModelControllerClient client, final OutputStream stdout) {
            super(commandBuilder, client, stdout);
            domainClient = DomainClient.Factory.create(client);
        }

        @Override
        protected void stopServer() {
            ServerHelper.shutdownDomain(domainClient);
            safeClose(domainClient);
        }

        @Override
        protected boolean waitForStart(final Process process, final long timeout) throws IOException, InterruptedException {
            return ServerHelper.waitForDomain(process, domainClient, timeout);
        }

        @Override
        public boolean isRunning() {
            return ServerHelper.isDomainRunning(domainClient);
        }

        @Override
        public Set<Host> getHosts() {
            final DomainClient client = getClient();
            final Set<Host> hosts = new LinkedHashSet<>();
            // Get all the hosts
            final ModelNode op = Operations.createOperation(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
            op.get(ClientConstants.CHILD_TYPE).set(ClientConstants.HOST);
            final ModelNode result = safeExecuteForSuccess(client, op);
            for (ModelNode host : result.asList()) {
                hosts.add(new HostImpl(client, host.asString()));
            }
            return Collections.unmodifiableSet(hosts);
        }

        @Override
        public Host getHost(final String name) {
            // Ensure the host exists
            safeExecuteForSuccess(client, Operations.createReadResourceOperation(Operations.createAddress(ClientConstants.HOST, name)));
            return new HostImpl(getClient(), name);
        }

        @Override
        public DomainClient getClient() {
            return domainClient;
        }

        @Override
        public Set<ServerGroup> getServerGroups() {
            final DomainClient client = getClient();
            final Set<ServerGroup> serverGroups = new LinkedHashSet<>();
            // Get all the hosts
            final ModelNode op = Operations.createOperation(ClientConstants.READ_CHILDREN_NAMES_OPERATION);
            op.get(ClientConstants.CHILD_TYPE).set(ClientConstants.SERVER_GROUP);
            final ModelNode result = safeExecuteForSuccess(client, op);
            for (ModelNode serverGroup : result.asList()) {
                serverGroups.add(new ServerGroupImpl(client, serverGroup.asString()));
            }
            return Collections.unmodifiableSet(serverGroups);
        }

        @Override
        public ServerGroup getServerGroup(final String name) {
            // Ensure the server-group exists
            safeExecuteForSuccess(client, Operations.createReadResourceOperation(Operations.createAddress(ClientConstants.SERVER_GROUP, name)));
            return new ServerGroupImpl(getClient(), name);
        }

        @Override
        public String toString() {
            return "Domain: " + getRunningVersion();
        }
    }

    private static class HostImpl implements Host {
        private final DomainClient client;
        private final String name;
        private final ModelNode address;

        private HostImpl(final DomainClient client, final String name) {
            this.client = client;
            this.name = name;
            address = Operations.createAddress("host", name);
        }

        @Override
        public boolean isRunning() {
            return ServerHelper.isHostControllerRunning(client);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<Server> getServers() {
            final Set<Server> servers = new LinkedHashSet<>();
            // Get all the servers in the servers
            final ModelNode op = Operations.createOperation(ClientConstants.READ_CHILDREN_NAMES_OPERATION, address);
            op.get(ClientConstants.CHILD_TYPE).set("servers");
            final ModelNode result = safeExecuteForSuccess(client, op);
            for (ModelNode server : result.asList()) {
                final String serverName = server.asString();
                servers.add(new HostServer(client, name, serverName));
            }
            return Collections.unmodifiableSet(servers);
        }

        @Override
        public Server getServer(final String serverName) {
            // Ensure the server exists
            safeExecuteForSuccess(client, Operations.createReadResourceOperation(Operations.createAddress("host", name, "server", serverName)));
            return new HostServer(client, name, serverName);
        }
    }

    private static class ServerGroupImpl implements ServerGroup {
        private final DomainClient client;
        private final String name;
        private final ModelNode address;

        private ServerGroupImpl(final DomainClient client, final String name) {
            this.client = client;
            this.name = name;
            address = Operations.createAddress(ClientConstants.SERVER_GROUP, name);
        }

        @Override
        public void reloadServers() {
            final ModelNode op = Operations.createOperation("reload-servers", address);
            op.get("blocking").set(true);
            safeExecuteForSuccess(client, op);
        }

        @Override
        public void restartServers() {
            final ModelNode op = Operations.createOperation("restart-servers", address);
            op.get("blocking").set(true);
            safeExecuteForSuccess(client, op);
        }

        @Override
        public void resumeServers() {
            safeExecuteForSuccess(client, Operations.createOperation("resume-servers", address));
        }

        @Override
        public void startServers() {
            final ModelNode op = Operations.createOperation("start-servers", address);
            op.get("blocking").set(true);
            safeExecuteForSuccess(client, op);
        }

        @Override
        public void stopServers() {
            final ModelNode op = Operations.createOperation("stop-servers", address);
            op.get("blocking").set(true);
            safeExecuteForSuccess(client, op);
        }

        @Override
        public void suspendServers(final int timeout) {
            final ModelNode op = Operations.createOperation("suspend-servers", address);
            op.get("timeout").set(timeout);
            safeExecuteForSuccess(client, op);
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static class HostServer implements Server {
        private final DomainClient client;
        private final ModelNode address;

        private HostServer(final DomainClient client, final String hostName, final String serverName) {
            this.client = client;
            address = Operations.createAddress("host", hostName, "server-config", serverName);
        }

        @Override
        public void start(final long timeout) throws IOException, InterruptedException {
            final ModelNode op = Operations.createOperation("start", address);
            op.get("blocking").set(true);
            executeForSuccess(client, op);
        }

        @Override
        public void stop() {
            final ModelNode op = Operations.createOperation("stop", address);
            op.get("blocking").set(true);
            safeExecuteForSuccess(client, op);
        }

        @Override
        public ModelControllerClient getClient() {
            return client;
        }

        @Override
        public boolean isRunning() {
            final ModelNode op = Operations.createReadAttributeOperation(address, "status");
            try {
                final ModelNode result = executeForSuccess(client, op);
                return "STARTED".equals(Operations.readResult(result).asString());
            } catch (IOException ignore) {

            }
            return false;
        }
    }

    private static ModelNode safeExecuteForSuccess(final ModelControllerClient client, final ModelNode op) {
        try {
            return executeForSuccess(client, op);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ModelNode executeForSuccess(final ModelControllerClient client, final ModelNode op) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            // TODO (jrp) use a better exception
            throw new IOException(Operations.getFailureDescription(result).asString());
        }
        return Operations.readResult(result);
    }
}
