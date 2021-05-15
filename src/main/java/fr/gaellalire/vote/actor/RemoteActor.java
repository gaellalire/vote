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

package fr.gaellalire.vote.actor;

import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * @author Gael Lalire
 */
public abstract class RemoteActor extends UnicastRemoteObject {

    private static final long serialVersionUID = -2689029993776945523L;

    private EntityManagerFactory entityManagerFactory;

    private Map<Thread, EntityManager> entityManagerByThread = new HashMap<Thread, EntityManager>();

    private ThreadLocal<EntityManager> entityManagerThreadLocal = new ThreadLocal<EntityManager>();

    private Map<String, String> properties;

    protected RemoteActor(final EntityManagerFactory entityManagerFactory) throws RemoteException {
        this(entityManagerFactory, null);
    }

    protected RemoteActor(final EntityManagerFactory entityManagerFactory, final Map<String, String> properties) throws RemoteException {
        super();
        this.properties = properties;
        this.entityManagerFactory = entityManagerFactory;
    }

    public void clean() {
        synchronized (entityManagerByThread) {
            Iterator<Entry<Thread, EntityManager>> iterator = entityManagerByThread.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<Thread, EntityManager> entry = iterator.next();
                if (!entry.getKey().isAlive()) {
                    entry.getValue().close();
                    iterator.remove();
                }
            }
        }
    }

    public EntityManager getEntityManager() {
        EntityManager em = entityManagerThreadLocal.get();

        if (em == null) {
            if (properties == null) {
                em = entityManagerFactory.createEntityManager();
            } else {
                em = entityManagerFactory.createEntityManager(properties);
            }
            synchronized (entityManagerByThread) {
                entityManagerByThread.put(Thread.currentThread(), em);
            }
            entityManagerThreadLocal.set(em);
        }
        return em;
    }

    public void closeEntityManager() {
        EntityManager em = entityManagerThreadLocal.get();
        if (em != null) {
            em.close();
            entityManagerThreadLocal.remove();
        }
    }

    public void beginTransaction() {
        getEntityManager().getTransaction().begin();
    }

    public void rollback() {
        getEntityManager().getTransaction().rollback();
    }

    public void commit() {
        getEntityManager().getTransaction().commit();
    }

    public void close() {
        synchronized (entityManagerByThread) {
            for (Entry<Thread, EntityManager> entry : entityManagerByThread.entrySet()) {
                entry.getValue().close();
            }
        }
        entityManagerFactory.close();
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            // ignore
        }
    }

}
