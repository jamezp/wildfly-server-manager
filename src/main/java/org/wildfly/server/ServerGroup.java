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

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface ServerGroup {

    /**
     * Reloads all the servers associated with this server group.
     */
    void reloadServers();

    /**
     * Restarts all the servers associated with this server group.
     */
    void restartServers();

    /**
     * Resumes all the previously suspended servers associated with this server group.
     */
    void resumeServers();

    /**
     * Starts all the servers associated with this server group.
     */
    void startServers();

    /**
     * Stops all the servers associated with this server group.
     */
    void stopServers();

    /**
     * Suspends all the servers associated with this server group.
     *
     * @param timeout the timeout in seconds. A value of 0 returns immediately and a value of -1 will wait indefinitely
     */
    void suspendServers(int timeout);

    /**
     * The name of the server group.
     *
     * @return the name
     */
    String getName();
}
