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
package org.membase.jet.tests;

import com.sun.jet.framework.TestCase;
import com.sun.jet.jag.client.PortAllocator;
import com.sun.jet.util.JAGInstance;
import com.sun.jet.util.LoggerFactory;
import com.sun.jet.util.TestinfraException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.membase.jmemcachedtest.MemcachedClient;

/**
 * Basic tests on the JMembase cluster...
 *
 * @author Trond Norbye
 */
public class DatasetTest extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetTest.class);

    private class BulkLoader implements Runnable {

        String host;
        int port;
        int start;
        int stop;
        int min;
        int max;
        boolean error;

        BulkLoader(String host, int port, int start, int stop, int min, int max) {
            this.host = host;
            this.port = port;
            this.start = start;
            this.stop = stop;
            this.min = min;
            this.max = max;
        }

        @Override
        public void run() {
            MemcachedClient client = null;
            try {
                client = new MemcachedClient(host, port);
                client.bulkLoad(start, stop, min, max);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Failed to set data", ex);
                error = true;
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }
    }

    /**
     * Load the server with all of the objects we want
     *
     * @todo replace with a more efficient way to load the items (use multiple
     * threads and pipelined sets)
     *
     * @throws TestinfraException
     */
    public void bulkLoad() throws TestinfraException {
        int numberOfKeys = getBindings().getInt("jet.membase.load.numberOfKeys");
        int minimumItemSize = getBindings().getInt("jet.membase.load.minimumItemSize");
        int maximumItemSize = getBindings().getInt("jet.membase.load.maximumItemSize") - minimumItemSize;
        int numThreads = getBindings().getInt("jet.membase.load.numberOfThreads");

        for (JAGInstance host : getBindings().getServerMachines()) {
            PortAllocator alloc = new PortAllocator(host);
            LOG.log(Level.INFO, "Connecting to: {0}:{1}", new Object[]{host.getHostName(), "" + alloc.getBatchPort("memcached")});

            List<Thread> threads = new ArrayList<Thread>();
            List<BulkLoader> loaders = new ArrayList<BulkLoader>();

            int perThread = numberOfKeys / numThreads;
            int offset = 0;
            String hostnm = host.getHostName();
            int port = alloc.getBatchPort("memcached");
            for (int ii = 0; ii < numThreads; ++ii) {
                BulkLoader bl;
                if (ii == numThreads - 1) {
                    bl = new BulkLoader(hostnm, port, offset, numberOfKeys, minimumItemSize, maximumItemSize);
                } else {
                    bl = new BulkLoader(hostnm, port, offset, offset + perThread, minimumItemSize, maximumItemSize);
                }
                loaders.add(bl);
                Thread t = new Thread(bl);
                t.start();
                threads.add(t);
                offset += perThread;
            }

            // Wait for the threads to complete the population...
            for (Thread t : threads) {
                do {
                    try {
                        t.join();
                    } catch (InterruptedException ex) {
                    }
                } while (t.isAlive());
            }

            for (BulkLoader bl : loaders) {
                if (bl.error) {
                    throw new TestinfraException("Failed to populate data!");
                }
            }

        }
    }

    /**
     * Verify that we've got all of the items!
     * @throws TestinfraException
     */
    public void verify() throws TestinfraException {
        int numberOfKeys = getBindings().getInt("jet.membase.load.numberOfKeys");
        int minimumItemSize = getBindings().getInt("jet.membase.load.minimumItemSize");
        int maximumItemSize = getBindings().getInt("jet.membase.load.maximumItemSize");

        int numErrors = 0;

        for (JAGInstance host : getBindings().getServerMachines()) {
            PortAllocator alloc = new PortAllocator(host);
            LOG.log(Level.INFO, "Connecting to: {0}:{1}", new Object[]{host.getHostName(), "" + alloc.getBatchPort("memcached")});

            MemcachedClient client = null;
            try {
                client = new MemcachedClient(host.getHostName(), alloc.getBatchPort("memcached"));
                for (int ii = 0; ii < numberOfKeys; ++ii) {
                    int size = client.get(ii);
                    if (size == -1) {
                        LOG.log(Level.SEVERE, "Failed to retrieve {0}", "" + ii);
                        ++numErrors;
                    } else if (size < minimumItemSize || size >= maximumItemSize) {
                        LOG.log(Level.SEVERE, "Illegal size for {0} ({1})", new Object[]{"" + ii, "" + size});
                        ++numErrors;
                    }
                }
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
                throw new TestinfraException(ex);
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }
        }

        if (numErrors != 0) {
            throw new TestinfraException("Verification failed! " + numErrors + " errors.. check the logs for more details");
        }
    }
}
