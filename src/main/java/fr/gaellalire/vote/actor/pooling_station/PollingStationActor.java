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

package fr.gaellalire.vote.actor.pooling_station;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import fr.gaellalire.vestige.spi.trust.TrustException;
import fr.gaellalire.vote.actor.RemoteActor;
import fr.gaellalire.vote.actor.citizen.RMIOverrides;
import fr.gaellalire.vote.actor.party.service.PartyService;
import fr.gaellalire.vote.actor.pooling_station.service.PollingStationService;
import fr.gaellalire.vote.actor.pooling_station.service.PollingStationState;
import fr.gaellalire.vote.actor.pooling_station.service.VotingMetadata;
import fr.gaellalire.vote.actor.pooling_station.service.VotingModulusList;
import fr.gaellalire.vote.actor.pooling_station.service.VotingSignatureList;
import fr.gaellalire.vote.actor.state.service.Citizen;
import fr.gaellalire.vote.actor.state.service.Party;
import fr.gaellalire.vote.actor.state.service.StateService;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSAPrivatePart;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

public class PollingStationActor extends RemoteActor implements PollingStationService {

    private static final long serialVersionUID = -9123299188247788226L;

    private StateService stateService;

    private List<PartyService> partyServices;

    private RSAPrivatePart rsaPrivatePart;

    private AESUtils aesUtils;

    private VotingMetadata votingMetadata;

    private RSATrustSystem rsaTrustSystem;

    private VotingModulusList votingModulusList;

    private VotingSignatureList votingSignatureList;

    private volatile PollingStationState pollingStationState;

    private int registeredCount;

    private String pollingStationName;

    private Object mutex = new Object();

    protected PollingStationActor(final EntityManagerFactory entityManagerFactory, final RSAPrivatePart rsaPrivatePart, final AESUtils aesUtils, final String pollingStationName,
            final StateService stateService, final List<PartyService> partyServices, final RSATrustSystem rsaTrustSystem) throws RemoteException, TrustException {
        super(entityManagerFactory);
        this.rsaPrivatePart = rsaPrivatePart;
        this.stateService = stateService;
        this.partyServices = partyServices;
        votingMetadata = new VotingMetadata(rsaPrivatePart.getPublicPart().getModulus());
        this.rsaTrustSystem = rsaTrustSystem;
        this.aesUtils = aesUtils;
        this.pollingStationName = pollingStationName;
        votingModulusList = new VotingModulusList();
        votingSignatureList = new VotingSignatureList();
        pollingStationState = PollingStationState.WAIT_FOR_REGISTERING;
    }

    public String getName() {
        return pollingStationName;
    }

    public void endRegisteringPeriod() {
        pollingStationState = PollingStationState.WAIT_FOR_VOTING_KEYS;
    }

    @Override
    public VotingMetadata register(final String ssNumber, final byte[] biometricData) throws RemoteException {
        if (pollingStationState != PollingStationState.WAIT_FOR_REGISTERING) {
            // too late to register
            return null;
        }
        if (!stateService.verifyCitizen(ssNumber, biometricData)) {
            // unrecognized citizen, don't give him the public key or he could force the degraded mode
            // he could also know how to access private key of a dead citizen
            return null;
        }
        synchronized (mutex) {
            registeredCount++;
        }
        return votingMetadata;
    }

    @Override
    public void sendVotingPublicPartModulus(final byte[] iv, final byte[] aesKeyRSACrypted, final byte[] votingPublicPartModulusAESCrypted) throws RemoteException {
        if (pollingStationState != PollingStationState.WAIT_FOR_VOTING_KEYS) {
            return;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            rsaPrivatePart.decrypt(new ByteArrayInputStream(aesKeyRSACrypted), os);
        } catch (TrustException e) {
            return;
        }
        byte[] decodedKey = os.toByteArray();

        SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        os.reset();
        try {
            aesUtils.decrypt(iv, secretKey, new ByteArrayInputStream(votingPublicPartModulusAESCrypted), os);
        } catch (Exception e) {
            return;
        }

        BigInteger votingPublicPartModulus = new BigInteger(os.toByteArray());

        synchronized (mutex) {
            votingModulusList.getModulus().add(votingPublicPartModulus);
            if (registeredCount == votingModulusList.getModulus().size()) {
                pollingStationState = PollingStationState.WAIT_FOR_SIGNATURE;
            }
        }

    }

    @Override
    public VotingModulusList getVotingModulusList() throws RemoteException {
        return votingModulusList;
    }

