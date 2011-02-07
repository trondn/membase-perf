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
 * A Transaction type that implements a set operation to the membase cluster
 *
 * @author Trond Norbye
 * @version 1.0
 */
public class SetTransaction extends Transaction {

    private static final Logger LOG = LoggerFactory.getLogger(GetTransaction.class);
    private final MemcachedClient client;
    private final Random random;
    private final int numberOfKeys;

    /**
     * Initialize a new instance of the SetTransaction
     *
     * @param bindings The bindings for the test case
     * @throws TestinfraException if an error occurs
     */
    public SetTransaction(Bindings bindings) throws TestinfraException {
        super(bindings);
        setMetrics(TransactionMetrics.createTransactionMetrics(bindings));

        JAGInstance host = getBindings().getServerMachines().get(0);
        PortAllocator alloc = new PortAllocator(host);
        client = new MemcachedClient(host.getHostName(), alloc.getBatchPort("memcached"));
        random = new Random();
        numberOfKeys = bindings.getInt("jet.membase.load.numberOfKeys");
    }

    /**
     * Run a single get transaction
     *
     * @throws Exception
     */
    @Override
    protected void doTransaction() throws Exception {
        int key = random.nextInt(numberOfKeys);
        int datasz = random.nextInt(1024);

        if (!client.store(key, datasz)) {
            LOG.log(Level.SEVERE, "Failed to store: \"{0}\"", "" + key);
            getMetrics().incRollbacks();
        } else {
            getMetrics().incCommits();
        }
    }

     /**
     * Close this transaction object and release all allocated resources.
     */
   @Override
    public void close() {
        client.shutdown();
    }
}
