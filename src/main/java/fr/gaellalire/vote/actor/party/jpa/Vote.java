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

import java.math.BigInteger;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * @author Gael Lalire
 */
@Entity
@Table
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(optional = false)
    private PollingStation pollingStation;

    @Column(unique = true, nullable = false, length = 260)
    private String publicKeyModulus;

    @OneToMany(mappedBy = "vote")
    private List<Judgment> judgments;

    public BigInteger getPublicKeyModulus() {
        return new BigInteger(publicKeyModulus, 16);
    }

    public void setPublicKeyModulus(final BigInteger publicKeyModulus) {
        this.publicKeyModulus = publicKeyModulus.toString(16);
    }

    public long getId() {
        return id;
    }

    public void setPollingStation(final PollingStation pollingStation) {
        this.pollingStation = pollingStation;
    }

    public PollingStation getPollingStation() {
        return pollingStation;
    }

    public List<Judgment> getJudgments() {
        return judgments;
    }

    public void setJudgments(final List<Judgment> judgments) {
        this.judgments = judgments;
    }

}
