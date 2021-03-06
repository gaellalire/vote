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

package fr.gaellalire.vote;

import java.io.File;
import java.math.BigInteger;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.vote.actor.citizen.CitizenActor;
import fr.gaellalire.vote.actor.citizen.CitizenListener;
import fr.gaellalire.vote.actor.citizen.RMIOverrides;
import fr.gaellalire.vote.actor.party.PartyActor;
import fr.gaellalire.vote.actor.party.service.PartyService;
import fr.gaellalire.vote.actor.polling_station.PollingStationActor;
import fr.gaellalire.vote.actor.polling_station.service.PollingStationService;
import fr.gaellalire.vote.actor.polling_station.service.PollingStationState;
import fr.gaellalire.vote.actor.state.StateActor;
import fr.gaellalire.vote.actor.state.service.StateService;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSAPrivatePart;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

public class VoteDemo extends AbstractLauncher implements Callable<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoteDemo.class);

    /**
     * Allow to test with a lot a citizen without having unlimited available ports
     * @author Gael Lalire
     */
    private static class DemoRMIOverrides implements RMIOverrides {

        private StateService stateService;

        private Map<String, PollingStationService> pollingStationServiceByName = new HashMap<String, PollingStationService>();

        private Map<String, PartyService> partyServiceByName = new HashMap<String, PartyService>();

        @Override
        public StateService getStateService() {
            return stateService;
        }

        @Override
        public PollingStationService getPollingStationService(final String pollingStationName) {
            return pollingStationServiceByName.get(pollingStationName);
        }

        @Override
        public PartyService getPartyService(final String partyName) {
            return partyServiceByName.get(partyName);
        }

    }

    private static class VoteState {

        private CitizenActor citizenActor;

        private BigInteger pollingStationPublicKeyModulus;

        private RSAPrivatePart votingPrivatePart;

        public VoteState(final CitizenActor citizenActor, final BigInteger pollingStationPublicKeyModulus) {
            this.citizenActor = citizenActor;
            this.pollingStationPublicKeyModulus = pollingStationPublicKeyModulus;
        }

    }

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        File targetFile = new File("target");
        if (!targetFile.isDirectory()) {
            targetFile.mkdirs();
        }
        new VoteDemo(targetFile, targetFile, targetFile, false).call();
    }

    public VoteDemo(final File config, final File data, final File cache) {
        super(config, data, cache, true);
    }

    public VoteDemo(final File config, final File data, final File cache, final boolean insideVestige) {
        super(config, data, cache, insideVestige);
    }

    @Override
    public void runService() throws Exception {

        final File data = getData();

        Registry registry = LocateRegistry.createRegistry(1099);
        try {

            final SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);
            // long citizenNumber = 67000000;
            // long pollingStationNumber = 1000;

            final int partyNumber = 5;
            int pollingStationNumber = 3;
            final int citizenThread = 5;
            final long citizenNumber = 500;

            final RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
            final AESUtils aesUtils = new AESUtils(random);

            final DemoRMIOverrides overrides = new DemoRMIOverrides();

            Map<String, String> entityManagerProperties = new HashMap<>();
            entityManagerProperties.put("hibernate.hbm2ddl.auto", "create");
            entityManagerProperties.put("connection.driver_class", "org.h2.Driver");
            entityManagerProperties.put("hibernate.connection.url", "jdbc:h2:" + data.toURI().toURL().toString() + "/db/state");
            entityManagerProperties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

            LOGGER.info("Creating state");
            StateActor stateActor = StateActor.create(rsaTrustSystem, "localhost", new HashMap<>(entityManagerProperties));
            overrides.stateService = stateActor;
            LOGGER.info("State created");
            try {

                LOGGER.info("Creating {} parties", partyNumber);
                List<PartyActor> partyActors = new ArrayList<PartyActor>();
                try {
                    for (int i = 0; i < partyNumber; i++) {
                        File partyActorDataFile = new File(data, "ca" + i + ".data");
                        partyActorDataFile.delete();
                        String partyName = String.valueOf(i);
                        entityManagerProperties.put("hibernate.connection.url", "jdbc:h2:" + data.toURI().toURL().toString() + "/db/party" + partyName);
                        PartyActor partyActor = PartyActor.create(rsaTrustSystem, aesUtils, "localhost", "localhost", partyName, partyActorDataFile,
                                new HashMap<>(entityManagerProperties));
                        partyActors.add(partyActor);
                        overrides.partyServiceByName.put(partyActor.getName(), partyActor);
                    }
                    LOGGER.info("Parties created");

                    // populate polling station

                    LOGGER.info("Creating {} polling stations", pollingStationNumber);
                    List<PollingStationActor> pollingStationActors = new ArrayList<PollingStationActor>(pollingStationNumber);
                    try {
                        for (int i = 0; i < pollingStationNumber; i++) {
                            File privateKeyFile = new File(data, "ps" + i + ".key");
                            privateKeyFile.delete();
                            String pollingStationName = String.valueOf(i);
                            entityManagerProperties.put("hibernate.connection.url", "jdbc:h2:" + data.toURI().toURL().toString() + "/db/pollingStation" + pollingStationName);
                            PollingStationActor pollingStationActor = PollingStationActor.create(rsaTrustSystem, aesUtils, "localhost", "localhost", pollingStationName,
                                    privateKeyFile, new HashMap<>(entityManagerProperties), overrides);
                            pollingStationActors.add(pollingStationActor);
                            overrides.pollingStationServiceByName.put(pollingStationActor.getName(), pollingStationActor);
                        }
                        LOGGER.info("Polling stations created");

                        // populate citizen
                        LOGGER.info("Creating {} citizens", citizenNumber);
                        for (int i = 0; i < citizenNumber; i++) {
                            File citizenActorDataFile = new File(data, "ca" + i + ".data");
                            // citizenActorDataFile.delete();
                            CitizenActor.create(rsaTrustSystem, aesUtils, "localhost", "SS" + i, String.valueOf(i % pollingStationNumber), citizenActorDataFile, overrides);
                        }
                        LOGGER.info("Citizens created");

                        // create a vote

                        LOGGER.info("Initiating parties data");
                        for (PartyActor partyActor : partyActors) {
                            partyActor.init();
                        }
                        LOGGER.info("Parties data initiated");

                        LOGGER.info("Initiating vote");
                        stateActor.initVote();
                        LOGGER.info("Vote initiated");

                        // 6 million -> 9 seconds
                        // 60 -> 90 seconds ?

                        LOGGER.info("Start the vote");

                        final long[] remain = new long[1];
                        remain[0] = citizenNumber;
                        final CitizenListener citizenListener = new CitizenListener() {

                            @Override
                            public void voteDone() {
                                synchronized (remain) {
                                    remain[0]--;
                                    if (remain[0] == 0) {
                                        remain.notify();
                                    }
                                }
                            }

                            @Override
                            public void registerDone() {
                                synchronized (remain) {
                                    remain[0]--;
                                    if (remain[0] == 0) {
                                        remain.notify();
                                    }
                                }

                            }
                        };

                        // we cannot have a thread per citizen, it costs too much memory
                        for (int i = 0; i < citizenThread; i++) {
                            final int finalI = i;
                            new Thread("citizen-thread" + i) {
                                public void run() {
                                    try {

                                        List<VoteState> voteStates = new ArrayList<VoteState>();

                                        for (int j = finalI; j < citizenNumber; j += citizenThread) {
                                            File citizenActorDataFile = new File(data, "ca" + j + ".data");
                                            final CitizenActor citizenActor = CitizenActor.restore(rsaTrustSystem, aesUtils, citizenActorDataFile, overrides);
                                            BigInteger pollingStationPublicKeyModulus = citizenActor.register();
                                            citizenListener.registerDone();
                                            voteStates.add(new VoteState(citizenActor, pollingStationPublicKeyModulus));
                                        }

                                        for (VoteState voteState : voteStates) {
                                            voteState.citizenActor.waitFor(PollingStationState.WAIT_FOR_VOTING_KEYS);
                                            voteState.votingPrivatePart = voteState.citizenActor.sendVotingPublicPartModulus(voteState.pollingStationPublicKeyModulus);
                                        }

                                        for (VoteState voteState : voteStates) {
                                            voteState.citizenActor.waitFor(PollingStationState.WAIT_FOR_SIGNATURE);
                                            voteState.citizenActor.sendVotingModulusListSignature(voteState.votingPrivatePart.getPublicPart().getModulus());
                                        }

                                        for (VoteState voteState : voteStates) {
                                            voteState.citizenActor.waitFor(PollingStationState.WORK_DONE);
                                            Judgment[] judgments = new Judgment[partyNumber];
                                            for (int j = 0; j < partyNumber; j++) {
                                                judgments[j] = Judgment.values()[(int) (random.nextDouble() * (Judgment.values().length - 1))];
                                            }

                                            voteState.citizenActor.sendVote(voteState.votingPrivatePart, new Ballot(judgments));
                                            citizenListener.voteDone();
                                        }
                                    } catch (Exception e) {
                                        LOGGER.error("Unable to vote", e);
                                    }

                                }
                            }.start();
                        }

                        // in reality it will be a fixed time
                        synchronized (remain) {
                            while (remain[0] != 0) {
                                remain.wait();
                            }
                        }

                        LOGGER.info("endRegisteringPeriod");
                        remain[0] = citizenNumber;

                        for (int i = 0; i < pollingStationNumber; i++) {
                            for (PollingStationActor pollingStationActor : pollingStationActors) {
                                pollingStationActor.endRegisteringPeriod();
                            }
                        }

                        // in reality it will be a fixed time
                        synchronized (remain) {
                            while (remain[0] != 0) {
                                remain.wait();
                            }
                        }

                        LOGGER.info("Stop the vote");
                        for (PartyActor partyActor : partyActors) {
                            partyActor.endVote();
                        }

                    } finally {
                        for (PollingStationActor pollingStationActor : pollingStationActors) {
                            pollingStationActor.close();
                        }
                    }

                } finally {
                    for (PartyActor partyActor : partyActors) {
                        partyActor.close();
                    }
                }

            } finally {
                stateActor.close();
            }

        } finally {
            UnicastRemoteObject.unexportObject(registry, true);
        }

        // 67 millions -> 513 seconds (8 minutes) + 3,6G de donn??es

        // with private key generation
        // 30 seconds for 500 citizens
        // 4020000 seconds, 67000 minutes, 1116 heures, 46 jours pour 67 millions

        // without private key generation
        // 7 seconds for 500 citizens, 10 jours pour 67 millions

        // vote time
        // 16 seconds for 500 citizens : 20 jours pour 67 millions

    }

}
