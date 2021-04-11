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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.vote.Ballot;
import fr.gaellalire.vote.Judgment;
import fr.gaellalire.vote.actor.party.service.PartyService;
import fr.gaellalire.vote.actor.pooling_station.service.PollingStationService;
import fr.gaellalire.vote.actor.pooling_station.service.PollingStationState;
import fr.gaellalire.vote.actor.pooling_station.service.VotingMetadata;
import fr.gaellalire.vote.actor.pooling_station.service.VotingModulusList;
import fr.gaellalire.vote.actor.pooling_station.service.VotingSignatureList;
import fr.gaellalire.vote.actor.state.service.Citizen;
import fr.gaellalire.vote.actor.state.service.Party;
import fr.gaellalire.vote.actor.state.service.PollingStation;
import fr.gaellalire.vote.actor.state.service.StateService;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSAPrivatePart;
import fr.gaellalire.vote.trust.rsa.RSAPublicPart;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

/**
 * CitizenActor should be a smartphone. PollingStation can contact us with SMS.
 * @author Gael Lalire
 */
public class CitizenActor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitizenActor.class);

    // should be kept in smartcard (maybe knox ?)
    private RSAPrivatePart ssPrivatePart;

    private RSATrustSystem rsaTrustSystem;

    private AESUtils aesUtils;

    private Citizen citizen;

    private PollingStation pollingStation;

    private StateService stateService;

    private PollingStationService pollingStationService;

    private List<PartyService> partyServices;

    public CitizenActor(final RSAPrivatePart ssPrivatePart, final RSATrustSystem rsaTrustSystem, final AESUtils aesUtils, final Citizen citizen,
            final PollingStation pollingStation, final StateService stateService, final PollingStationService pollingStationService, final List<PartyService> partyServices) {
        this.ssPrivatePart = ssPrivatePart;
        this.rsaTrustSystem = rsaTrustSystem;
        this.aesUtils = aesUtils;
        this.citizen = citizen;
        this.pollingStation = pollingStation;
        this.stateService = stateService;
        this.pollingStationService = pollingStationService;
        this.partyServices = partyServices;
    }

    public void vote(final Ballot ballot) throws Exception {
        vote(ballot, null);
    }

    public void vote(final Ballot ballot, final CitizenListener citizenListener) throws Exception {
        VotingMetadata votingMetadata = pollingStationService.register(citizen.getSsNumber(), null);
        if (citizenListener != null) {
            citizenListener.registerDone();
        }

        BigInteger pollingStationPublicKeyModulus = votingMetadata.getPollingStationPublicKeyModulus();

        MessageDigest md = MessageDigest.getInstance("SHA-512", BouncyCastleProvider.PROVIDER_NAME);
        byte[] publicKeyModulusSha512 = md.digest(pollingStationPublicKeyModulus.toByteArray());

        if (!Arrays.equals(publicKeyModulusSha512, pollingStation.getPublicKeyModulusSha512())) {
            // the pollingStation is not the one registered to the state, do not vote

            // FIXME the pollingStation may have one different host by voter
            // the URL of pollingStation must be signed and kept by state
            return;
        }

        // WAIT FOR all people to be registered
        LOGGER.debug("WAIT_FOR_VOTING_KEYS in progress");
        waitFor(PollingStationState.WAIT_FOR_VOTING_KEYS);
        LOGGER.debug("WAIT_FOR_VOTING_KEYS done");

        // could also be generated in smartcard
        RSAPrivatePart votingPrivatePart = rsaTrustSystem.generatePrivatePart();

        RSAPublicPart votingPublicPart = votingPrivatePart.getPublicPart();
        RSAPublicPart pollingStationPublicPart = rsaTrustSystem.publicPartByModulus(pollingStationPublicKeyModulus);
        BigInteger votingPublicPartModulus = votingPublicPart.getModulus();

        SecretKey secretKey = aesUtils.generateKey();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] iv = aesUtils.encrypt(secretKey, new ByteArrayInputStream(votingPublicPartModulus.toByteArray()), os);
        byte[] votingPublicPartModulusAESCrypted = os.toByteArray();

        os.reset();
        pollingStationPublicPart.encrypt(new ByteArrayInputStream(secretKey.getEncoded()), os);
        byte[] aesKeyRSACrypted = os.toByteArray();

        pollingStationService.sendVotingPublicPartModulus(iv, aesKeyRSACrypted, votingPublicPartModulusAESCrypted);

        // WAIT FOR votingModulusList to be ready
        LOGGER.debug("WAIT_FOR_SIGNATURE in progress");
        waitFor(PollingStationState.WAIT_FOR_SIGNATURE);
        LOGGER.debug("WAIT_FOR_SIGNATURE done");

        VotingModulusList votingModulusList = pollingStationService.getVotingModulusList();

        if (!votingModulusList.getModulus().contains(votingPublicPartModulus)) {
            // our key is not in the list, we cannot sign

            // we ask for degraded mode, polling station cannot generate a new votingModulusList because allowing that would cause an issue to our anonymity
            os.reset();
            ssPrivatePart.sign(new ByteArrayInputStream(citizen.getSsNumber().getBytes("UTF-8")), os);
            byte[] signature = os.toByteArray();
            pollingStationService.switchToDegradedMode(citizen.getSsNumber(), signature);

            // TODO degraded mode

            return;
        }
        byte[] encoded = votingModulusList.getEncoded();
        os.reset();
        ssPrivatePart.sign(new ByteArrayInputStream(encoded), os);
        byte[] signature = os.toByteArray();

        pollingStationService.sendVotingModulusListSignature(citizen.getSsNumber(), signature);

        // WAIT FOR all signatures to be sent
        LOGGER.debug("wait for WORK_DONE");
        waitFor(PollingStationState.WORK_DONE);
        LOGGER.debug("WORK_DONE done");

        // check that all signature are good
        VotingSignatureList signatureList = pollingStationService.getSignatureList();
        Map<String, byte[]> signatureBySSNumber = signatureList.getSignatureBySSNumber();

        List<String> ssNumbers = pollingStation.getSsNumbers();
        if (!signatureBySSNumber.keySet().containsAll(ssNumbers)) {
            // invalid list, should report corruption of polling station
            return;
        }
        for (String ssNumber : ssNumbers) {
            byte[] bs = signatureBySSNumber.get(ssNumber);
            RSAPublicPart rsaPublicPart = rsaTrustSystem.publicPartByModulus(stateService.getCitizen(ssNumber).getPublicKeyModulus());
            if (!rsaPublicPart.verify(new ByteArrayInputStream(encoded), new ByteArrayInputStream(bs))) {
                // invalid list, should report corruption of polling station
                return;
            }
        }

        // all clear we finally can send our vote to the party subset of our choice

        os.reset();
        votingPrivatePart.sign(new ByteArrayInputStream(ballot.getEncoded()), os);
        byte[] ballotSignature = os.toByteArray();
        for (PartyService partyService : partyServices) {
            String partyName = "unknown";
            try {
                partyName = partyService.getName();
                partyService.vote(votingPublicPartModulus, ballot, ballotSignature);
            } catch (Exception e) {
                LOGGER.error("Unable to vote at party " + partyName, e);
            }
        }
        if (citizenListener != null) {
            citizenListener.voteDone();
        }

        LOGGER.debug("end of vote");

    }

    public void waitFor(final PollingStationState pollingStationState) throws RemoteException {
        while (pollingStationService.getState() != pollingStationState) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    public static CitizenActor create(final RSATrustSystem rsaTrustSystem, final AESUtils aesUtils, final String stateHost, final String ssNumber, final String pollingStationName,
            final File dataFile) throws Exception {
        Registry registry = LocateRegistry.getRegistry(stateHost);
        StateService stateService = (StateService) registry.lookup("State");

        RSAPrivatePart ssPrivatePart;
        { // try to restore at least the key, because it takes a lot of time to create
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(dataFile));
                try {
                    CitizenActorData citizenActorData = (CitizenActorData) objectInputStream.readObject();
                    ssPrivatePart = citizenActorData.getSsPrivatePart();
                } finally {
                    objectInputStream.close();
                }
            } catch (Exception e) {
                // sadly we have to create a key
                ssPrivatePart = rsaTrustSystem.generatePrivatePart();
            }
        }

        RSAPublicPart publicPart = ssPrivatePart.getPublicPart();

        stateService.addCitizen(ssNumber, pollingStationName, publicPart.getModulus());

        List<PartyService> partyServices = new ArrayList<PartyService>();
        List<Party> partyList = stateService.getPartyList();
        for (Party party : partyList) {
            Registry partyRegistry = LocateRegistry.getRegistry(party.getHost());
            partyServices.add((PartyService) partyRegistry.lookup(party.getRmiName()));
        }

        Citizen citizen = stateService.getCitizen(ssNumber);

        PollingStation pollingStation = stateService.getPollingStation(pollingStationName);

        Registry pollingStationRegistry = LocateRegistry.getRegistry(pollingStation.getHost());

        PollingStationService pollingStationService = (PollingStationService) pollingStationRegistry.lookup(pollingStation.getRmiName());

        if (dataFile != null) {
            CitizenActorData citizenActorData = new CitizenActorData(stateHost, ssNumber, ssPrivatePart);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(dataFile));
            try {
                objectOutputStream.writeObject(citizenActorData);
            } finally {
                objectOutputStream.close();
            }
        }

        return new CitizenActor(ssPrivatePart, rsaTrustSystem, aesUtils, citizen, pollingStation, stateService, pollingStationService, partyServices);
    }

    public static CitizenActor restore(final RSATrustSystem rsaTrustSystem, final AESUtils aesUtils, final File dataFile) throws Exception {

        CitizenActorData citizenActorData;
        ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(dataFile));
        try {
            citizenActorData = (CitizenActorData) objectInputStream.readObject();
        } finally {
            objectInputStream.close();
        }

        Registry registry = LocateRegistry.getRegistry(citizenActorData.getStateHost());
        StateService stateService = (StateService) registry.lookup("State");

        List<PartyService> partyServices = new ArrayList<PartyService>();
        List<Party> partyList = stateService.getPartyList();
        for (Party party : partyList) {
            Registry partyRegistry = LocateRegistry.getRegistry(party.getHost());
            partyServices.add((PartyService) partyRegistry.lookup(party.getRmiName()));
        }

        Citizen citizen = stateService.getCitizen(citizenActorData.getSsNumber());

        String pollingStationName = citizen.getPollingStationName();

        PollingStation pollingStation = stateService.getPollingStation(pollingStationName);

        Registry pollingStationRegistry = LocateRegistry.getRegistry(pollingStation.getHost());

        PollingStationService pollingStationService = (PollingStationService) pollingStationRegistry.lookup(pollingStation.getRmiName());

        RSAPrivatePart ssPrivatePart = citizenActorData.getSsPrivatePart();

        return new CitizenActor(ssPrivatePart, rsaTrustSystem, aesUtils, citizen, pollingStation, stateService, pollingStationService, partyServices);
    }

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);
        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        int argPos = 0;
        String command = args[argPos++];
        if ("create".equals(command)) {
            String stateHost = args[argPos++];
            String ssNumber = args[argPos++];
            String pollingStationName = args[argPos++];
            File dataFile = new File(args[argPos++]);
            create(rsaTrustSystem, aesUtils, stateHost, ssNumber, pollingStationName, dataFile);
        } else if ("vote".equals(command)) {
            File dataFile = new File(args[argPos++]);
            String ballot = args[argPos++];

            CitizenActor citizenActor = restore(rsaTrustSystem, aesUtils, dataFile);

            String[] split = ballot.split(",");
            Judgment[] judgments = new Judgment[split.length];
            for (int i = 0; i < split.length; i++) {
                judgments[i] = Judgment.values()[Integer.parseInt(split[i])];
            }
            citizenActor.vote(new Ballot(judgments));

        } else if ("verify".equals(command)) {
            File dataFile = new File(args[argPos++]);

        }

    }

}
