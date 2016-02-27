/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * A helper used to query or manage certain aspects of a running WildFly container.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class ServerHelper {
    public static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    private static final Logger LOGGER = Logger.getLogger(ServerHelper.class);

    static {
        EMPTY_ADDRESS.protect();
    }

    /**
     * Validates the path is a valid WildFly directory by checking for a {@code jboss-modules.jar}.
     *
     * @param path the path to check
     *
     * @return {@code true} if the path appears to be a valid WildFly directory, otherwise {@code false}.
     */
    static boolean isValidWildFlyHome(final Path path) {
        return Files.exists(path.resolve("jboss-modules.jar"));
    }

    /**
     * Waits for each required server in a domain to be started. If the process has been destroyed or the timeout limit
     * is reached {@code false} is returned.
     *
     * @param process        the domain process
     * @param client         the domain client used to communicate with the server
     * @param startupTimeout the timeout, in seconds, to wait for all servers to start
     *
     * @return {@code true} if all severs were started within the timeout limit, otherwise {@code false}
     *
     * @throws InterruptedException if the interrupted while waiting for the servers to start
     */
    static boolean waitForDomain(final Process process, final DomainClient client, final long startupTimeout) throws InterruptedException {
        long timeout = startupTimeout * 1000;
        final long sleep = 100;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isDomainRunning(client)) {
                return true;
            }
            timeout -= (System.currentTimeMillis() - before);
            if (ProcessHelper.processHasDied(process)) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        return false;
    }

    /**
     * Checks whether a domain server is running.
     *
     * @param client the client used to query the server
     *
     * @return {@code true} if all required servers in the domain are running otherwise {@code false}
     */
    static boolean isDomainRunning(final DomainClient client) {
        return isDomainRunning(client, false);
    }

    static boolean isHostControllerRunning(final DomainClient client) {
        try {
            final ModelNode hostAddress = determineHostAddress(client);
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(hostAddress, "host-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not determine if host was running", e);
        }
        return false;
    }

    static String determineHostName(final DomainClient client) throws IOException {
        ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "local-host-name"));
        if (Operations.isSuccessfulOutcome(response)) {
            return Operations.readResult(response).asString();
        }
        throw new RuntimeException("Could not determine host name; " + Operations.getFailureDescription(response).asString());
    }

    static ModelNode determineHostAddress(final DomainClient client) throws IOException {
        return Operations.createAddress("host", determineHostName(client));
    }

    /**
     * Shuts down a domain server. Each server in the domain is stopped, then the domain server itself is stopped.
     *
     * @param client the client used to issue the shutdown operations
     */
    static void shutdownDomain(final DomainClient client) {
        final ModelNode address = Operations.createAddress("host", "master");
        try {
            // First shutdown the servers
            ModelNode op = Operations.createOperation("stop-servers");
            ModelNode response = client.execute(op);
            if (Operations.isSuccessfulOutcome(response)) {
                op = Operations.createOperation("shutdown", address);
                response = client.execute(op);
                if (Operations.isSuccessfulOutcome(response)) {
                    // Wait until the process has died
                    while (true) {
                        if (isDomainRunning(client, true)) {
                            try {
                                TimeUnit.MILLISECONDS.sleep(20L);
                            } catch (InterruptedException e) {
                                LOGGER.debug("Interrupted during sleep", e);
                            }
                        } else {
                            break;
                        }
                    }
                } else {
                    LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
                }
            } else {
                LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
            }
        } catch (IOException e) {
            LOGGER.debug("Error shutting down domain", e);
        }
    }

    /**
     * Waits for a standalone server to start. If the process has been destroyed or the timeout limit is reached
     * {@code false} is returned.
     *
     * @param process        the process for the standalone server
     * @param client         the client used to communicate with the server
     * @param startupTimeout the startup timeout in seconds
     *
     * @return {@code true} if the server was started within the timeout limit otherwise {@code false}
     *
     * @throws InterruptedException if the interrupted while waiting for the server to start
     */
    static boolean waitForStandalone(final Process process, final ModelControllerClient client, final long startupTimeout) throws InterruptedException {
        long timeout = startupTimeout * 1000;
        final long sleep = 100L;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isStandaloneRunning(client))
                return true;
            timeout -= (System.currentTimeMillis() - before);
            if (ProcessHelper.processHasDied(process)) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        return false;
    }

    /**
     * Checks whether or not a standalone server is running.
     *
     * @param client the client used to query the server
     *
     * @return {@code true} if the server is running otherwise {@code false}
     */
    static boolean isStandaloneRunning(final ModelControllerClient client) {
        try {
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.debug("Interrupted determining if standalone is running", e);
        }
        return false;
    }

    /**
     * Shuts down a standalone server.
     *
     * @param client the client used to execute the shutdown operation
     */
    static void shutdownStandalone(final ModelControllerClient client) {
        try {
            final ModelNode op = Operations.createOperation("shutdown");
            final ModelNode response = client.execute(op);
            if (Operations.isSuccessfulOutcome(response)) {
                while (true) {
                    if (isStandaloneRunning(client)) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(20L);
                        } catch (InterruptedException e) {
                            LOGGER.debug("Interrupted during sleep", e);
                        }
                    } else {
                        break;
                    }
                }
            } else {
                LOGGER.debugf("Failed to execute %s: %s", op, Operations.getFailureDescription(response));
            }
        } catch (IOException e) {
            LOGGER.debug("Interrupted shutting down standalone", e);
        }
    }

    private static boolean isDomainRunning(final DomainClient client, boolean shutdown) {
        final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();
        try {
            // Check for admin-only
            final ModelNode hostAddress = determineHostAddress(client);
            final Operations.CompositeOperationBuilder builder = Operations.CompositeOperationBuilder.create()
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "running-mode"))
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "host-state"));
            ModelNode response = client.execute(builder.build());
            if (Operations.isSuccessfulOutcome(response)) {
                response = Operations.readResult(response);
                if ("ADMIN_ONLY".equals(Operations.readResult(response.get("step-1")).asString())) {
                    if (Operations.isSuccessfulOutcome(response.get("step-2"))) {
                        final String state = Operations.readResult(response).asString();
                        return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                                && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
                    }
                }
            }
            final Map<ServerIdentity, ServerStatus> statuses = client.getServerStatuses();
            for (ServerIdentity id : statuses.keySet()) {
                final ServerStatus status = statuses.get(id);
                switch (status) {
                    case DISABLED:
                    case STARTED: {
                        servers.put(id, status);
                        break;
                    }
                }
            }
            if (shutdown) {
                return statuses.isEmpty();
            }
            return statuses.size() == servers.size();
        } catch (Exception e) {
            LOGGER.debug("Interrupted determining if domain is running", e);
        }
        return false;
    }
}
