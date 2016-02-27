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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.DelegatingModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractServer implements Server, Killable {

    private final CommandBuilder commandBuilder;
    private final OutputStream stdout;
    protected final ModelControllerClient client;
    private volatile String versionString;
    private ConsoleConsumer consoleConsumer;
    private Process process;

    protected AbstractServer(final CommandBuilder commandBuilder, final ModelControllerClient client, final OutputStream stdout) {
        this.commandBuilder = commandBuilder;
        this.stdout = stdout;
        this.client = new NonClosingModelControllerClient(client);
    }

    /**
     * Starts the server.
     *
     * @throws IOException the an error occurs creating the process
     */
    public final synchronized void start(final long timeout) throws IOException, InterruptedException {
        final Launcher launcher = Launcher.of(commandBuilder);
        // Determine if we should consume stdout
        if (stdout == null) {
            launcher.inherit();
        } else {
            launcher.setRedirectErrorStream(true);
        }
        final Process process = launcher.launch();
        if (stdout != null) {
            consoleConsumer = ConsoleConsumer.start(process.getInputStream(), stdout);
        }
        if (!waitForStart(process, timeout)) {
            try {
                process.destroy();
                process.waitFor(5L, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
            }
            throw new IllegalStateException(String.format("Managed server was not started within [%d] s", timeout));
        }
        this.process = process;
    }

    /**
     * Stops the server.
     */
    public final synchronized void stop() {
        final ConsoleConsumer consoleConsumer = this.consoleConsumer;
        this.consoleConsumer = null;
        try {
            // Stop the servers
            stopServer();
        } finally {
            try {
                ProcessHelper.destroyProcess(process);
            } catch (InterruptedException ignore) {
                // no-op
            }
            process = null;
            versionString = null;
            if (consoleConsumer != null) consoleConsumer.shutdown();
        }
    }

    /**
     * Returns a management client to communicate with the server.
     * <p>
     * Note that invoking the {@link ModelControllerClient#close() close()} method does not close the client.
     * </p>
     *
     * @return the client
     */
    public ModelControllerClient getClient() {
        return client;
    }

    /**
     * Kills the process forcibly.
     */
    public void kill() {
        final Process process = this.process;
        if (process != null) {
            process.destroyForcibly();
        }
    }

    /**
     * Stops the server before the process is destroyed. A no-op override will just destroy the process.
     */
    protected abstract void stopServer();

    protected abstract boolean waitForStart(Process process, long timeout) throws IOException, InterruptedException;

    /**
     * Checks the status of the server and returns {@code true} if the server is fully started.
     *
     * @return {@code true} if the server is fully started, otherwise {@code false}
     */
    public abstract boolean isRunning();

    /**
     * Queries the running server and returns a string identifying information about the container.
     *
     * @return information about the container or {@code null} if the server is not running
     */
    String getRunningVersion() {
        // Lazy load the version
        if (isRunning() && versionString == null) {
            synchronized (this) {
                if (versionString == null) {
                    final StringBuilder sb = new StringBuilder();
                    try {
                        final ModelNode op = Operations.createReadResourceOperation(ServerHelper.EMPTY_ADDRESS);
                        final ModelNode result = client.execute(op);
                        if (Operations.isSuccessfulOutcome(result)) {
                            final ModelNode model = Operations.readResult(result);
                            if (model.hasDefined("product-name")) {
                                sb.append(model.get("product-name").asString());
                            } else {
                                sb.append("WildFly");
                            }

                            if (model.hasDefined("product-version")) {
                                sb.append(' ').append(model.get("product-version").asString());
                                if (model.hasDefined("release-codename")) {
                                    sb.append(' ').append('"').append(model.get("release-codename").asString()).append('"');
                                }

                                if (model.hasDefined("release-version")) {
                                    sb.append(" (WildFly Core ").append(model.get("release-version").asString()).append(')');
                                }
                            } else {
                                if (model.hasDefined("release-version")) {
                                    sb.append(' ').append(model.get("release-version").asString());
                                }
                                if (model.hasDefined("release-codename")) {
                                    sb.append(' ').append('"').append(model.get("release-codename").asString()).append('"');
                                }
                            }
                            return sb.toString();
                        } else {
                            sb.append("Error determining running version: ").append(Operations.getFailureDescription(result));
                        }
                    } catch (IOException e) {
                        sb.append("Error determining running version: ").append(e.getMessage());
                    }
                    versionString = sb.toString();
                }
            }
        }
        return versionString;
    }

    static void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Exception ignore) {
        }
    }

    /**
     * Consumes input and writes it to the output.
     *
     * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
     */
    private static class ConsoleConsumer {
        private final InputStream in;
        private final OutputStream out;
        private final ExecutorService executorService;

        private ConsoleConsumer(final InputStream in, final OutputStream out) {
            this.in = in;
            this.out = out;
            executorService = Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("WildFly-Console-Consumer");
                return t;
            });
        }

        /**
         * Creates and starts consuming the input stream.
         *
         * @param in  the input stream to redirect to the output stream
         * @param out the output stream to redirect to
         *
         * @return the console consumer which has started
         */
        static ConsoleConsumer start(final InputStream in, final OutputStream out) {
            final ConsoleConsumer consumer = new ConsoleConsumer(in, out);
            consumer.start();
            return consumer;
        }

        /**
         * Shutdown the consumer.
         */
        protected void shutdown() {
            try {
                executorService.shutdown();
                executorService.awaitTermination(2L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }

        private void start() {
            executorService.submit(() -> {
                byte[] buffer = new byte[2048];
                try {
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                } catch (IOException ignore) {
                }
            });
        }
    }

    private static class NonClosingModelControllerClient extends DelegatingModelControllerClient {

        public NonClosingModelControllerClient(final ModelControllerClient delegate) {
            super(delegate);
        }

        @Override
        public void close() throws IOException {
            // Do nothing
        }
    }
}
