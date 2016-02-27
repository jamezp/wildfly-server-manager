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

import java.util.Set;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface Host {

    /**
     * Checks that the host controller is running.
     *
     * @return {@code true} if the host controller is running, otherwise {@code false}
     */
    boolean isRunning();

    /**
     * Gets the name of host controller running. If the host controller is not running {@code null} is returned.
     *
     * @return the name of the host controller or {@code null} if the host controller is not running
     */
    String getName();

    /**
     * Returns the servers associated with the host.
     *
     * @return the servers
     */
    Set<Server> getServers();

    /**
     * Gets the server by name on this host.
     *
     * @param serverName the server name
     *
     * @return the server
     *
     * @throws RuntimeException if the server does not exist on this host
     */
    Server getServer(String serverName);
}
