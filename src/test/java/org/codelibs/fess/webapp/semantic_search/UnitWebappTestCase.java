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
package org.codelibs.fess.webapp.semantic_search;

import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastadi.LastaDiTestCase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.TestInfo;

public abstract class UnitWebappTestCase extends LastaDiTestCase {
    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    // ===== Assert methods for JUnit 5 compatibility =====

    protected void fail(String message) {
        Assertions.fail(message);
    }

    protected void assertTrue(String message, boolean condition) {
        Assertions.assertTrue(condition, message);
    }

    protected void assertFalse(String message, boolean condition) {
        Assertions.assertFalse(condition, message);
    }

    protected void assertEquals(String message, Object expected, Object actual) {
        Assertions.assertEquals(expected, actual, message);
    }

    protected void assertNotNull(String message, Object object) {
        Assertions.assertNotNull(object, message);
    }

    protected void assertNull(String message, Object object) {
        Assertions.assertNull(object, message);
    }

    protected void assertSame(Object expected, Object actual) {
        Assertions.assertSame(expected, actual);
    }

    protected void assertSame(String message, Object expected, Object actual) {
        Assertions.assertSame(expected, actual, message);
    }
}
