/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.elasticjob.lite.console.dao.search;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.shardingsphere.elasticjob.lite.console.dao.search.RDBJobEventSearch.Result;
import org.apache.shardingsphere.elasticjob.lite.tracing.event.JobExecutionEvent;
import org.apache.shardingsphere.elasticjob.lite.tracing.event.JobStatusTraceEvent;
import org.apache.shardingsphere.elasticjob.lite.tracing.event.JobStatusTraceEvent.Source;
import org.apache.shardingsphere.elasticjob.lite.tracing.event.JobStatusTraceEvent.State;
import org.apache.shardingsphere.elasticjob.lite.tracing.rdb.storage.RDBJobEventStorage;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public final class RDBJobEventSearchTest {
    
    private static RDBJobEventStorage storage;
    
    private static RDBJobEventSearch repository;
    
    @BeforeClass
    public static void setUpClass() throws SQLException {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName(org.h2.Driver.class.getName());
        dataSource.setUrl("jdbc:h2:mem:");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        storage = new RDBJobEventStorage(dataSource);
        repository = new RDBJobEventSearch(dataSource);
        initStorage();
    }
    
    private static void initStorage() {
        for (int i = 1; i <= 500; i++) {
            JobExecutionEvent startEvent = new JobExecutionEvent("localhost", "127.0.0.1", "fake_task_id", "test_job_" + i, JobExecutionEvent.ExecutionSource.NORMAL_TRIGGER, 0);
            storage.addJobExecutionEvent(startEvent);
            if (i % 2 == 0) {
                JobExecutionEvent successEvent = startEvent.executionSuccess();
                storage.addJobExecutionEvent(successEvent);
            }
            storage.addJobStatusTraceEvent(new JobStatusTraceEvent(
                    "test_job_" + i, "fake_failed_failover_task_id", "fake_slave_id", Source.LITE_EXECUTOR, "FAILOVER", "0", State.TASK_FAILED, "message is empty."));
        }
    }
    
    @Test
    public void assertFindJobExecutionEventsWithPageSizeAndNumber() {
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(50, 1, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(50));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(100, 5, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(100));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(100, 6, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(0));
    }
    
    @Test
    public void assertFindJobExecutionEventsWithErrorPageSizeAndNumber() {
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(-1, -1, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobExecutionEventsWithSort() {
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, "jobName", "ASC", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        assertThat(result.getRows().get(0).getJobName(), is("test_job_1"));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, "jobName", "DESC", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        assertThat(result.getRows().get(0).getJobName(), is("test_job_99"));
    }
    
    @Test
    public void assertFindJobExecutionEventsWithErrorSort() {
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, "jobName", "ERROR_SORT", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        assertThat(result.getRows().get(0).getJobName(), is("test_job_1"));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, "notExistField", "ASC", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobExecutionEventsWithTime() {
        Date now = new Date();
        Date tenMinutesBefore = new Date(now.getTime() - 10 * 60 * 1000);
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, tenMinutesBefore, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, now, null, null));
        assertThat(result.getTotal(), is(0));
        assertThat(result.getRows().size(), is(0));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, tenMinutesBefore, null));
        assertThat(result.getTotal(), is(0));
        assertThat(result.getRows().size(), is(0));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, now, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, tenMinutesBefore, now, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobExecutionEventsWithFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("isSuccess", "1");
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, fields));
        assertThat(result.getTotal(), is(250));
        assertThat(result.getRows().size(), is(10));
        fields.put("isSuccess", null);
        fields.put("jobName", "test_job_1");
        result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, fields));
        assertThat(result.getTotal(), is(1));
        assertThat(result.getRows().size(), is(1));
    }
    
    @Test
    public void assertFindJobExecutionEventsWithErrorFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("notExistField", "some value");
        Result<JobExecutionEvent> result = repository.findJobExecutionEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, fields));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithPageSizeAndNumber() {
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(50, 1, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(50));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(100, 5, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(100));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(100, 6, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(0));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithErrorPageSizeAndNumber() {
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(-1, -1, null, null, null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithSort() {
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, "jobName", "ASC", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        assertThat(result.getRows().get(0).getJobName(), is("test_job_1"));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, "jobName", "DESC", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        assertThat(result.getRows().get(0).getJobName(), is("test_job_99"));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithErrorSort() {
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, "jobName", "ERROR_SORT", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        assertThat(result.getRows().get(0).getJobName(), is("test_job_1"));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, "notExistField", "ASC", null, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithTime() {
        Date now = new Date();
        Date tenMinutesBefore = new Date(now.getTime() - 10 * 60 * 1000);
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, tenMinutesBefore, null, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, now, null, null));
        assertThat(result.getTotal(), is(0));
        assertThat(result.getRows().size(), is(0));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, tenMinutesBefore, null));
        assertThat(result.getTotal(), is(0));
        assertThat(result.getRows().size(), is(0));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, now, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
        result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, tenMinutesBefore, now, null));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("jobName", "test_job_1");
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, fields));
        assertThat(result.getTotal(), is(1));
        assertThat(result.getRows().size(), is(1));
    }
    
    @Test
    public void assertFindJobStatusTraceEventsWithErrorFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("notExistField", "some value");
        Result<JobStatusTraceEvent> result = repository.findJobStatusTraceEvents(new RDBJobEventSearch.Condition(10, 1, null, null, null, null, fields));
        assertThat(result.getTotal(), is(500));
        assertThat(result.getRows().size(), is(10));
    }
}
