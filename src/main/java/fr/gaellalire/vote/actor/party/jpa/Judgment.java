/*
 * Copyright 2020 The Apache Software Foundation.
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

package fr.gaellalire.vote.actor.party.jpa;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * @author Gael Lalire
 */
@Entity
@Table
public class Judgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(optional = false)
    private Vote vote;

    private int indice;

    private int value;

    public int getIndice() {
        return indice;
    }

    public int getValue() {
        return value;
    }

    public Vote getVote() {
        return vote;
    }

    public void setIndice(final int indice) {
        this.indice = indice;
    }

    public void setValue(final int value) {
        this.value = value;
    }

    public void setVote(final Vote vote) {
        this.vote = vote;
    }

}
