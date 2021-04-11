/*
 * Copyright 2021 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.gaellalire.vote.actor.state.service;

import java.io.Serializable;
import java.util.List;

/**
 * @author Gael Lalire
 */
public class PollingStation implements Serializable {

    private static final long serialVersionUID = -8662759616168653021L;

    private String name;

    private String host;

    private String rmiName;

    private byte[] publicKeyModulusSha512;

    private List<String> ssNumbers;

    public PollingStation(final String name, final String host, final String rmiName, final byte[] publicKeyModulusSha512, final List<String> ssNumbers) {
        this.name = name;
        this.host = host;
        this.rmiName = rmiName;
        this.publicKeyModulusSha512 = publicKeyModulusSha512;
        this.ssNumbers = ssNumbers;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public String getRmiName() {
        return rmiName;
    }

    public byte[] getPublicKeyModulusSha512() {
        return publicKeyModulusSha512;
    }

    public List<String> getSsNumbers() {
        return ssNumbers;
    }

}
