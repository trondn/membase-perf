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
package org.membase.jet.ops;

import com.sun.jet.jag.agent.JAGOperation;
import com.sun.jet.util.LoggerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This is the implementation of the MemcachedController that runs on
 * the test servers.
 *
 * @author Trond Norbye
 */
public class MemcachedController extends JAGOperation implements MemcachedControllerMBean {
    private static final Logger LOG = LoggerFactory.getLogger(MemcachedController.class);
    private Process memcached;

    /**
     * Implementation of the start method from the MBean interface.
     *
     * @param port The port the memcached service should listen on
     * @param installPath The install path for this test
     * @param dataPath Where to store the database
     * @throws IOException If an error occurs
     */
    @Override
    public void start(String port, File installPath, String dataPath) throws IOException {
        File wd = new File(dataPath);
        if (!wd.exists()) {
            if (!wd.mkdirs()) {
                throw new IOException("Failed to create working directory");
            }
        }

        File binPath = new File(installPath, "bin");
        File binary = new File(binPath, "memcached");
        List<String> command = new ArrayList<String>();
        command.add(binary.getAbsolutePath());
        command.add("-p");
        command.add(port);
        command.add("-E");
        command.add("ep.so");
        command.add("-e");
        command.add("dbname=" + dataPath + "/perf.db");
        command.add("-X");
        command.add("stdin_term_handler.so");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(new File(dataPath));

        Map<String, String> env = pb.environment();
        File libPath = new File(installPath, "lib");
        if (System.getProperty("os.name").equals("Mac OS X")) {
            env.put("DYLD_LIBRARY_PATH", libPath.getAbsolutePath());
        } else {
            env.put("LD_LIBRARY_PATH", libPath.getAbsolutePath());
        }

        StringBuilder sb = new StringBuilder();
        for (String s : command) {
            sb.append(s);
            sb.append(" ");
        }
        LOG.log(Level.INFO, "Starting: ''{0}''", sb.toString().trim());

        memcached = pb.start();

        final BufferedReader in = new BufferedReader(new InputStreamReader(memcached.getInputStream()));

        Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                // @todo improve this..
                try {
                    String s;

                    while ((s = in.readLine()) != null) {
                        LOG.warning(s);
                    }
                } catch (IOException exp) {
                    LOG.info("Stopped spooling info from memcached");
                }
            }
        });
        t.start();
    }

    /**
     * This method is called by the framework when the object is removed
     * from the registry. Shut down the memcached process unless the
     * client did it earlier.
     */
    @Override
    public void postDeregister() {
        try {
            terminate();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to shut down memcached", ex);
        }
        super.postDeregister();
    }

    /**
     * Implementation of the terminate method from the MBean interface.
     *
     * @throws IOException never thrown
     */
    @Override
    public void terminate() throws IOException {
        if (memcached != null) {
            try {
                memcached.getOutputStream().close();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Failed to close memcached output stream", ex);
            }

            try {
                memcached.waitFor();
            } catch (InterruptedException ex) {
                LOG.log(Level.SEVERE, "Failed to get process exit code", ex);
                memcached.destroy();
            }
            memcached = null;
        }
    }
}
