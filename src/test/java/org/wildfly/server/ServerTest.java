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

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerTest {

    @Test
    public void testStartStandalone() throws Exception {
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(Environment.WILDFLY_HOME);
        final StandaloneServer server = Server.builder(commandBuilder)
                .setStandardOut(System.out)
                .build();
        server.start(5L);

        Assert.assertTrue("The server does not appear to be running", server.isRunning());
        server.stop();
        Assert.assertFalse("The server does not appear to have stopped", server.isRunning());
    }

    @Test
    public void testStartDomain() throws Exception {
        final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(Environment.WILDFLY_HOME);
        final DomainServer server = Server.builder(commandBuilder)
                .setStandardOut(System.out)
                .build();
        server.start(10L);

        Assert.assertTrue("The server does not appear to be running", server.isRunning());
        final Host host = server.getHost("master");
        Assert.assertNotNull(host);
        Assert.assertTrue("The host server does not appear to be running", host.isRunning());
        Assert.assertEquals("master", host.getName());

        final Server serverOne = host.getServer("server-one");
        serverOne.stop();
        Assert.assertFalse("server-one was not stopped", serverOne.isRunning());
        serverOne.start(-1); // TODO (jrp) something needs to be done with the timeout as it's not used here
        Assert.assertTrue("server-one was not started", serverOne.isRunning());

        server.stop();
        Assert.assertFalse("The server does not appear to have stopped", server.isRunning());
    }
}
