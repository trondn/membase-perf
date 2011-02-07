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

import com.sun.jet.jag.agent.JAGOperationMBean;
import java.io.File;
import java.io.IOException;

/**
 * Interface to use between the client nodes and the server nodes.
 *
 * @author Trond Norbye
 */
public interface MemcachedControllerMBean extends JAGOperationMBean {
    /**
     * Start memcached with ep-engine
     *
     * @param port The port the memcached service should listen on
     * @param installPath The install path for this test
     * @param dataPath Where to store the database
     * @throws IOException If an error occurs
     */
    public void start(String port, File installPath, String dataPath) throws IOException;

    /**
     * Shut down the memcached service
     *
     * @throws IOException If an error occurs
     */
    public void terminate() throws IOException;
}
