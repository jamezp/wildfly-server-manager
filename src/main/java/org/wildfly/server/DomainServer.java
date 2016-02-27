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

import org.jboss.as.controller.client.helpers.domain.DomainClient;

/**
 * Represents a domain server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public interface DomainServer extends Server, Killable {

    /**
     * The hosts associated with the domain server.
     *
     * @return the hosts associated with the domain server
     */
    Set<Host> getHosts();

    /**
     * Gets a host by name associated with the domain.
     *
     * @param name the name of the host
     *
     * @return the host
     *
     * @throws RuntimeException if the host does not exist
     */
    Host getHost(String name);

    @Override
    DomainClient getClient();

    /**
     * Returns the server groups associated with this domain.
     *
     * @return the server groups
     */
    Set<ServerGroup> getServerGroups();

    /**
     * Get the server group by name.
     *
     * @param name the name of the server group
     *
     * @return the server group
     *
     * @throws RuntimeException if the server group does not exist
     */
    ServerGroup getServerGroup(String name);
}
