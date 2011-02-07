/**
 *     Copyright 2011 Membase, Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.membase.jet.setup;

import com.sun.jet.framework.TestSetup;
import com.sun.jet.jag.client.PortAllocator;
import com.sun.jet.util.JAGInstance;
import com.sun.jet.util.LoggerFactory;
import com.sun.jet.util.TestinfraException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.Test;
import org.membase.jet.client.MemcachedControllerClient;
import org.membase.jmemcachedtest.MemcachedClient;

/**
 * Utility class to start and stop the Memcached servers.
 *
 * @author Trond Norbye
 * @version 1.0
 */
public class MemcachedServerSetup extends TestSetup {

    private static final Logger LOG = LoggerFactory.getLogger(MemcachedServerSetup.class);
    /**
     * A map containing all of the MemcachedControllerClient used to control
     * the memcached servers running on the server machines represented
     * by the JAGInstance.
     */
    private Map<JAGInstance, MemcachedControllerClient> clients;

    /**
     * Initialize this MemcachedServerSetup
     *
     * @param test The test that is inside this constructor
     */
    public MemcachedServerSetup(Test test) {
        super(test);
        clients = new HashMap<JAGInstance, MemcachedControllerClient>();
    }

    /**
     * Starts a Memcached server on all of the servers defined in the
     * property file for this test. Sleep for two secs to allow them
     * to spin up before we activate all of the vbuckets on the servers
     *
     * TODO: We should add support for vbucket map. Right now all servers
     *       thinks they are responsible for all of the vbuckets....
     *
     * @throws Exception in case of errors
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        List<JAGInstance> serverHosts = getBindings().getServerMachines();

        for (JAGInstance host : serverHosts) {
            MemcachedControllerClient client;

            client = MemcachedControllerClient.create(getBindings(), host);
            if (client != null) {
                clients.put(host, client);
            }
        }

        LOG.log(Level.INFO, "Sleep 2 seconds to allow the server to start...");
        Thread.sleep(2000);
        LOG.log(Level.INFO, "Servers should be up'n'running...");

        for (JAGInstance host : serverHosts) {
            PortAllocator alloc = new PortAllocator(host);
            LOG.log(Level.INFO, "Connecting to: {0}:{1}", new Object[]{host.getHostName(), "" + alloc.getBatchPort("memcached")});

            MemcachedClient client = null;
            try {
                client = new MemcachedClient(host.getHostName(), alloc.getBatchPort("memcached"));
                for (int ii = 0; ii < 1024; ++ii) {
                    LOG.fine("Activating bucket: " + ii);
                    if (!client.setVbucket(ii)) {
                        LOG.log(Level.SEVERE, "Failed to define vbucket: {0}", "" + ii);
                        throw new TestinfraException("Failed to define vbucket: : " + ii);
                    }
                }
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Received an IOException while activating vbuckets", ex);
                throw new TestinfraException(ex);
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }
    }

    /**
     * Shut down all of the Memcached servers I've started
     *
     * @throws Exception in case of errors
     */
    @Override
    protected void tearDown() throws Exception {
        for (Map.Entry<JAGInstance, MemcachedControllerClient> entry : clients.entrySet()) {
            MemcachedControllerClient client = entry.getValue();
            client.terminate();
            client.unregister();
        }

        super.tearDown();
    }
}
