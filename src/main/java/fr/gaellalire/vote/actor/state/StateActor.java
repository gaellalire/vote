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

package fr.gaellalire.vote.actor.state;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import fr.gaellalire.vestige.spi.trust.TrustException;
import fr.gaellalire.vote.actor.RemoteActor;
import fr.gaellalire.vote.actor.pooling_station.service.VotingModulusList;
import fr.gaellalire.vote.actor.pooling_station.service.VotingSignatureList;
import fr.gaellalire.vote.actor.state.jpa.Citizen;
import fr.gaellalire.vote.actor.state.jpa.CitizenApproval;
import fr.gaellalire.vote.actor.state.jpa.CitizenApprovalType;
import fr.gaellalire.vote.actor.state.jpa.Party;
import fr.gaellalire.vote.actor.state.jpa.PollingStation;
import fr.gaellalire.vote.actor.state.service.Approval;
import fr.gaellalire.vote.actor.state.service.StateService;
import fr.gaellalire.vote.trust.rsa.RSAPublicPart;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

// 67 millions (3 millions a paris)
// 1000 electeurs par bureau
// 67 000 bureaux ...
public class StateActor extends RemoteActor implements StateService {

    private static final long serialVersionUID = 932148319038559136L;

    private RSATrustSystem rsaTrustSystem;

    public StateActor(final EntityManagerFactory entityManagerFactory, final RSATrustSystem rsaTrustSystem) throws RemoteException {
        super(entityManagerFactory);
        this.rsaTrustSystem = rsaTrustSystem;
    }

    @Override
    public void addParty(final String name, final String host, final String rmiName, final BigInteger publicKeyModulus) throws RemoteException {
        beginTransaction();

        Party pollingStation = new Party(name, host, rmiName);
        getEntityManager().persist(pollingStation);

        commit();
    }

    public void addPollingStation(final String name, final String host, final String rmiName, final byte[] publicKeyModulusSha512) {
        beginTransaction();

        PollingStation pollingStation = new PollingStation(name, host, rmiName, publicKeyModulusSha512);
        getEntityManager().persist(pollingStation);

        commit();
    }

    public void addCitizen(final String ssNumber, final String pollingStationName, final BigInteger publicKeyModulus) {
        beginTransaction();

        PollingStation pollingStation = getPollingStationByName(pollingStationName);

        Citizen citizen = new Citizen();
        citizen.setSSNumber(ssNumber);
        citizen.setPublicKeyModulus(publicKeyModulus);
        citizen.setPollingStation(pollingStation);
        getEntityManager().persist(citizen);

        commit();
    }

    @Override
    public List<fr.gaellalire.vote.actor.state.service.PollingStation> getPollingStationList() throws RemoteException {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<PollingStation> query = criteriaBuilder.createQuery(PollingStation.class);
        Root<PollingStation> rootPollingStation = query.from(PollingStation.class);

        query.select(rootPollingStation);

        TypedQuery<PollingStation> typedQuery = getEntityManager().createQuery(query);
        List<PollingStation> resultList = typedQuery.getResultList();
        List<fr.gaellalire.vote.actor.state.service.PollingStation> result = new ArrayList<fr.gaellalire.vote.actor.state.service.PollingStation>();
        for (PollingStation pollingStation : resultList) {
            entityManager.refresh(pollingStation);
            result.add(convertToServicePollingStation(pollingStation));
        }
        return result;
    }

    @Override
    public List<fr.gaellalire.vote.actor.state.service.Party> getPartyList() {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Party> query = criteriaBuilder.createQuery(Party.class);
        Root<Party> rootParty = query.from(Party.class);

        query.select(rootParty);

        TypedQuery<Party> typedQuery = getEntityManager().createQuery(query);
        List<Party> resultList = typedQuery.getResultList();
        List<fr.gaellalire.vote.actor.state.service.Party> result = new ArrayList<fr.gaellalire.vote.actor.state.service.Party>();
        for (Party party : resultList) {
            entityManager.refresh(party);
            result.add(new fr.gaellalire.vote.actor.state.service.Party(party.getHost(), party.getRmiName()));
        }
        return result;
    }

    public PollingStation getPollingStationByName(final String name) {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<PollingStation> query = criteriaBuilder.createQuery(PollingStation.class);
        Root<PollingStation> rootPollingStation = query.from(PollingStation.class);
        ParameterExpression<String> nameParameter = criteriaBuilder.parameter(String.class, "name");

        query.select(rootPollingStation).where(criteriaBuilder.equal(rootPollingStation.get("name"), nameParameter));

        TypedQuery<PollingStation> typedQuery = getEntityManager().createQuery(query);
        typedQuery.setParameter("name", name);
        PollingStation pollingStation = typedQuery.getSingleResult();
        entityManager.refresh(pollingStation);
        return pollingStation;
    }

