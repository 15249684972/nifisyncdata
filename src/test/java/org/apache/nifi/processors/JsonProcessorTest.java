package org.apache.nifi.processors;

import org.apache.commons.io.IOUtils;
import org.apache.nifi.annotation.behavior.TriggerWhenEmpty;
import org.apache.nifi.httpProcesssors.JsonProcessor;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Created by coco1 on 2017/7/20.
 */
@TriggerWhenEmpty
public class JsonProcessorTest {
    @org.junit.Test
    public void testOnTrigger() throws IOException, InterruptedException{
        // Content to be mock a json file
        InputStream content = new ByteArrayInputStream("{\"hello\":\"nifi rocks\"}".getBytes());
        
        // Generate a test runner to mock a processor in a flow
        TestRunner runner = TestRunners.newTestRunner(new JsonProcessor());
        // Add properites
        runner.setProperty(JsonProcessor.JSON_PATH, "$.hello");
        // Add the content to the runner
        runner.enqueue(content);

        // Run the enqueued content, it also takes an int = number of contents queued
        	 runner.run(1);
        	 
       new Thread(new Runnable() {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			
		}
	}).start();
        // All results were processed with out failure
        runner.assertQueueEmpty();

        // If you need to read or do aditional tests on results you can access the content
        List<MockFlowFile> results = runner.getFlowFilesForRelationship(JsonProcessor.SUCCESS);
        assertTrue("1 match", results.size() == 1);
        MockFlowFile result = results.get(0);
        String resultValue = new String(runner.getContentAsByteArray(result));
        System.out.println("Match: " + IOUtils.toString(runner.getContentAsByteArray(result)));

        // Test attributes and content
        result.assertAttributeEquals(JsonProcessor.MATCH_ATTR, "nifi rocks");
        result.assertContentEquals("nifi rocks");
    }
}
