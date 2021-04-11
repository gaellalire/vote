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

package fr.gaellalire.vote.actor.party.jpa;

import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * @author Gael Lalire
 */
@Entity
@Table(indexes = {@Index(name = "polling_station_name_index", columnList = "name", unique = true)})
public class PollingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;

    private byte[] publicKeyModulusSha512;

    @OneToMany(mappedBy = "pollingStation")
    private List<Citizen> citizens;

    @ElementCollection
    @CollectionTable(name = "PollingStationModulus", joinColumns = @JoinColumn(name = "id"))
    @Column(length = 260)
    private List<String> modulus;

    public PollingStation() {
    }

    public PollingStation(final String name, final byte[] publicKeyModulusSha512) {
        this.name = name;
        this.publicKeyModulusSha512 = publicKeyModulusSha512;
    }

    public String getName() {
        return name;
    }

    public byte[] getPublicKeyModulusSha512() {
        return publicKeyModulusSha512;
    }

    public void setPublicKeyModulusSha512(final byte[] publicKeyModulusSha512) {
        this.publicKeyModulusSha512 = publicKeyModulusSha512;
    }

    public List<Citizen> getCitizens() {
        return citizens;
    }

    public List<String> getModulus() {
        return modulus;
    }

}