    public Citizen getCitizenBySS(final String ssNumber) {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Citizen> query = criteriaBuilder.createQuery(Citizen.class);
        Root<Citizen> rootCitizen = query.from(Citizen.class);
        ParameterExpression<String> ssNumberParameter = criteriaBuilder.parameter(String.class, "ssNumber");

        query.select(rootCitizen).where(criteriaBuilder.equal(rootCitizen.get("ssNumber"), ssNumberParameter));

        TypedQuery<Citizen> typedQuery = entityManager.createQuery(query);
        typedQuery.setParameter("ssNumber", ssNumber);
        return typedQuery.getSingleResult();
    }

    public byte[] generateApproval(final String approbatorSSNumber, final String approvedSSNumber,
            final fr.gaellalire.vote.actor.state.service.CitizenApprovalType citizenApprovalType) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(approbatorSSNumber);
        stringBuilder.append(",");
        stringBuilder.append(approvedSSNumber);
        stringBuilder.append(",");
        stringBuilder.append(citizenApprovalType);

        try {
            return stringBuilder.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    public boolean approve(final String approbatorSSNumber, final String approvedSSNumber, final fr.gaellalire.vote.actor.state.service.CitizenApprovalType citizenApprovalType,
            final byte[] signature) {
        Citizen approbatorCitizen = getCitizenBySS(approbatorSSNumber);
        Citizen approvedCitizen = getCitizenBySS(approvedSSNumber);

        if (approbatorCitizen == null || approvedCitizen == null) {
            return false;
        }

        RSAPublicPart approbatorPublicPart = rsaTrustSystem.publicPartByModulus(approbatorCitizen.getPublicKeyModulus());

        boolean verified;
        try {
            verified = approbatorPublicPart.verify(new ByteArrayInputStream(generateApproval(approbatorSSNumber, approvedSSNumber, citizenApprovalType)),
                    new ByteArrayInputStream(signature));
        } catch (Exception e) {
            verified = false;
        }
        if (!verified) {
            return false;
        }

        beginTransaction();

        CitizenApproval citizenApproval = new CitizenApproval();
        citizenApproval.setApprobator(approbatorCitizen);
        citizenApproval.setApproved(approvedCitizen);
        citizenApproval.setApprovalType(CitizenApprovalType.valueOf(citizenApprovalType.name()));
        citizenApproval.setSignature(signature);

        getEntityManager().persist(citizenApproval);

        commit();

        return true;
    }

    public BigInteger verifyIdentity(final String ssNumber, final byte[] collectedBiometricData) {

        Citizen citizen = getCitizenBySS(ssNumber);
        if (citizen == null) {
            return null;
        }
        if (!citizen.isSame(collectedBiometricData)) {
            return null;
        }
        return citizen.getPublicKeyModulus();
    }

    @Override
    public boolean verifyCitizen(final String ssNumber, final byte[] collectedBiometricData) {
        Citizen citizenBySS = getCitizenBySS(ssNumber);
        if (citizenBySS.isSame(collectedBiometricData)) {
            return true;
        }
        return false;
    }

    @Override
    public fr.gaellalire.vote.actor.state.service.PollingStation searchPollingStationByVotePublicModulus(final BigInteger votePublicModulus) throws RemoteException {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<PollingStation> query = criteriaBuilder.createQuery(PollingStation.class);
        Root<PollingStation> rootPollingStation = query.from(PollingStation.class);
        ParameterExpression<String> nameParameter = criteriaBuilder.parameter(String.class, "name");

        query.select(rootPollingStation).where(criteriaBuilder.equal(rootPollingStation.get("name"), nameParameter));

        TypedQuery<PollingStation> typedQuery = getEntityManager().createQuery(query);
        // typedQuery.setParameter("name", name);
        PollingStation pollingStation = typedQuery.getSingleResult();
        entityManager.refresh(pollingStation);
        return convertToServicePollingStation(pollingStation);
    }

    public fr.gaellalire.vote.actor.state.service.PollingStation convertToServicePollingStation(final PollingStation pollingStation) {
        List<Citizen> citizens = pollingStation.getCitizens();
        List<String> ssNumbers = new ArrayList<String>();
        for (Citizen citizen : citizens) {
            ssNumbers.add(citizen.getSSNumber());
        }
        return new fr.gaellalire.vote.actor.state.service.PollingStation(pollingStation.getName(), pollingStation.getHost(), pollingStation.getRmiName(),
                pollingStation.getPublicKeyModulusSha512(), ssNumbers);
    }

    public fr.gaellalire.vote.actor.state.service.PollingStation getPollingStation(final String name) {
        PollingStation pollingStation = getPollingStationByName(name);
        return convertToServicePollingStation(pollingStation);
    }

    public fr.gaellalire.vote.actor.state.service.Citizen convertToServiceCitizen(final Citizen citizen) {
        List<Approval> approbatorOf = new ArrayList<Approval>();
        List<Approval> approvedBy = new ArrayList<Approval>();
        for (CitizenApproval citizenApproval : citizen.getApprobatorOf()) {
            approbatorOf.add(new Approval(citizenApproval.getSignature(), citizenApproval.getApproved().getSSNumber(),
                    fr.gaellalire.vote.actor.state.service.CitizenApprovalType.valueOf(citizenApproval.getApprovalType().name())));
        }
        for (CitizenApproval citizenApproval : citizen.getApprovedBy()) {
            approvedBy.add(new Approval(citizenApproval.getSignature(), citizenApproval.getApprobator().getSSNumber(),
                    fr.gaellalire.vote.actor.state.service.CitizenApprovalType.valueOf(citizenApproval.getApprovalType().name())));
        }

        return new fr.gaellalire.vote.actor.state.service.Citizen(citizen.getSSNumber(), citizen.getPollingStation().getName(), citizen.getPublicKeyModulus(), approvedBy,
                approbatorOf);
    }

    public fr.gaellalire.vote.actor.state.service.Citizen getCitizen(final String ssNumber) {
        Citizen citizenBySS = getCitizenBySS(ssNumber);
        getEntityManager().refresh(citizenBySS);
        return convertToServiceCitizen(citizenBySS);
    }

    public static StateActor create(final RSATrustSystem rsaTrustSystem, final String host) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host);

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("statePersistenceUnit");

