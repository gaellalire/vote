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

package fr.gaellalire.vote.actor.citizen;

import java.io.Serializable;

import fr.gaellalire.vote.trust.rsa.RSAPrivatePart;

/**
 * @author Gael Lalire
 */
public class CitizenActorData implements Serializable {

    private static final long serialVersionUID = -8038826374326125026L;

    private String stateHost;

    private String ssNumber;

    // should be kept in smartcard (maybe knox ?)
    private RSAPrivatePart ssPrivatePart;

    public CitizenActorData(final String stateHost, final String ssNumber, final RSAPrivatePart ssPrivatePart) {
        this.stateHost = stateHost;
        this.ssNumber = ssNumber;
        this.ssPrivatePart = ssPrivatePart;
    }

    public String getStateHost() {
        return stateHost;
    }

    public String getSsNumber() {
        return ssNumber;
    }

    public RSAPrivatePart getSsPrivatePart() {
        return ssPrivatePart;
    }
}
