package io.github.dhartebsco.jmeter.backendlistener.cloudwatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang.NullArgumentException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;

public class CloudWatchSender {
    private static final Logger logger = LoggerFactory.getLogger(CloudWatchSender.class);
    private final CloudWatchLogsClient logsClient;

    private List<InputLogEvent> eventList;
    private String lastSequenceToken;

    private final String cwLogGroup;
    private final String cwLogStream;
    private final String cwTestMode;

    /*
     * AWS Credentials must be set as environment variables by the launching system
     */
    public CloudWatchSender(CloudWatchLogsClient client, String logGroup, String testMode) throws Exception {
       
        if (logGroup == null || logGroup.isEmpty())
            throw new NullArgumentException("log group is null or empty.");

        if (testMode == null || testMode.isEmpty())
            throw new NullArgumentException("test mode is null or empty.");     
            
        if (client == null)
            throw new NullArgumentException("CloudWatch client is null.");    

        cwTestMode = testMode;
        cwLogGroup = logGroup;
        logsClient = client;

        eventList = new ArrayList<InputLogEvent>();
        lastSequenceToken = null;

        // create a new log stream for each test run.
        // builds/tests can be running concurrently and reusing logstreams can cause conflicts. 
        cwLogStream = UUID.randomUUID().toString();
        WriteDebugToConsole("New Log Stream = " + cwLogStream);

        CreateLogStreamRequest createLogStream = CreateLogStreamRequest.builder()
            .logGroupName(this.cwLogGroup)
            .logStreamName(this.cwLogStream)
            .build();
        
        CreateLogStreamResponse response = logsClient.createLogStream(createLogStream);
        if (response.sdkHttpResponse().statusCode() != 200) {
            String errorMessage = String.format("AWS CloudWatch failed to create LogStream for LogGroup {}. Response status: {}.  Verify IAM credentials have permission to CreateLogStream.",
            this.cwLogGroup, response.sdkHttpResponse().statusText());
            WriteDebugToConsole(errorMessage);
            logger.error(errorMessage);
            throw new Exception(errorMessage);
        }
        
    }

    /**
     * This method returns the current size of the CloudWatch documents list
     * 
     * @return integer representing the size of the CloudWatch documents list
     */
    public int getListSize() {
        return this.eventList.size();
    }

    /**
     * This method clears the CloudWatch documents list
     */
    public void clearList() {
        this.eventList.clear();
    }

    /**
     * This method adds a CloudWatchLogEvent to the list (CloudWatchLogEventList).
     */
    public void addToList(final String messageJSON) {
        
        this.eventList
                .add(InputLogEvent.builder().message(messageJSON).timestamp(Instant.now().toEpochMilli()).build());
        
    }

    private void WriteDebugToConsole(String message)
    {
        if (this.cwTestMode != null && this.cwTestMode.equals("debug"))
                System.out.println(message);  
    }

    /**
     * This method sends the CloudWatch documents for each document present in the
     * list (eventList). All is being sent through the CloudWatch Client.
     */
    public void sendRequest() {

        try {

            WriteDebugToConsole("Enter Sending Request");

            if (lastSequenceToken == null || lastSequenceToken.isEmpty())
                lastSequenceToken = getNextSequenceToken();

            WriteDebugToConsole("Received sequence token = " + lastSequenceToken);

            PutLogEventsRequest putLogEventsRequest = PutLogEventsRequest.builder()
            .logEvents(this.eventList)
            .logGroupName(this.cwLogGroup)
            .logStreamName(this.cwLogStream)
            // Sequence token is required so that the log can be written to the
            // latest location in the stream.
            .sequenceToken(lastSequenceToken)
            .build();

            PutLogEventsResponse response =  logsClient.putLogEvents(putLogEventsRequest);

            lastSequenceToken = response.nextSequenceToken();

            if (response.sdkHttpResponse().statusCode() != 200) {
                String errorMessage = String.format("AWS CloudWatch failed to write results for LogGroup %s. Response status: %s",
                this.cwLogGroup, response.sdkHttpResponse().statusText());
                logger.error(errorMessage);
                throw new Exception(errorMessage);
            } else {
                String message = String.format("AWS CloudWatch has successfully written to LogGroup %s", this.cwLogGroup);
                WriteDebugToConsole(message);
                logger.debug(message);
            }
        } catch (final Exception e) {
            String message = String.format("Log Group: %s\r\nException: %s\r\nStackTrace: %s",this.cwLogGroup, e.getMessage(),e.getStackTrace());
            WriteDebugToConsole(message);
            if (logger.isErrorEnabled()) {
                logger.error(message);                
                logger.error("AWS CloudWatch was unable to perform request to the CloudWatch. Check your JMeter console for more info.");
            }
        }
    }

    private String getNextSequenceToken() {
        final DescribeLogStreamsRequest logStreamRequest = DescribeLogStreamsRequest.builder()
                .logGroupName(this.cwLogGroup).logStreamNamePrefix(this.cwLogStream).build();

        final DescribeLogStreamsResponse describeLogStreamsResponse = logsClient.describeLogStreams(logStreamRequest);

        // Assume that a single stream is returned since a specific stream name was
        // specified in the previous request.
        return describeLogStreamsResponse.logStreams().get(0).uploadSequenceToken();
    }

}
