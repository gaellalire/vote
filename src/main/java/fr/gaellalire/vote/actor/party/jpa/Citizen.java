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
import javax.persistence.Index;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * @author Gael Lalire
 */
@Entity
@Table(indexes = {@Index(name = "citizen_ss_number_index", columnList = "ssNumber", unique = true),
        @Index(name = "citizen_public_key_modulus_index", columnList = "publicKeyModulus", unique = true)})
public class Citizen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(optional = false)
    private PollingStation pollingStation;

    @Column(unique = true, nullable = false)
    private String ssNumber;

    @Column(unique = true, nullable = false, length = 260)
    private String publicKeyModulus;

    @OneToMany(mappedBy = "primaryKey.approved")
    private List<CitizenApproval> approvedBy;

    @OneToMany(mappedBy = "primaryKey.approbator")
    private List<CitizenApproval> approbatorOf;

    private byte[] pollingStationModulusListSignature;

    public BigInteger getPublicKeyModulus() {
        return new BigInteger(publicKeyModulus, 16);
    }

    public void setPublicKeyModulus(final BigInteger publicKeyModulus) {
        this.publicKeyModulus = publicKeyModulus.toString(16);
    }

    public String getSSNumber() {
        return ssNumber;
    }

    public void setSSNumber(final String ssNumber) {
        this.ssNumber = ssNumber;
    }

    public long getId() {
        return id;
    }

    public List<CitizenApproval> getApprovedBy() {
        return approvedBy;
    }

    public List<CitizenApproval> getApprobatorOf() {
        return approbatorOf;
    }

    public void setPollingStation(final PollingStation pollingStation) {
        this.pollingStation = pollingStation;
    }

    public PollingStation getPollingStation() {
        return pollingStation;
    }

    public void setPollingStationModulusListSignature(final byte[] pollingStationModulusListSignature) {
        this.pollingStationModulusListSignature = pollingStationModulusListSignature;
    }

    public byte[] getPollingStationModulusListSignature() {
        return pollingStationModulusListSignature;
    }

}
