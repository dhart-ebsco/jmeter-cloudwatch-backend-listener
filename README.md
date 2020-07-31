
# Overview
### Description
This is a fork from the Elastic Search jmeter listener found here (https://github.com/delirius325/jmeter-elasticsearch-backend-listener).  Some of the wording below is directly from this repo.

The idea to make a CloudWatch version came from this article found here (https://www.concurrencylabs.com/blog/publish-jmeter-test-results-to-cloudwatch-logs/)

Thank you authors.

JMeter CloudWatch Backend Listener is a JMeter plugin enabling you to send test results directly to a CloudWatch Log Group. CloudWatch metrics can be setup based on these log entries and InSights can be used to query the log entries.  CloudWatch Dashboards can be used to visualize these logs.  These logs can be forwarded on to other services for further analysis and visualization. 

### Features

* CloudWatch 
  * Uses the AWS CloudWatch SDK to push logs
* IAM Role or IAM credential authentication
  * If running JMeter in AWS, the SDK will automatically use the instance/node IAM Role.  If running on-prem, set the AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables or set in .aws/credentials file with credentials and the SDK will automatically use those credentials. 
* Bulk requests
  * The LogEvents are sent in bulk up to 100 at a time. 
* Filters
  * Only send the samples you want by using Filters! Simply type them as follows in the field ``cw.sample.filter`` : ``filter1;filter2;filter3`` or ``sampleLabel_must_contain_this``.
  * You can also choose to exclude certain samplers; `!!exclude_this;filter1;filter2`
* Specific fields ```field1;field2;field3`
  * Specify fields that you want to send to CloudWatch (possible fields below)
     * AllThreads
     * BodySize
     * Bytes
     * SentBytes
     * ConnectTime
     * ContentType
     * DataType
     * ErrorCount
     * GrpThreads
     * IdleTime
     * Latency
     * ResponseTime
     * SampleCount
     * SampleLabel
     * ThreadName
     * URL
     * ResponseCode
     * TestStartTime
     * SampleStartTime
     * SampleEndTime
     * Timestamp
     * InjectorHostname
* Verbose, semi-verbose, error only, and quiet mode
  * __debug__ : Send request/response information of all samplers (headers, body, etc.)
  * __info__ : Sends all samplers to the CloudWatch engine, but only sends the headers, body info for the failed samplers.
  * __quiet__ : Only sends the response time, bytes, and other metrics
  * __error__ : Only sends the failing samplers to the CloudWatch engine (Along with their headers and body information).
* Use CloudWatch dashboards to visualize your results!


### Maven
```xml
<dependency>
  <groupId>io.github.dhart-ebsco</groupId>
  <artifactId>jmeter.backendlistener.cloudwatch</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Contributing
Feel free to contribute by branching and making pull requests, or simply by suggesting ideas through the "Issues" tab.

### Packaging and testing your newly added code
Execute below mvn command. Make sure JAVA_HOME is set properly
```
mvn package
```
Move the resulting JAR to your `JMETER_HOME/lib/ext`.

### For more info
For more information, here's a little [documentation](https://github.com/dhartebsco/jmeter-CloudWatch-backend-listener/wiki).
