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

package fr.gaellalire.vote;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.h2.Driver;

/**
 * @author Gael Lalire
 */
public abstract class AbstractLauncher implements Callable<Void> {

    private File config;

    private File data;

    private File cache;

    private boolean insideVestige;

    private Object mutex = new Object();

    public AbstractLauncher(final File config, final File data, final File cache, final boolean insideVestige) {
        this.config = config;
        this.data = data;
        this.cache = cache;
        this.insideVestige = insideVestige;
    }

    public void setVestigeSystem(final VoteVestigeSystem vestigeSystem) {
        if (System.getSecurityManager() != null) {
            // policy activated
            Policy policy = new Policy() {

                private Map<CodeSource, Permissions> permissionsByCodeSource = new HashMap<CodeSource, Permissions>();

                @Override
                public PermissionCollection getPermissions(final CodeSource codesource) {
                    Permissions permissions = permissionsByCodeSource.get(codesource);
                    if (permissions == null || permissions.isReadOnly()) {
                        permissions = new Permissions();
                        permissionsByCodeSource.put(codesource, permissions);
                    }
                    return permissions;
                }

                @Override
                public boolean implies(final ProtectionDomain domain, final Permission permission) {
                    // all permissions
                    return true;
                }

            };
            vestigeSystem.setPolicy(policy);
        }
    }

    public Properties loadProperties(final String fileName) throws IOException {
        Properties properties = new Properties();
        FileInputStream inStream = new FileInputStream(new File(config, fileName));
        try {
            properties.load(inStream);
        } finally {
            inStream.close();
        }
        return properties;
    }

    public Map<String, String> createEntityManagerProperties(final Properties properties) {
        String persistenceName = properties.getProperty("profile.persistence.name");

        Map<String, String> persistenceMapping = new HashMap<>();
        persistenceMapping.put("driver_class", "connection.driver_class");
        persistenceMapping.put("connection.url", "hibernate.connection.url");
        persistenceMapping.put("connection.username", "hibernate.connection.username");
        persistenceMapping.put("connection.password", "hibernate.connection.password");
        persistenceMapping.put("dialect", "hibernate.dialect");

        Map<String, String> entityManagerProperties = new HashMap<>();
        for (Entry<String, String> entry : persistenceMapping.entrySet()) {
            String value = properties.getProperty("persistence." + persistenceName + "." + entry.getKey());
            if (value != null) {
                value = value.replaceAll(Pattern.quote("${vestige.data}"), data.getAbsolutePath());
                entityManagerProperties.put(entry.getValue(), value);
            }
        }
        return entityManagerProperties;
    }

    public File getConfig() {
        return config;
    }

    public File getData() {
        return data;
    }

    public File getCache() {
        return cache;
    }

    public boolean isInsideVestige() {
        return insideVestige;
    }

    public void waitForInterruption() {
        try {
            synchronized (mutex) {
                while (true) {
                    mutex.wait();
                }
            }
        } catch (InterruptedException e) {
            // ok
        }
    }

    public abstract void runService() throws Exception;

    @Override
    public final Void call() throws Exception {
        Driver.load();
        org.postgresql.Driver.isRegistered();

        try {
            runService();
        } finally {

            if (insideVestige) {
                // interrupt thread, BC EntropyGatherer thread leak
                Thread currentThread = Thread.currentThread();
                ThreadGroup threadGroup = currentThread.getThreadGroup();
                int activeCount = threadGroup.activeCount();
                while (activeCount != 1) {
                    Thread[] list = new Thread[activeCount];
                    int enumerate = threadGroup.enumerate(list);
                    for (int i = 0; i < enumerate; i++) {
                        Thread t = list[i];
                        if (t == currentThread) {
                            continue;
                        }
                        t.interrupt();
                    }
                    for (int i = 0; i < enumerate; i++) {
                        Thread t = list[i];
                        if (t == currentThread) {
                            continue;
                        }
                        try {
                            t.join();
                        } catch (InterruptedException e1) {
                            break;
                        }
                    }
                    activeCount = threadGroup.activeCount();
                }
            }

        }

        return null;
    }

}
