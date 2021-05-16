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

package fr.gaellalire.vote.actor.polling_station;

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
public class PollingStationLauncher extends AbstractLauncher implements Callable<Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingStationLauncher.class);

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkPermission(final Permission perm) {
            }
        });

        File targetFile = new File("target/app/pollingStation");
        if (!targetFile.isDirectory()) {
            targetFile.mkdirs();
            new PollingStationInstaller(targetFile).install();
        }
        new PollingStationLauncher(targetFile, targetFile, targetFile, false).call();
    }

    public PollingStationLauncher(final File config, final File data, final File cache) {
        super(config, data, cache, true);
    }

    public PollingStationLauncher(final File config, final File data, final File cache, final boolean insideVestige) {
        super(config, data, cache, insideVestige);
    }

    @Override
    public void runService() throws Exception {
        File data = getData();

        Properties properties = loadProperties("pollingStation.properties");

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);

        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        String pollingStationName = properties.getProperty("name");
        File privateKeyFile = new File(data, "ps.key");

        Map<String, String> entityManagerProperties = createEntityManagerProperties(properties);

        LOGGER.info("Creating polling station {}", pollingStationName);
        PollingStationActor pollingStationActor = PollingStationActor.create(rsaTrustSystem, aesUtils, properties.getProperty("stateHost"), properties.getProperty("host"),
                pollingStationName, privateKeyFile, entityManagerProperties);

        long until;
        String property = properties.getProperty("endRegisteringPeriod");
        if (property.startsWith("+")) {
            property = property.substring(1);
            until = System.currentTimeMillis() + Long.parseLong(property);
        } else {
            until = Long.parseLong(property);
        }

        LOGGER.info("Polling station created");
        try {
            if (waitForInterruption(until)) {
                LOGGER.info("Polling station end of registering");
                pollingStationActor.endRegisteringPeriod();
                waitForInterruption();
            }
        } finally {
            pollingStationActor.close();
        }

    }

}
