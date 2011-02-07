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
package org.membase.jet.client;

import com.sun.jet.jag.client.JAGClient;
import com.sun.jet.jag.client.PortAllocator;
import com.sun.jet.util.Bindings;
import com.sun.jet.util.JAGInstance;
import com.sun.jet.util.JETInstallpath;
import com.sun.jet.util.LoggerFactory;
import com.sun.jet.util.TestinfraException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the class used on the client machines to communicate to the
 * server machines by using JMX.
 *
 * @author Trond Norbye
 * @version 1.0
 */
public class MemcachedControllerClient extends JAGClient {

    private static final Logger LOG = LoggerFactory.getLogger(MemcachedControllerClient.class);

    public MemcachedControllerClient(JAGInstance instance) throws TestinfraException {
        super(instance);
    }

    @Override
    public boolean init(String mletloader) throws TestinfraException {
        return super.initImpl(mletloader, "MemcachedController",
                "org.membase.jet.ops.MemcachedController");
    }

    /**
     * Try to execute start() on the server machine.
     *
     * @param port the port memcached should listen to
     * @param installPath Where the binaries are installed
     * @param dataPath where to store the database
     * @throws TestinfraException if an error occurs
     */
    public void start(String port, File installPath, String dataPath) throws TestinfraException {
        try {
            invoke("start",
                    new Object[]{port, installPath, dataPath},
                    new String[]{String.class.getName(), File.class.getName(), String.class.getName()});
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to start memcached", e);
            throw new TestinfraException(e);
        }
    }

    /**
     * Try to shut down memcached on the server machine.
     * @throws TestinfraException if an error occurs
     */
    public void terminate() throws TestinfraException {
        try {
            invoke("terminate", null, null);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to shut down memcached", e);
            throw new TestinfraException(e);
        }
    }

    /**
     * Factory method to create an initialized MemcachedController
     * on the specified host
     *
     * @param bindings The runtime bindings
     * @param host The JAGInstance representing the host we want the object to
     *             be created on
     * @return A client object ready to use!
     * @throws TestinfraException if an error occurs during creation of the
     *         object.
     */
    public static MemcachedControllerClient create(Bindings bindings, JAGInstance host) throws TestinfraException {
        MemcachedControllerClient client = null;
        LOG.log(Level.SEVERE, "Starting memached on ''{0}''", host);
        try {
            PortAllocator alloc = new PortAllocator(host);
            client = new MemcachedControllerClient(host);

            client.init(bindings.getString("jag." + host + ".loadername"));

            List<JETInstallpath> installPaths = bindings.getInstallPaths(host);
            File memcached = null;
            for (JETInstallpath p : installPaths) {
                if (p.getInstallName().equals("membase")) {
                    memcached = p.getInstallpath();
                    LOG.log(Level.INFO, "Found memcached install path as: {0}", memcached.toString());
                }
            }

            if (memcached == null) {
                throw new TestinfraException("Failed to lookup memcached installpath for " + host);
            }

            LOG.log(Level.SEVERE, "Starting memached on {0}:{1}", new Object[]{host, "" + alloc.getBatchPort("memcached")});
            client.start("" + alloc.getBatchPort("memcached"), memcached, bindings.getTestLogPath());
        } catch (IOException ex) {
            Logger.getLogger(MemcachedControllerClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new TestinfraException("Failed to get install paths for " + host, ex);
        } catch (TestinfraException je) {
            throw new TestinfraException("Failed to create MemcachedController on " + host, je);
        }

        return client;
    }
}
