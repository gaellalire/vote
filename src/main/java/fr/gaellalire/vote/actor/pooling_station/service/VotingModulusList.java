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

package fr.gaellalire.vote.actor.pooling_station.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gael Lalire
 */
public class VotingModulusList implements Serializable {

    private static final long serialVersionUID = 950121547028008668L;

    private List<BigInteger> modulus;

    public VotingModulusList() {
        modulus = new ArrayList<BigInteger>();
    }

    public List<BigInteger> getModulus() {
        return modulus;
    }

    public byte[] getEncoded() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        for (BigInteger bigInteger : modulus) {
            try {
                byteArrayOutputStream.write(bigInteger.toByteArray());
            } catch (IOException e) {
                // not possible
            }
            byteArrayOutputStream.write(0);
        }
        return byteArrayOutputStream.toByteArray();
    }

}
