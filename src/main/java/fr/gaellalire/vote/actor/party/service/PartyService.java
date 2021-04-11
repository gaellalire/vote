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

package fr.gaellalire.vote.actor.party.service;

import java.math.BigInteger;
import java.rmi.Remote;
import java.rmi.RemoteException;

import fr.gaellalire.vote.Ballot;
import fr.gaellalire.vote.RequireAnonymousNetwork;
import fr.gaellalire.vote.actor.pooling_station.service.VotingModulusList;
import fr.gaellalire.vote.actor.pooling_station.service.VotingSignatureList;

/**
 * @author Gael Lalire
 */
public interface PartyService extends Remote {

    String getName() throws RemoteException;

    void setPollingStationData(String pollingStationName, VotingModulusList votingModulusList, VotingSignatureList votingSignatureList) throws RemoteException;

    @RequireAnonymousNetwork
    void vote(BigInteger votePublicModulus, Ballot ballot, byte[] ballotSignature) throws RemoteException;

}