    @Override
    public void sendVotingModulusListSignature(final String ssNumber, final byte[] votingModulusListSignature) throws RemoteException {
        if (pollingStationState != PollingStationState.WAIT_FOR_SIGNATURE) {
            return;
        }
        Citizen citizen = stateService.getCitizen(ssNumber);
        try {
            if (!rsaTrustSystem.publicPartByModulus(citizen.getPublicKeyModulus()).verify(new ByteArrayInputStream(votingModulusList.getEncoded()),
                    new ByteArrayInputStream(votingModulusListSignature))) {
                // invalid signature of votingModulusList
                return;
            }
        } catch (TrustException e) {
            return;
        }

        synchronized (mutex) {
            votingSignatureList.getSignatureBySSNumber().put(ssNumber, votingModulusListSignature);

            if (registeredCount == votingSignatureList.getSignatureBySSNumber().size()) {
                pollingStationState = PollingStationState.WORK_DONE;
                // first state
                stateService.setPollingStationData(pollingStationName, votingModulusList, votingSignatureList);
                // then party (they will check with state data)
                for (PartyService partyService : partyServices) {
                    partyService.setPollingStationData(pollingStationName, votingModulusList, votingSignatureList);
                }
            }
        }

    }

    @Override
    public void switchToDegradedMode(final String ssNumberAsking, final byte[] signature) throws RemoteException {

    }

    @Override
    public PollingStationState getState() throws RemoteException {
        return pollingStationState;
    }

    @Override
    public VotingSignatureList getSignatureList() throws RemoteException {
        return votingSignatureList;
    }

    public static PollingStationActor create(final RSATrustSystem rsaTrustSystem, final AESUtils aesUtils, final String stateHost, final String host,
            final String pollingStationName, final File privateKeyFile) throws Exception {
        return create(rsaTrustSystem, aesUtils, stateHost, host, pollingStationName, privateKeyFile, null);
    }

    public static PollingStationActor create(final RSATrustSystem rsaTrustSystem, final AESUtils aesUtils, final String stateHost, final String host,
            final String pollingStationName, final File privateKeyFile, final RMIOverrides rmiOverrides) throws Exception {

        StateService stateService = null;
        if (rmiOverrides != null) {
            stateService = rmiOverrides.getStateService();
        }
        if (stateService == null) {
            Registry registry = LocateRegistry.getRegistry(stateHost);
            stateService = (StateService) registry.lookup("State");
        }

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("pollingStationPersistenceUnit",
                Collections.singletonMap("hibernate.connection.url", "jdbc:h2:./db/pollingStation" + pollingStationName));

        List<Party> partyList = stateService.getPartyList();
        List<PartyService> partyServices = new ArrayList<PartyService>(partyList.size());
        for (Party party : partyList) {
            PartyService partyService = null;
            if (rmiOverrides != null) {
                partyService = rmiOverrides.getPartyService(party.getName());
            }
            if (partyService == null) {
                Registry partyRegistry = LocateRegistry.getRegistry(party.getHost());
                partyService = (PartyService) partyRegistry.lookup(party.getRmiName());
            }
            partyServices.add(partyService);
        }

        RSAPrivatePart rsaPrivatePart;
        if (!privateKeyFile.isFile()) {
            // new polling station
            rsaPrivatePart = rsaTrustSystem.generatePrivatePart();

            // register to state
            MessageDigest md = MessageDigest.getInstance("SHA-512", BouncyCastleProvider.PROVIDER_NAME);
            byte[] publicKeyModulusSha512 = md.digest(rsaPrivatePart.getPublicPart().getModulus().toByteArray());

            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(privateKeyFile));
            try {
                objectOutputStream.writeObject(rsaPrivatePart);
            } finally {
                objectOutputStream.close();
            }

            stateService.addPollingStation(pollingStationName, host, "PollingStation" + pollingStationName, publicKeyModulusSha512);

        } else {
            // restore polling station

            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(privateKeyFile));
            try {
                rsaPrivatePart = (RSAPrivatePart) objectInputStream.readObject();
            } finally {
                objectInputStream.close();
            }

        }

        PollingStationActor pollingStationActor = new PollingStationActor(entityManagerFactory, rsaPrivatePart, aesUtils, pollingStationName, stateService, partyServices,
                rsaTrustSystem);
        Registry registry = LocateRegistry.getRegistry(host);

        registry.rebind("PollingStation" + pollingStationName, pollingStationActor);
        return pollingStationActor;
    }

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        int argPos = 0;
        String stateHost = args[argPos++];
        String host = args[argPos++];
        String pollingStationName = args[argPos++];
        String privateKeyFileName = args[argPos++];

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);
        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        File privateKeyFile = new File(privateKeyFileName);
        create(rsaTrustSystem, aesUtils, stateHost, host, pollingStationName, privateKeyFile);

    }

}
