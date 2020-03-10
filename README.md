## Amazon Kinesis Analytics Taxi Consumer

Sample Apache Flink application that can be deployed to Kinesis Analytics for Java. It reads taxi events from a Kinesis data stream, processes and aggregates them, and ingests the result to an Amazon Elasticsearch Service cluster for visualization with Kibana.

To see the sample application in action, simply execute the following CloudFormation template in your own AWS account. The template first builds the Flink application that is analyzing the incoming taxi trips, including the Flink Kinesis Connector that is required to read data from a Kinesis data stream, and then creates the infrastructure and submits the Flink application to KDA for Java.

[![Launch CloudFormation Stack](https://s3.amazonaws.com/cloudformation-examples/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home#/stacks/new?stackName=kinesis-analytics-taxi-consumer&templateURL=https://s3.amazonaws.com/aws-bigdata-blog/artifacts/kinesis-analytics-taxi-consumer/cfn-templates/kinesis-analytics-taxi-consumer.yml)

The entire process of building the application and creating the infrastructure takes about 15 minutes. Once the creation of the CloudFormation stack completes, the Flink application has been deployed to KDA for Java as a KDA for Java application and waits for events in the data stream to arrive. Checkpointing has been enabled so that the application can seamlessly recover from failures of the underlying infrastructure while KDA for Java will manage the checkpoints on your behalf. In addition, autoscaling has been configured so that KDA for Java automatically allocates or removes resources and scales the application, ie, adapts its parallelism, in response to changes of the incoming traffic.

To populate the Kinesis data stream, we use a Java application that replays a public data set of historic taxi trips made in New York City into the data stream. The Java application has already been downloaded to an EC2 instance that has been provisioned by CloudFormation, you just need to connect to the Instance and execute the jar file to start ingesting events into the stream.

Note that all of the following commands, including their correct parameters, can be obtained from the output section of the CloudFormation template that has been executed previously.

```
$ ssh ec2-user@«Replay instance DNS name»

$ java -jar amazon-kinesis-replay-*.jar -streamName «Kinesis data stream name» -streamRegion «AWS region» -speedup 3600
```

The speedup parameter determines how much faster the data is ingested into the Kinesis data stream relative to the actual occurrence of the historic events. With the given parameters the Java application ingests an hour of historic data within one second, which results in a throughput of roughly 13k events and 6 MB of data per second and hence completely saturates the Kinesis data stream—more on this later.

You can then go ahead and inspect the derived data through the Kibana dashboard that has been created, or you can create your own visualizations to explore the data in Kibana.

```
https://«Elasticsearch endpoint»/_plugin/kibana/app/kibana#/dashboard/nyc-tlc-dashboard
```

The prepared Kibana dashboard contains a heatmap and a line graph. The heatmap visualizes locations where taxis are currently requested and it shows that the highest demand for taxis is Manhattan. Moreover, the airports JFK and LaGuardia are also spots on the map where substantially more rides are requested compared to their direct neighborhoods. The line graph visualizes the average trip duration to these two airports and you can see how it is steadily increasing throughout the day until it abruptly drops in the evening.

![Kibana Dashboard Screen Shot](misc/kibana-dashboard-screenshot.png?raw=true)

Note that the Elasticsearch cluster is configured to accept connections from the IP address range specified as a parameter of the CloudFormation template. Please note that for production workloads it’s much more desirable to further tighten the security of your Elasticsearch domain, for instance, by using Amazon Cognito for Kibana access control.

## License

This library is licensed under the Apache 2.0 License.
