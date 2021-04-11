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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;

import javax.crypto.Cipher;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import fr.gaellalire.vestige.spi.trust.PublicPart;
import fr.gaellalire.vestige.spi.trust.TrustException;

/**
 * @author Gael Lalire
 */
public class RSAPublicPart implements PublicPart, Serializable {

    private static final long serialVersionUID = 7395249350432971078L;

    private RSAPublicKey publicKey;

    public RSAPublicPart(final PublicKey publicKey) {
        this.publicKey = (RSAPublicKey) publicKey;
    }

    public BigInteger getModulus() {
        return publicKey.getModulus();
    }

    @Override
    public void encrypt(final InputStream is, final OutputStream os) throws TrustException {
        try {
            Cipher cipher = Cipher.getInstance("RSA/None/OAEPWithSHA1AndMGF1Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] buf = new byte[RSATrustSystem.BUFFER_SIZE];
            byte[] bufOutput = new byte[RSATrustSystem.BUFFER_SIZE];
            int len;
            while ((len = is.read(buf)) > 0) {
                int update = cipher.update(buf, 0, len, bufOutput);
                os.write(bufOutput, 0, update);
            }
            os.write(cipher.doFinal());
        } catch (Exception e) {
            throw new TrustException(e);
        }
    }

    @Override
    public boolean verify(final InputStream dataInputStream, final InputStream signatureInputStream) throws TrustException {
        try {
            Signature signature = Signature.getInstance("SHA512withRSA", BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(publicKey);
            byte[] buf = new byte[RSATrustSystem.BUFFER_SIZE];
            int len;
            while ((len = dataInputStream.read(buf)) > 0) {
                signature.update(buf, 0, len);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            while ((len = signatureInputStream.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
            return signature.verify(bos.toByteArray());
        } catch (Exception e) {
            throw new TrustException(e);
        }
    }

}
