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

package fr.gaellalire.vote.actor.state.jpa;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

/**
 * @author Gael Lalire
 */
@Entity
@Table(indexes = {@Index(name = "party_name_index", columnList = "name", unique = true)})
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    private String host;

    private String rmiName;

    public Party() {
    }

    public Party(final String name, final String host, final String rmiName) {
        this.name = name;
        this.host = host;
        this.rmiName = rmiName;
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

}
