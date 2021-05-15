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

package fr.gaellalire.vote.actor.polling_station.service;

import java.rmi.Remote;
import java.rmi.RemoteException;

import fr.gaellalire.vote.RequireAnonymousNetwork;

/**
 * @author Gael Lalire
 */
public interface PollingStationService extends Remote {

    /**
     * Physical action, the citizen must go to a place so that biometric data can be collected
     */
    VotingMetadata register(String ssNumber, byte[] collectedBiometricData) throws RemoteException;

    /**
     * We crypt to be able to ignore data from people who don't know the crypting key
     * @param votingPublicPartModulusCrypted
     */
    @RequireAnonymousNetwork
    void sendVotingPublicPartModulus(byte[] iv, byte[] aesKeyRSACrypted, byte[] votingPublicPartModulusAESCrypted) throws RemoteException;

    VotingModulusList getVotingModulusList() throws RemoteException;

    void sendVotingModulusListSignature(String ssNumber, byte[] votingModulusListSignature) throws RemoteException;

    void switchToDegradedMode(String ssNumberAsking, byte[] signature) throws RemoteException;

    PollingStationState getState() throws RemoteException;

    VotingSignatureList getSignatureList() throws RemoteException;
}
