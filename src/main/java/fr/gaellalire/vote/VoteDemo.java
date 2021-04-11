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
import java.rmi.registry.LocateRegistry;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.vote.actor.citizen.CitizenActor;
import fr.gaellalire.vote.actor.citizen.CitizenListener;
import fr.gaellalire.vote.actor.party.PartyActor;
import fr.gaellalire.vote.actor.pooling_station.PollingStationActor;
import fr.gaellalire.vote.actor.state.StateActor;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

public class VoteDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoteDemo.class);

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        File targetFile = new File("target");
        if (!targetFile.isDirectory()) {
            targetFile.mkdirs();
        }

        LocateRegistry.createRegistry(1099);

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);
        // long citizenNumber = 67000000;
        // long pollingStationNumber = 1000;

        int partyNumber = 5;
        int pollingStationNumber = 3;
        long citizenNumber = 50;

        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        LOGGER.info("Creating state");
        StateActor.create(rsaTrustSystem, "localhost");
        LOGGER.info("State created");

        LOGGER.info("Creating {} parties", partyNumber);
        List<PartyActor> partyActors = new ArrayList<PartyActor>();
        for (int i = 0; i < partyNumber; i++) {
            File partyActorDataFile = new File(targetFile, "ca" + i + ".data");
            partyActorDataFile.delete();
            partyActors.add(PartyActor.create(rsaTrustSystem, aesUtils, "localhost", String.valueOf(i), partyActorDataFile));
        }
        LOGGER.info("Parties created");

        // populate polling station

        LOGGER.info("Creating {} polling stations", pollingStationNumber);
        List<PollingStationActor> pollingStationActors = new ArrayList<PollingStationActor>(pollingStationNumber);
        for (int i = 0; i < pollingStationNumber; i++) {
            File privateKeyFile = new File(targetFile, "ps" + i + ".key");
            privateKeyFile.delete();
            pollingStationActors.add(PollingStationActor.create(rsaTrustSystem, aesUtils, "localhost", String.valueOf(i), privateKeyFile));
        }
        LOGGER.info("Polling stations created");

        // populate citizen
        LOGGER.info("Creating {} citizens", citizenNumber);
        for (int i = 0; i < citizenNumber; i++) {
            File citizenActorDataFile = new File(targetFile, "ca" + i + ".data");
            // citizenActorDataFile.delete();
            CitizenActor.create(rsaTrustSystem, aesUtils, "localhost", "SS" + i, String.valueOf(i % pollingStationNumber), citizenActorDataFile);
        }
        LOGGER.info("Citizens created");

        // create a vote

        LOGGER.info("Initiating parties data");
        for (PartyActor partyActor : partyActors) {
            partyActor.init();
        }
        LOGGER.info("Parties data initiated");

        // 6 million -> 9 seconds
        // 60 -> 90 seconds ?

        LOGGER.info("Start the vote");

        long[] remain = new long[1];
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

        for (int i = 0; i < citizenNumber; i++) {
            File citizenActorDataFile = new File(targetFile, "ca" + i + ".data");
            final CitizenActor citizenActor = CitizenActor.restore(rsaTrustSystem, aesUtils, citizenActorDataFile);
            new Thread("citizen" + i) {
                public void run() {
                    try {
                        Judgment[] judgments = new Judgment[partyNumber];
                        for (int j = 0; j < partyNumber; j++) {
                            judgments[j] = Judgment.values()[(int) (random.nextDouble() * (Judgment.values().length - 1))];
                        }
                        citizenActor.vote(new Ballot(judgments), citizenListener);
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

        System.exit(0);

        // 67 millions -> 513 seconds (8 minutes) + 3,6G de données

        // with private key generation
        // 30 seconds for 500 citizens
        // 4020000 seconds, 67000 minutes, 1116 heures, 46 jours pour 67 millions

        // without private key generation
        // 7 seconds for 500 citizens, 10 jours pour 67 millions

    }

}
