package io.github.dhartebsco.jmeter.backendlistener.cloudwatch;

import static org.junit.Assert.assertNotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;

import org.apache.jmeter.samplers.SampleResult;
import org.junit.Before;
import org.junit.Test;

public class TestCloudWatchBackend {
    private CloudWatchLogEvent logEventNoCI;

    private CloudWatchLogEvent logEventCI;

    @Before
    public void setUp() throws Exception {
        logEventCI = new CloudWatchLogEvent(new SampleResult(), "info", "yyyy-MM-dd'T'HH:mm:ss.SSSZZ", 1, false,
                new HashSet<String>());
        logEventNoCI = new CloudWatchLogEvent(new SampleResult(), "info", "yyyy-MM-dd'T'HH:mm:ss.SSSZZ", 0, false, new HashSet<String>());
    }

    @Test
    public void testGetElapsedTimeNoCI() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date testDate = this.logEventNoCI.getElapsedTime(false);
        assertNotNull("testDate = " + sdf.format(testDate), sdf.format(testDate));
    }

    @Test
    public void testGetElapsedTimeCI() throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date testDate = this.logEventCI.getElapsedTime(true);
        assertNotNull("testDate = " + sdf.format(testDate), sdf.format(testDate));
    }
}
