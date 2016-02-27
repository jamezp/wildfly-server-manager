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

import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) create suspendable, restartable and reloadable interfaces possibly
public interface Server {

    /**
     * Starts the server.
     *
     * @throws IOException the an error occurs creating the process
     */
    // TODO (jrp) possibly just move this to StandaloneServer and DomainServer as ServerGroup servers don't allow a timeout
    void start(final long timeout) throws IOException, InterruptedException;

    /**
     * Stops the server.
     */
    void stop();

    /**
     * Returns a management client to communicate with the server.
     * <p>
     * Note that invoking the {@link ModelControllerClient#close() close()} method does not close the client.
     * </p>
     *
     * @return the client
     */
    // TODO (jrp) possibly just move this to StandaloneServer and DomainServer as ServerGroup servers don't need a client
    ModelControllerClient getClient();

    /**
     * Checks the status of the server and returns {@code true} if the server is fully started.
     *
     * @return {@code true} if the server is fully started, otherwise {@code false}
     */
    boolean isRunning();

    static DomainServerBuilder builder(final DomainCommandBuilder commandBuilder) {
        return new DomainServerBuilder(commandBuilder);
    }

    static StandaloneServerBuilder builder(final StandaloneCommandBuilder commandBuilder) {
        return new StandaloneServerBuilder(commandBuilder);
    }
}
