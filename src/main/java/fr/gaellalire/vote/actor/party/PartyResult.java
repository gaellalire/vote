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

import fr.gaellalire.vote.Judgment;

/**
 * @author Gael Lalire
 */
public class PartyResult implements Comparable<PartyResult> {

    private int partyNumber;

    private Judgment judgment;

    private boolean plus;

    private double percentPlus;

    private double percentMinus;

    public PartyResult(final int partyNumber, final Judgment judgment, final boolean plus, final double percentPlus, final double percentMinus) {
        this.partyNumber = partyNumber;
        this.judgment = judgment;
        this.plus = plus;
        this.percentPlus = percentPlus;
        this.percentMinus = percentMinus;
    }

    @Override
    public int compareTo(final PartyResult o) {
        // https://scienceetonnante.com/2016/10/21/reformons-lelection-presidentielle/

        if (judgment.ordinal() != o.judgment.ordinal()) {
            // higher judgment win
            return o.judgment.ordinal() - judgment.ordinal();
        }
        if (plus != o.plus) {
            // plus win
            if (plus) {
                return -1;
            } else {
                return 1;
            }
        }
        if (percentPlus != o.percentPlus) {
            // greater percent win
            if (percentPlus - o.percentPlus < 0) {
                return 1;
            } else {
                return -1;
            }
        }
        if (percentMinus != o.percentMinus) {
            // lesser percent win
            if (percentMinus - o.percentMinus > 0) {
                return 1;
            } else {
                return -1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return "PartyResult [partyNumber=" + partyNumber + ", judgment=" + judgment + ", plus=" + plus + ", percentPlus=" + percentPlus + ", percentMinus=" + percentMinus + "]";
    }

}
