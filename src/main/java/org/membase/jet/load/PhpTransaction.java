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
package org.membase.jet.load;

import com.sun.jet.framework.load.Transaction;
import com.sun.jet.framework.load.metrics.TransactionMetrics;
import com.sun.jet.jag.client.PortAllocator;
import com.sun.jet.util.Bindings;
import com.sun.jet.util.JAGInstance;
import com.sun.jet.util.LoggerFactory;
import com.sun.jet.util.TestinfraException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.membase.jmemcachedtest.MemcachedClient;

/**
 * The PhpTransaction class is used to simulate a page view from a
 * PHP application. The PHP drivers don't use persistent connections,
 * so this transaction connects to the server each time and run a small
 * number of transactions towards the membase cluster.
 *
 * @author Trond Norbye
 * @version 1.0
 */
public class PhpTransaction extends Transaction {

    private static final Logger LOG = LoggerFactory.getLogger(GetTransaction.class);
    private final Random random;
    private final int numberOfKeys;
    private final int maxOps;
    private final int minOps;
    private final int setPrc;
    private final String host;
    private final int port;
    private final int minDataSize;
    private final int maxDataSize;

    /**
     * Create a new instance of the PHP simulator.
     *
     * @param bindings the bindings provided by the test setup
     * @throws TestinfraException If an error occurs
     */
    public PhpTransaction(Bindings bindings) throws TestinfraException {
        super(bindings);
        setMetrics(TransactionMetrics.createTransactionMetrics(bindings));

        JAGInstance h = getBindings().getServerMachines().get(0);
        this.host = h.getHostName();
        PortAllocator alloc = new PortAllocator(h);
        port = alloc.getBatchPort("memcached");

        random = new Random();
        numberOfKeys = bindings.getInt("jet.membase.load.numberOfKeys");
        minDataSize = bindings.getInt("jet.membase.load.minimumItemSize");
        maxDataSize = bindings.getInt("jet.membase.load.maximumItemSize") - minDataSize;
        minOps = bindings.getInt("jet.membase.load.php.minimumNumberOfPageOperations");
        maxOps = bindings.getInt("jet.membase.load.php.maximumNumberOfPageOperations") - minOps;
        int set = bindings.getInt("jet.membase.load.php.setPercentage");
        if (set > 100 || set < 0) {
            throw new TestinfraException("Illegal value for jet.membase.load.php.setPercentage");
        }

        LOG.log(Level.INFO, "Config: {0} keys, [{1}, {2}] number of operations per page with {3}% set operations",
                new Object[]{numberOfKeys, minOps, maxOps + minOps, set});
        if ((set / 10) != 0) {
            setPrc = set / 10;
        } else {
            setPrc = 1;
        }
    }

    /**
     * Close this transaction object. The PHP simulator connects to the
     * membase cluster for each invocation of the transactions, so we don't
     * need to do any cleanup.
     */
    @Override
    public void close() {
    }

    /**
     * Perform a transaction. The transaction tries to simulate what a
     * typical PHP application would do to render a page.
     *
     * @throws Exception if an error occurs
     */
    @Override
    protected void doTransaction() throws Exception {
        getMetrics().incReconnects();
        try {
            MemcachedClient client = null;
            try {
                client = new MemcachedClient(host, port);

                int ops = random.nextInt(maxOps) + minOps;
                for (int ii = 0; ii < ops; ++ii) {
                    int key = random.nextInt(numberOfKeys);
                    if (ii % setPrc == 0) {
                        int datasz = random.nextInt(maxDataSize) + minDataSize;

                        if (!client.store(key, datasz)) {
                            LOG.log(Level.SEVERE, "Failed to store: \"{0}\"", "" + key);
                            getMetrics().incRollbacks();
                            return;
                        }
                    } else {
                        int ret = client.get(key);
                        if (ret == -1) {
                            LOG.log(Level.SEVERE, "Key is no longer there: \"{0}\"", "" + key);
                            getMetrics().incRollbacks();
                            return;
                        }
                    }
                }

                getMetrics().incCommits();
            } finally {
                if (client != null) {
                    client.shutdown();
                }
            }

        } catch (Exception e) {
            LOG.log(Level.INFO, "Got exception talking to {0}:{1}", new Object[]{host, "" + port});
            LOG.log(Level.SEVERE, "Got exception: ", e);
            throw e;
        }
    }
}
