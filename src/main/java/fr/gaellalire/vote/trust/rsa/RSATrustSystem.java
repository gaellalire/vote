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

package fr.gaellalire.vote.trust.rsa;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import fr.gaellalire.vestige.spi.trust.PrivatePart;
import fr.gaellalire.vestige.spi.trust.Signature;
import fr.gaellalire.vestige.spi.trust.TrustException;
import fr.gaellalire.vestige.spi.trust.TrustSystem;

/**
 * @author Gael Lalire
 */
public class RSATrustSystem implements TrustSystem {

    public static BigInteger publicExponent = new BigInteger("10001", 16);

    public static final int BUFFER_SIZE = 1024;

    private SecureRandom random;

    public RSATrustSystem(final SecureRandom random) {
        this.random = random;
    }

    private KeyPair generateKeyPair() throws TrustException {
        try {
            RSAKeyPairGenerator keyGen = new RSAKeyPairGenerator();
            keyGen.init(new RSAKeyGenerationParameters(publicExponent, random, 1024, 80));
            AsymmetricCipherKeyPair keys = keyGen.generateKeyPair();

            RSAPrivateCrtKeyParameters rsaKeyParameters = (RSAPrivateCrtKeyParameters) keys.getPrivate();
            RSAPrivateKeySpec privateSpec = new RSAPrivateCrtKeySpec(rsaKeyParameters.getModulus(), publicExponent, rsaKeyParameters.getExponent(), rsaKeyParameters.getP(),
                    rsaKeyParameters.getQ(), rsaKeyParameters.getDP(), rsaKeyParameters.getDQ(), rsaKeyParameters.getQInv());
            KeyFactory factory = KeyFactory.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);

            PrivateKey privateKey = factory.generatePrivate(privateSpec);
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(keys.getPublic()).getEncoded()));

            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            throw new TrustException(e);
        }
    }

    public RSAPublicPart publicPartByModulus(final BigInteger modulus) {
        return new RSAPublicPart(new RSAPublicKey() {

            private static final long serialVersionUID = -8674185657460479104L;

            @Override
            public BigInteger getModulus() {
                return modulus;
            }

            @Override
            public String getFormat() {
                return "X.509";
            }

            @Override
            public byte[] getEncoded() {
                return null;
            }

            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public BigInteger getPublicExponent() {
                return publicExponent;
            }
        });

    }

    public RSAPrivatePart generatePrivatePart() throws TrustException {
        KeyPair keyPair = generateKeyPair();

        RSAPublicPart rsaPublicPart = new RSAPublicPart(keyPair.getPublic());

        return new RSAPrivatePart(rsaPublicPart, keyPair.getPrivate());
    }

    @Override
    public Signature loadSignature(final InputStream inputStream) throws TrustException {
        return null;
    }

    @Override
    public PrivatePart getDefaultPrivatePart() throws TrustException {
        return null;
    }

}
