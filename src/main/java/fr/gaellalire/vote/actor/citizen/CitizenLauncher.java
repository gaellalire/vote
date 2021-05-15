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

import java.io.File;
import java.security.Permission;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.vote.AbstractLauncher;
import fr.gaellalire.vote.Ballot;
import fr.gaellalire.vote.Judgment;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

/**
 * @author Gael Lalire
 */
public class CitizenLauncher extends AbstractLauncher implements Callable<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitizenLauncher.class);

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(final Permission perm) {
            }
        });

        File targetFile = new File("target/app/citizen");
        if (!targetFile.isDirectory()) {
            targetFile.mkdirs();
            new CitizenInstaller(targetFile).install();
        }
        new CitizenLauncher(targetFile, targetFile, targetFile, false).call();
    }

    public CitizenLauncher(final File config, final File data, final File cache) {
        super(config, data, cache, true);
    }

    public CitizenLauncher(final File config, final File data, final File cache, final boolean insideVestige) {
        super(config, data, cache, insideVestige);
    }

    @Override
    public void runService() throws Exception {
        File data = getData();

        Properties properties = loadProperties("citizen.properties");

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);

        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        String ssNumber = properties.getProperty("ssNumber");
        String pollingStationName = properties.getProperty("pollingStationName");
        File privateKeyFile = new File(data, "ca.key");

        LOGGER.info("Creating citizen");
        CitizenActor citizenActor = CitizenActor.create(rsaTrustSystem, aesUtils, properties.getProperty("stateHost"), ssNumber, pollingStationName, privateKeyFile);

        LOGGER.info("Citizen created");

        String vote = properties.getProperty("vote");
        String[] split = vote.split(",");

        Judgment[] judgments = new Judgment[split.length];
        for (int j = 0; j < split.length; j++) {
            judgments[j] = Judgment.values()[Integer.parseInt(split[j])];
        }

        citizenActor.vote(new Ballot(judgments));

        LOGGER.info("Vote sent");
        // TODO verify our vote
    }

}