        StateActor stateActor = new StateActor(entityManagerFactory, rsaTrustSystem);
        registry.rebind("State", stateActor);
        return stateActor;
    }

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);

        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);

        create(rsaTrustSystem, InetAddress.getLocalHost().getHostAddress());
    }

    @Override
    public void setPollingStationData(final String pollingStationName, final VotingModulusList votingModulusList, final VotingSignatureList signatureList) throws RemoteException {
        PollingStation pollingStation = getPollingStationByName(pollingStationName);

        byte[] encoded = votingModulusList.getEncoded();

        Map<String, byte[]> signatureBySSNumber = signatureList.getSignatureBySSNumber();

        List<Citizen> citizens = pollingStation.getCitizens();
        if (citizens.size() != signatureBySSNumber.size()) {
            // not enough signature
            return;
        }

        beginTransaction();

        for (Citizen citizen : citizens) {
            String ssNumber = citizen.getSSNumber();
            byte[] signature = signatureBySSNumber.get(ssNumber);
            if (signature == null) {
                // invalid list
                rollback();
                return;
            }
            RSAPublicPart rsaPublicPart = rsaTrustSystem.publicPartByModulus(citizen.getPublicKeyModulus());
            try {
                if (!rsaPublicPart.verify(new ByteArrayInputStream(encoded), new ByteArrayInputStream(signature))) {
                    // invalid list
                    rollback();
                    return;
                }
            } catch (TrustException e) {
                // invalid list
                rollback();
                return;
            }
            citizen.setPollingStationModulusListSignature(signature);
        }

        // list is signed by all citizen of pollingStation, we can save it

        List<String> modulus = pollingStation.getModulus();
        for (BigInteger bigInteger : votingModulusList.getModulus()) {
            modulus.add(bigInteger.toString(16));
        }

        commit();

    }

    @Override
    public List<fr.gaellalire.vote.actor.state.service.Citizen> getCitizenList() throws RemoteException {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Citizen> query = criteriaBuilder.createQuery(Citizen.class);
        Root<Citizen> rootPollingStation = query.from(Citizen.class);

        query.select(rootPollingStation);

        TypedQuery<Citizen> typedQuery = getEntityManager().createQuery(query);
        List<Citizen> resultList = typedQuery.getResultList();
        List<fr.gaellalire.vote.actor.state.service.Citizen> result = new ArrayList<fr.gaellalire.vote.actor.state.service.Citizen>();
        for (Citizen citizen : resultList) {
            entityManager.refresh(citizen);
            result.add(convertToServiceCitizen(citizen));
        }
        return result;
    }

}
