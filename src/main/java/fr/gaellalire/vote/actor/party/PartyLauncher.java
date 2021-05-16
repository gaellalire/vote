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

package fr.gaellalire.vote.actor.party;

import java.io.File;
import java.security.Permission;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.vote.AbstractLauncher;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

/**
 * @author Gael Lalire
 */
public class PartyLauncher extends AbstractLauncher implements Callable<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyLauncher.class);

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(final Permission perm) {
            }
        });

        File targetFile = new File("target/app/party");
        if (!targetFile.isDirectory()) {
            targetFile.mkdirs();
            new PartyInstaller(targetFile).install();
        }
        new PartyLauncher(targetFile, targetFile, targetFile, false).call();
    }

    public PartyLauncher(final File config, final File data, final File cache) {
        super(config, data, cache, true);
    }

    public PartyLauncher(final File config, final File data, final File cache, final boolean insideVestige) {
        super(config, data, cache, insideVestige);
    }

    @Override
    public void runService() throws Exception {
        File data = getData();

        Properties properties = loadProperties("party.properties");

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);

        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        Map<String, String> entityManagerProperties = createEntityManagerProperties(properties);

        String partyName = properties.getProperty("name");
        File privateKeyFile = new File(data, "ps.key");

        LOGGER.info("Creating party {}", partyName);
        PartyActor partyActor = PartyActor.create(rsaTrustSystem, aesUtils, properties.getProperty("stateHost"), properties.getProperty("host"), partyName, privateKeyFile,
                entityManagerProperties);

        // all polling station and citizen must be known by state when started
        long until;
        String property = properties.getProperty("init");
        if (property.startsWith("+")) {
            property = property.substring(1);
            until = System.currentTimeMillis() + Long.parseLong(property);
        } else {
            until = Long.parseLong(property);
        }

        LOGGER.info("Party created");
        try {
            if (waitForInterruption(until)) {
                partyActor.init();
                LOGGER.info("Party initiated");

                property = properties.getProperty("endVote");
                if (property.startsWith("+")) {
                    property = property.substring(1);
                    until = System.currentTimeMillis() + Long.parseLong(property);
                } else {
                    until = Long.parseLong(property);
                }
                if (waitForInterruption(until)) {
                    LOGGER.info("Party end of vote");
                    partyActor.endVote();
                    waitForInterruption();
                }
            }
        } finally {
            partyActor.close();
        }

    }

}
