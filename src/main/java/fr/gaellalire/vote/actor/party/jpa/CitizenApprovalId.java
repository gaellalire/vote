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

import java.io.Serializable;

import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.ManyToOne;

/**
 * @author Gael Lalire
 */
@Embeddable
public class CitizenApprovalId implements Serializable {

    private static final long serialVersionUID = -780961119073066222L;

    @ManyToOne(cascade = CascadeType.ALL)
    private Citizen approbator;

    @ManyToOne(cascade = CascadeType.ALL)
    private Citizen approved;

    public Citizen getApprobator() {
        return approbator;
    }

    public Citizen getApproved() {
        return approved;
    }

    public void setApprobator(final Citizen approbator) {
        this.approbator = approbator;
    }

    public void setApproved(final Citizen approved) {
        this.approved = approved;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((approbator == null) ? 0 : (int) approbator.getId());
        result = prime * result + ((approved == null) ? 0 : (int) approved.getId());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CitizenApprovalId)) {
            return false;
        }
        CitizenApprovalId other = (CitizenApprovalId) obj;
        if (approbator == null) {
            if (other.approbator != null) {
                return false;
            }
        } else if (approbator.getId() == other.approbator.getId()) {
            return false;
        }
        if (approved == null) {
            if (other.approved != null) {
                return false;
            }
        } else if (approved.getId() != other.approved.getId()) {
            return false;
        }
        return true;
    }

}
