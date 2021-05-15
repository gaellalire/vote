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

package fr.gaellalire.vote.actor.citizen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

/**
 * @author Gael Lalire
 */
public class CitizenInstaller {

    private File config;

    public CitizenInstaller(final File config) {
        this.config = config;
    }

    public void install() throws Exception {
        File entryDestination = new File(config, "citizen.properties");
        OutputStream out = new FileOutputStream(entryDestination);
        IOUtils.copy(CitizenInstaller.class.getResourceAsStream("citizen.properties"), out);
        IOUtils.closeQuietly(out);
    }

}
