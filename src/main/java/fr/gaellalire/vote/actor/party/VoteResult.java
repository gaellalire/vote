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
public class VoteResult {

    private int indice;

    private Judgment judgment;

    private long number;

    public VoteResult(final int indice, final int judgment, final long number) {
        this.indice = indice;
        this.judgment = Judgment.values()[judgment];
        this.number = number;
    }

    @Override
    public String toString() {
        return "VoteResult [indice=" + indice + ", judgment=" + judgment + ", number=" + number + "]";
    }

    public int getIndice() {
        return indice;
    }

    public Judgment getJudgment() {
        return judgment;
    }

    public long getNumber() {
        return number;
    }

}
