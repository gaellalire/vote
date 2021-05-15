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

package fr.gaellalire.vote.actor.party;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.vestige.spi.trust.TrustException;
import fr.gaellalire.vote.Ballot;
import fr.gaellalire.vote.actor.RemoteActor;
import fr.gaellalire.vote.actor.party.jpa.Citizen;
import fr.gaellalire.vote.actor.party.jpa.Judgment;
import fr.gaellalire.vote.actor.party.jpa.Vote;
import fr.gaellalire.vote.actor.party.service.PartyService;
import fr.gaellalire.vote.actor.polling_station.service.VotingModulusList;
import fr.gaellalire.vote.actor.polling_station.service.VotingSignatureList;
import fr.gaellalire.vote.actor.state.service.PollingStation;
import fr.gaellalire.vote.actor.state.service.StateService;
import fr.gaellalire.vote.trust.aes.AESUtils;
import fr.gaellalire.vote.trust.rsa.RSAPrivatePart;
import fr.gaellalire.vote.trust.rsa.RSAPublicPart;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

/**
 * Political party
 * @author Gael Lalire
 */
public class PartyActor extends RemoteActor implements PartyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartyActor.class);

    private static final long serialVersionUID = 3900801952876087750L;

    private StateService stateService;

    private RSATrustSystem rsaTrustSystem;

    private String partyName;

    protected PartyActor(final EntityManagerFactory entityManagerFactory, final RSAPrivatePart rsaPrivatePart, final String partyName, final StateService stateService,
            final RSATrustSystem rsaTrustSystem) throws RemoteException, TrustException {
        super(entityManagerFactory);
        this.stateService = stateService;
        this.rsaTrustSystem = rsaTrustSystem;
        this.partyName = partyName;
    }

    public void init() throws RemoteException {
        beginTransaction();

        List<PollingStation> pollingStationList = stateService.getPollingStationList();
        for (PollingStation pollingStation : pollingStationList) {

            fr.gaellalire.vote.actor.party.jpa.PollingStation pollingStationJPA = new fr.gaellalire.vote.actor.party.jpa.PollingStation(pollingStation.getName(),
                    pollingStation.getPublicKeyModulusSha512());
            getEntityManager().persist(pollingStationJPA);
        }

        List<fr.gaellalire.vote.actor.state.service.Citizen> citizenList = stateService.getCitizenList();
        for (fr.gaellalire.vote.actor.state.service.Citizen citizen : citizenList) {
            Citizen citizenEntity = new Citizen();
            citizenEntity.setPollingStation(getPollingStationByName(citizen.getPollingStationName()));
            citizenEntity.setPublicKeyModulus(citizen.getPublicKeyModulus());
            citizenEntity.setSSNumber(citizen.getSsNumber());
            getEntityManager().persist(citizenEntity);
        }

        commit();
    }

    @Override
    public void vote(final BigInteger votePublicModulus, final Ballot ballot, final byte[] ballotSignature) throws RemoteException {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<fr.gaellalire.vote.actor.party.jpa.PollingStation> query = criteriaBuilder.createQuery(fr.gaellalire.vote.actor.party.jpa.PollingStation.class);
        Root<fr.gaellalire.vote.actor.party.jpa.PollingStation> rootPollingStation = query.from(fr.gaellalire.vote.actor.party.jpa.PollingStation.class);
        ParameterExpression<String> votePublicModulusParameter = criteriaBuilder.parameter(String.class, "votePublicModulus");

        query.select(rootPollingStation).where(criteriaBuilder.equal(rootPollingStation.join("modulus"), votePublicModulusParameter));

        TypedQuery<fr.gaellalire.vote.actor.party.jpa.PollingStation> typedQuery = getEntityManager().createQuery(query);
        typedQuery.setParameter("votePublicModulus", votePublicModulus.toString(16));
        fr.gaellalire.vote.actor.party.jpa.PollingStation pollingStation = typedQuery.getSingleResult();
        entityManager.refresh(pollingStation);

        byte[] encoded = ballot.getEncoded();
        try {
            if (!rsaTrustSystem.publicPartByModulus(votePublicModulus).verify(new ByteArrayInputStream(encoded), new ByteArrayInputStream(ballotSignature))) {
                throw new RemoteException("Invalid signature");
            }
        } catch (TrustException e) {
            throw new RemoteException("Invalid signature", e);
        }

        beginTransaction();

        Vote vote = new Vote();
        vote.setPollingStation(pollingStation);
        vote.setPublicKeyModulus(votePublicModulus);
        getEntityManager().persist(vote);

        int indice = 0;
        for (fr.gaellalire.vote.Judgment judgment : ballot.getJudgments()) {
            Judgment e = new Judgment();
            e.setVote(vote);
            e.setIndice(indice);
            e.setValue(judgment.ordinal());
            getEntityManager().persist(e);
            indice++;
        }

        commit();

    }

    public static PartyActor create(final RSATrustSystem rsaTrustSystem, final AESUtils aesUtils, final String stateHost, final String host, final String partyName,
            final File privateKeyFile, final Map<String, String> entityManagerProperties) throws Exception {

        StateService stateService = null;
        if (stateService == null) {
            Registry registry = LocateRegistry.getRegistry(stateHost);
            stateService = (StateService) registry.lookup("State");
        }

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("partyPersistenceUnit", entityManagerProperties);

        RSAPrivatePart rsaPrivatePart;
        if (!privateKeyFile.isFile()) {
            // new polling station
            rsaPrivatePart = rsaTrustSystem.generatePrivatePart();

            // register to state

            ObjectOutputStream objectOutputStream = new ObjectOutputStream(new FileOutputStream(privateKeyFile));
            try {
                objectOutputStream.writeObject(rsaPrivatePart);
            } finally {
                objectOutputStream.close();
            }

            stateService.addParty(partyName, host, "Party" + partyName, rsaPrivatePart.getPublicPart().getModulus());

        } else {
            // restore polling station

            ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(privateKeyFile));
            try {
                rsaPrivatePart = (RSAPrivatePart) objectInputStream.readObject();
            } finally {
                objectInputStream.close();
            }

        }

        PartyActor partyActor = new PartyActor(entityManagerFactory, rsaPrivatePart, partyName, stateService, rsaTrustSystem);
        Registry registry = LocateRegistry.getRegistry(host);
        registry.rebind("Party" + partyName, partyActor);
        return partyActor;
    }

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        int argPos = 0;
        String host = args[argPos++];
        String partyName = args[argPos++];
        String privateKeyFileName = args[argPos++];

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);
        RSATrustSystem rsaTrustSystem = new RSATrustSystem(random);
        AESUtils aesUtils = new AESUtils(random);

        File privateKeyFile = new File(privateKeyFileName);

        Map<String, String> entityManagerProperties = new HashMap<>();
        entityManagerProperties.put("connection.driver_class", "org.h2.Driver");
        entityManagerProperties.put("hibernate.connection.url", "jdbc:h2:./db/party" + partyName);
        entityManagerProperties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

        create(rsaTrustSystem, aesUtils, host, host, partyName, privateKeyFile, entityManagerProperties);

    }

    public fr.gaellalire.vote.actor.party.jpa.PollingStation getPollingStationByName(final String name) {
        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<fr.gaellalire.vote.actor.party.jpa.PollingStation> query = criteriaBuilder.createQuery(fr.gaellalire.vote.actor.party.jpa.PollingStation.class);
        Root<fr.gaellalire.vote.actor.party.jpa.PollingStation> rootPollingStation = query.from(fr.gaellalire.vote.actor.party.jpa.PollingStation.class);
        ParameterExpression<String> nameParameter = criteriaBuilder.parameter(String.class, "name");

        query.select(rootPollingStation).where(criteriaBuilder.equal(rootPollingStation.get("name"), nameParameter));

        TypedQuery<fr.gaellalire.vote.actor.party.jpa.PollingStation> typedQuery = getEntityManager().createQuery(query);
        typedQuery.setParameter("name", name);
        fr.gaellalire.vote.actor.party.jpa.PollingStation pollingStation = typedQuery.getSingleResult();
        entityManager.refresh(pollingStation);
        return pollingStation;
    }

    @Override
    public void setPollingStationData(final String pollingStationName, final VotingModulusList votingModulusList, final VotingSignatureList signatureList) throws RemoteException {
        fr.gaellalire.vote.actor.party.jpa.PollingStation pollingStation = getPollingStationByName(pollingStationName);

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

    public PartyResult getPartyResult(final int currentIndice, final List<VoteResult> currentIndiceVoteResults, final long currentIndiceTotalNumber) {
        fr.gaellalire.vote.Judgment judgment = null;
        boolean plus = false;
        double percentPlus = 0;
        double percentMinus = 0;
        long majority = currentIndiceTotalNumber / 2;
        long currentIndiceNumber = 0;
        for (VoteResult voteResult : currentIndiceVoteResults) {
            if (currentIndiceNumber + voteResult.getNumber() > majority) {
                long minusNumber = currentIndiceNumber;
                percentMinus = (((double) minusNumber) / currentIndiceTotalNumber) * 100;
                long plusNumber = currentIndiceTotalNumber - (currentIndiceNumber + voteResult.getNumber());
                if (plusNumber >= minusNumber) {
                    plus = true;
                }
                percentPlus = (((double) plusNumber) / currentIndiceTotalNumber) * 100;
                judgment = voteResult.getJudgment();
                break;
            }
            currentIndiceNumber += voteResult.getNumber();
        }
        return new PartyResult(currentIndice, judgment, plus, percentPlus, percentMinus);
    }

    public void endVote() {
        // 1) send votes to state (maybe a big ZIP in future)
        // 2) state will send us the vote we did not have (using our vote method), we will check their validity as if it was a citizen

        // 3) then calculate the result

        EntityManager entityManager = getEntityManager();
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<VoteResult> query = criteriaBuilder.createQuery(VoteResult.class);
        Root<fr.gaellalire.vote.actor.party.jpa.Judgment> rootJudgment = query.from(fr.gaellalire.vote.actor.party.jpa.Judgment.class);

        query.multiselect(rootJudgment.get("indice"), rootJudgment.get("value"), criteriaBuilder.count(rootJudgment));
        query.groupBy(rootJudgment.get("indice"), rootJudgment.get("value"));
        query.orderBy(criteriaBuilder.asc(rootJudgment.get("indice")), criteriaBuilder.asc(rootJudgment.get("value")));

        TypedQuery<VoteResult> typedQuery = getEntityManager().createQuery(query);

        List<VoteResult> resultList = typedQuery.getResultList();
        int currentIndice = 0;
        long currentIndiceNumber = 0;
        List<PartyResult> partyResults = new ArrayList<PartyResult>();
        List<VoteResult> currentIndiceVoteResults = new ArrayList<VoteResult>();
        LOGGER.info("{}", resultList);
        for (VoteResult voteResult : resultList) {
            if (currentIndice != voteResult.getIndice()) {
                partyResults.add(getPartyResult(currentIndice, currentIndiceVoteResults, currentIndiceNumber));
                currentIndiceVoteResults.clear();
                currentIndiceNumber = 0;
                currentIndice = voteResult.getIndice();
            }
            currentIndiceVoteResults.add(voteResult);
            currentIndiceNumber += voteResult.getNumber();
        }
        partyResults.add(getPartyResult(currentIndice, currentIndiceVoteResults, currentIndiceNumber));
        Collections.sort(partyResults);
        LOGGER.info("{}", partyResults);
    }

    @Override
    public String getName() {
        return partyName;
    }

}
