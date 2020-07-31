    package io.github.dhartebsco.jmeter.backendlistener.cloudwatch;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.backend.AbstractBackendListenerClient;
import org.apache.jmeter.visualizers.backend.BackendListenerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;


import com.google.gson.Gson;

public class CloudWatchBackendClient extends AbstractBackendListenerClient {
    private static final String BUILD_NUMBER = "BuildNumber";
    private static final String CW_REGION = "Region";
    private static final String CW_LOGGROUP = "LogGroup";
    private static final String CW_FIELDS = "cw.fields";
    private static final String CW_TIMESTAMP = "cw.timestamp";
    private static final String CW_BULK_SIZE = "cw.bulk.size";
    private static final String CW_SAMPLE_FILTER = "cw.sample.filter";
    private static final String CW_TEST_MODE = "cw.test.mode";
    private static final String CW_PARSE_REQ_HEADERS = "cw.parse.all.req.headers";
    private static final Logger logger = LoggerFactory.getLogger(CloudWatchBackendClient.class);
    private static final Map<String, String> DEFAULT_ARGS = new LinkedHashMap<>();
    static {
     
        DEFAULT_ARGS.put(CW_TIMESTAMP, "yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
        DEFAULT_ARGS.put(CW_BULK_SIZE, "100");
        DEFAULT_ARGS.put(CW_SAMPLE_FILTER, null);
        DEFAULT_ARGS.put(CW_FIELDS, null);
        DEFAULT_ARGS.put(CW_TEST_MODE, "info");
        DEFAULT_ARGS.put(CW_PARSE_REQ_HEADERS, "false");

    }
    private CloudWatchSender sender;
    private Set<String> modes;
    private Set<String> filters;
    private Set<String> fields;
    private int buildNumber;
    

    private String region;
    private String logGroup;
    private int bulkSize;
    

    public CloudWatchBackendClient() {
        super();
    }

    @Override
    public Arguments getDefaultParameters() {
        Arguments arguments = new Arguments();
        DEFAULT_ARGS.forEach(arguments::addArgument);
        return arguments;
    }

    @Override
    public void setupTest(BackendListenerContext context) throws Exception {
        try {
            this.filters = new HashSet<>();
            this.fields = new HashSet<>();
            this.modes = new HashSet<>(Arrays.asList("info", "debug", "error", "quiet"));
            
            this.buildNumber = (JMeterUtils.getProperty(CloudWatchBackendClient.BUILD_NUMBER) != null
                    && !JMeterUtils.getProperty(CloudWatchBackendClient.BUILD_NUMBER).trim().equals(""))
                            ? Integer.parseInt(JMeterUtils.getProperty(CloudWatchBackendClient.BUILD_NUMBER)) : 0;
            
            this.region = (JMeterUtils.getProperty(CloudWatchBackendClient.CW_REGION) != null
            && !JMeterUtils.getProperty(CloudWatchBackendClient.CW_REGION).trim().equals(""))
                    ? JMeterUtils.getProperty(CloudWatchBackendClient.CW_REGION) : "";
 
            this.logGroup = (JMeterUtils.getProperty(CloudWatchBackendClient.CW_LOGGROUP) != null
            && !JMeterUtils.getProperty(CloudWatchBackendClient.CW_LOGGROUP).trim().equals(""))
                    ? JMeterUtils.getProperty(CloudWatchBackendClient.CW_LOGGROUP) : "";

                            
            convertParameterToSet(context, CW_SAMPLE_FILTER, this.filters);
            convertParameterToSet(context, CW_FIELDS, this.fields);
            checkTestMode(context.getParameter(CW_TEST_MODE));

            CloudWatchLogsClient cwClient = CloudWatchLogsClient.builder().region(Region.of(this.region)).build();

            this.sender = new CloudWatchSender(cwClient, this.logGroup, context.getParameter(CW_TEST_MODE));       
            
            super.setupTest(context);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create CloudWatch client.  Verify that AWS IAM credentials are set in .aws/credentials or set in env vars.  Verify IAM user has permissions to CreateLogStream and to PutLogEvent", e);
        }
    }

    /**
     * Method that converts a semicolon separated list contained in a parameter into a string set
     * @param context
     * @param parameter
     * @param set
     */
    private void convertParameterToSet(BackendListenerContext context, String parameter, Set<String> set) {
        String[] array = (context.getParameter(parameter).contains(";")) ? context.getParameter(parameter).split(";")
                : new String[] { context.getParameter(parameter) };
        if (array.length > 0 && !array[0].trim().equals("")) {
            for (String entry : array) {
                set.add(entry.toLowerCase().trim());
                if(logger.isDebugEnabled())
                    logger.debug("Parsed from " + parameter + ": " + entry.toLowerCase().trim());
            }
        }
    }



    @Override
    public void handleSampleResults(List<SampleResult> results, BackendListenerContext context) {
        for (SampleResult sr : results) {
            CloudWatchLogEvent metric = new CloudWatchLogEvent(sr, context.getParameter(CW_TEST_MODE),
                    context.getParameter(CW_TIMESTAMP), this.buildNumber,
                    context.getBooleanParameter(CW_PARSE_REQ_HEADERS, false),
                    fields);

            if (validateSample(context, sr)) {
                try {
                    this.sender.addToList(new Gson().toJson(metric.getLogEvent(context)));
                } catch (Exception e) {
                    logger.error(
                            "The CloudWatch Backend Listener was unable to add sampler to the list of samplers to send... More info in JMeter's console.");
                    e.printStackTrace();
                }
            }
        }

        if (this.sender.getListSize() >= this.bulkSize) {
            try {
                this.sender.sendRequest();
            } catch (Exception e) {
                logger.error("Error occured while sending bulk request.", e);
            } finally {
                this.sender.clearList();
            }
        }
    }

    @Override
    public void teardownTest(BackendListenerContext context) throws Exception {
        if (this.sender.getListSize() > 0) {
            this.sender.sendRequest();
        }
        
        super.teardownTest(context);
    }

    /**
     * This method checks if the test mode is valid
     * 
     * @param mode
     *            The test mode as String
     */
    private void checkTestMode(String mode) {
        if (!this.modes.contains(mode)) {
            logger.warn(
                    "The parameter \"cw.test.mode\" isn't set properly. Three modes are allowed: debug ,info, and quiet.");
            logger.warn(
                    " -- \"debug\": sends request and response details to CloudWatch. Info only sends the details if the response has an error.");
            logger.warn(" -- \"info\": should be used in production");
            logger.warn(" -- \"error\": should be used if you want to see only errors.");
            logger.warn(" -- \"quiet\": should be used if you don't care to have the details.");
        }
    }

    /**
     * This method will validate the current sample to see if it is part of the filters or not.
     * 
     * @param context
     *            The Backend Listener's context
     * @param sr
     *            The current SampleResult
     * @return true or false depending on whether or not the sample is valid
     */
    private boolean validateSample(BackendListenerContext context, SampleResult sr) {
        boolean valid = true;
        String sampleLabel = sr.getSampleLabel().toLowerCase().trim();

        if (this.filters.size() > 0) {
            for (String filter : filters) {
                Pattern pattern = Pattern.compile(filter);
                Matcher matcher = pattern.matcher(sampleLabel);

                if (!sampleLabel.startsWith("!!") && (sampleLabel.contains(filter) || matcher.find())) {
                    valid = true;
                    break;
                } else {
                    valid = false;
                }
            }
        }

        // if sample is successful but test mode is "error" only
        if (sr.isSuccessful() && context.getParameter(CW_TEST_MODE).trim().equalsIgnoreCase("error") && valid) {
            valid = false;
        }

        return valid;
    }
}
