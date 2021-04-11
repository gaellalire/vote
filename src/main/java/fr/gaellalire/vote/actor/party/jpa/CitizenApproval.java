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

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;

/**
 * @author Gael Lalire
 */
@Entity
@Table
public class CitizenApproval {

    @EmbeddedId
    private CitizenApprovalId primaryKey = new CitizenApprovalId();

    private CitizenApprovalType approvalType;

    @Lob
    private byte[] signature;

    public Citizen getApprobator() {
        return primaryKey.getApprobator();
    }

    public Citizen getApproved() {
        return primaryKey.getApproved();
    }

    public void setApprobator(final Citizen approbator) {
        primaryKey.setApprobator(approbator);
    }

    public void setApproved(final Citizen approved) {
        primaryKey.setApproved(approved);
    }

    public CitizenApprovalType getApprovalType() {
        return approvalType;
    }

    public void setApprovalType(final CitizenApprovalType approvalType) {
        this.approvalType = approvalType;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(final byte[] signature) {
        this.signature = signature;
    }

}
