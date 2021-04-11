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

/**
 * @author Gael Lalire
 */
public class Party implements Serializable {

    private static final long serialVersionUID = 1475287596274556996L;

    private String host;

    private String rmiName;

    public Party(final String host, final String rmiName) {
        this.host = host;
        this.rmiName = rmiName;
    }

    public String getHost() {
        return host;
    }

    public String getRmiName() {
        return rmiName;
    }
}
