/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.core.service.database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class DatabaseSpecificSQLGeneratorTest {

    private final DatabaseTypeResolver databaseTypeResolver = Mockito.mock(DatabaseTypeResolver.class);
    private final RoutingDataSource dataSource = Mockito.mock(RoutingDataSource.class);
    private final DatabaseSpecificSQLGenerator databaseSpecificSQLGenerator = new DatabaseSpecificSQLGenerator(databaseTypeResolver,
            dataSource);

    @Test
    public void testCountQueryResultOnEmptyString() {
        String sql = "";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM () AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithoutLimitOrOffset() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (" + sql + ") AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithLimit() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2 LIMIT 2";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithOffset() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2 OFFSET 2";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithLimitAndOffset() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2 OFFSET 2 LIMIT 50";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithLowercaseLimit() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2 limit 10";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithLowercaseOffset() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2 offset 5";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithMixedCaseLimitAndOffset() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2 LiMiT 10 OfFsEt 5";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithExtraWhitespace() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2  LIMIT  10  OFFSET  5";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithNewlineBeforeLimit() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2\nLIMIT 10";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithTabsAndNewlines() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2\n\tLIMIT\t10\n\tOFFSET\t5";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithMultipleSpaces() {
        String sql = "SELECT 1 FROM test_table WHERE asd=2     LIMIT     100     OFFSET     50";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table WHERE asd=2) AS temp", countQuery);
    }

    @Test
    public void testCountQueryResultOnSqlWithNoSpaceBeforeLimit() {
        // Edge case: LIMIT immediately after a word (though invalid SQL, we should handle it)
        String sql = "SELECT 1 FROM test_table LIMIT 10";
        String countQuery = databaseSpecificSQLGenerator.countQueryResult(sql);
        Assertions.assertEquals("SELECT COUNT(*) FROM (SELECT 1 FROM test_table) AS temp", countQuery);
    }
}
