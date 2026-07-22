/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.unit;

import java.io.File;
import java.io.IOException;

import org.codelibs.core.misc.DynamicProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal in-memory system-properties store for plugin unit tests, backed by a
 * temp-file-backed {@link DynamicProperties} instance. Registering this as the
 * "systemProperties" component (see {@code test_app.xml}) makes
 * {@code org.codelibs.fess.util.ComponentUtil#getSystemProperties()} resolvable in
 * tests, so that {@code FessProp#getSystemProperty(String, String)} /
 * {@code #setSystemProperty(String, String)} exercise real config-read/write logic
 * instead of throwing {@code ComponentNotFoundException}.
 *
 * Mirrors {@code org.codelibs.fess.unit.TestSystemProperties} from the fess core
 * test tree (same package/class name, same pattern), scoped to this plugin's own
 * test container since core's test-only class is not shipped in the core jar.
 */
public class TestSystemProperties extends DynamicProperties {

    private static final Logger logger = LoggerFactory.getLogger(TestSystemProperties.class);

    public TestSystemProperties() {
        super(createTempFile());
    }

    private static File createTempFile() {
        try {
            final File tempFile = File.createTempFile("test-system", ".properties");
            tempFile.deleteOnExit();
            return tempFile;
        } catch (final IOException e) {
            logger.warn("Failed to create temp file for TestSystemProperties", e);
            return null;
        }
    }
}
