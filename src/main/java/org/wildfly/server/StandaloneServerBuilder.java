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

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.CommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandaloneServerBuilder {

    private final CommandBuilder commandBuilder;

    private InetAddress managementAddress;
    private int managementPort;
    private String username;
    private String password;
    private OutputStream stdout;

    protected StandaloneServerBuilder(final CommandBuilder commandBuilder) {
        this.commandBuilder = commandBuilder;
        try {
            managementAddress = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        managementPort = 9990;
    }

    public StandaloneServerBuilder setManagementAddress(final String address) throws UnknownHostException {
        return setManagementAddress(InetAddress.getByName(address));
    }

    public StandaloneServerBuilder setManagementAddress(final InetAddress address) {
        managementAddress = address;
        return this;
    }

    public StandaloneServerBuilder setManagementPort(final int port) {
        managementPort = port;
        return this;
    }

    public StandaloneServerBuilder setStandardOut(final OutputStream stdout) {
        this.stdout = stdout;
        return this;
    }

    public StandaloneServer build() {
        final ModelControllerClient client = ModelControllerClient.Factory.create(managementAddress, managementPort);
        return new StandaloneServerImpl(commandBuilder, client, stdout);
    }

    private static class StandaloneServerImpl extends AbstractServer implements StandaloneServer {

        private StandaloneServerImpl(final CommandBuilder commandBuilder, final ModelControllerClient client, final OutputStream stdout) {
            super(commandBuilder, client, stdout);
        }

        @Override
        protected void stopServer() {
            ServerHelper.shutdownStandalone(client);
            safeClose(client);
        }

        @Override
        protected boolean waitForStart(final Process process, final long timeout) throws IOException, InterruptedException {
            return ServerHelper.waitForStandalone(process, client, timeout);
        }

        @Override
        public boolean isRunning() {
            return ServerHelper.isStandaloneRunning(client);
        }

        @Override
        public String toString() {
            return "Standalone : " + getRunningVersion();
        }
    }
}
