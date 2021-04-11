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

package fr.gaellalire.vote.trust.aes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import fr.gaellalire.vestige.spi.trust.TrustException;
import fr.gaellalire.vote.trust.rsa.RSATrustSystem;

public class AESUtils {

    private SecureRandom random;

    public AESUtils(final SecureRandom random) {
        this.random = random;
    }

    public SecretKey generateKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", BouncyCastleProvider.PROVIDER_NAME);
        keyGenerator.init(256, random);
        return keyGenerator.generateKey();
    }

    public byte[] encrypt(final SecretKey secretKey, final InputStream is, final OutputStream os) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
            IvParameterSpec ivParameterClient = new IvParameterSpec(random.generateSeed(16));

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterClient);
            byte[] buf = new byte[RSATrustSystem.BUFFER_SIZE];
            byte[] bufOutput = new byte[RSATrustSystem.BUFFER_SIZE];
            int len;
            while ((len = is.read(buf)) > 0) {
                int update = cipher.update(buf, 0, len, bufOutput);
                os.write(bufOutput, 0, update);
            }
            os.write(cipher.doFinal());
            return ivParameterClient.getIV();
        } catch (Exception e) {
            throw new TrustException(e);
        }
    }

    public void decrypt(final byte[] iv, final SecretKey secretKey, final InputStream is, final OutputStream os) throws Exception {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);

            IvParameterSpec ivParameterSpecServer = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpecServer);
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

    public static void main(final String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        SecureRandom random = SecureRandom.getInstance("DEFAULT", BouncyCastleProvider.PROVIDER_NAME);

        AESUtils aesUtils = new AESUtils(random);

        SecretKey secretKey = aesUtils.generateKey();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] iv = aesUtils.encrypt(secretKey, new ByteArrayInputStream("coucou".getBytes()), os);
        byte[] byteArray = os.toByteArray();
        System.out.println(Arrays.toString(byteArray));
        os.reset();
        aesUtils.decrypt(iv, secretKey, new ByteArrayInputStream(byteArray), os);
        byteArray = os.toByteArray();
        System.out.println(Arrays.toString(byteArray));
        System.out.println(new String(byteArray));

    }

}
