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

import java.math.BigInteger;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import fr.gaellalire.vote.actor.polling_station.service.VotingModulusList;
import fr.gaellalire.vote.actor.polling_station.service.VotingSignatureList;

/**
 * @author Gael Lalire
 */
public interface StateService extends Remote {

    void setPollingStationData(String pollingStationName, VotingModulusList votingModulusList, VotingSignatureList votingSignatureList) throws RemoteException;

    PollingStation searchPollingStationByVotePublicModulus(BigInteger votePublicModulus) throws RemoteException;

    PollingStation getPollingStation(String name) throws RemoteException;

    List<Citizen> getPollingStationCitizenList(String pollingStationName) throws RemoteException;

    List<Citizen> getCitizenList() throws RemoteException;

    List<PollingStation> getPollingStationList() throws RemoteException;

    Citizen getCitizen(String ssNumber) throws RemoteException;

    List<Party> getPartyList() throws RemoteException;

    byte[] generateApproval(String approbatorSSNumber, String approvedSSNumber, CitizenApprovalType citizenApprovalType) throws RemoteException;

    boolean approve(String approbatorSSNumber, String approvedSSNumber, CitizenApprovalType citizenApprovalType, byte[] signature) throws RemoteException;

    void addParty(String name, String host, String rmiName, BigInteger publicKeyModulus) throws RemoteException;

    void addPollingStation(String name, String host, String rmiName, byte[] publicKeyModulusSha512) throws RemoteException;

    void addCitizen(String ssNumber, String pollingStationName, BigInteger publicKeyModulus) throws RemoteException;

    boolean verifyCitizen(String ssNumber, byte[] biometricData) throws RemoteException;

}
