
# Building real-time stream processing pipelines on AWS

```
# Credits
Lab Created By - Steffen Hausmann
Markdown By - Unni Pillai
```

In this lab, you will learn how to deploy, operate, and scale an [Apache Flink][1] application with [Kinesis Data Analytics for Java Applications][2].

We use a scenario to analyze the telemetry data of a taxi fleet in New York City in near-real time to optimize the fleet operation. In this scenario, every taxi in the fleet is capturing information about completed trips.

The tracked information includes the pickup and drop-off locations, number of passengers, and generated revenue. This information is ingested into a Kinesis data stream as a simple JSON blob.

From there, the data is processed by a Flink application, which is deployed to Kinesis Data Analytics for Java Applications. This application identifies areas that are currently requesting a high number of taxi rides. The derived insights are finally persisted into [Amazon Elasticsearch Service][3], where they can be accessed and visualized using [Kibana][4].


![](images/01.png?raw=true)


# Create Kinesis Data Stream

Lets create an Amazon Kinesis Data Stream, later in the next step we will populate the stream using a historic data set of taxi trips made in NYC.

* Navigate to the Amazon Kinesis services and press **Get Started** when prompted (you may not need to complete this, if you have already used Amazon Kinesis). Select **Create data stream** to navigate to the Amazon Kinesis Data Stream service.

![](images/02.png?raw=true)



* When prompted, enter `taxi-trip-events` as **Kinesis stream name**. Enter **8** as the **Number of shards** and select **Create Kinesis stream** at the bottom of the page

![](images/03.png?raw=true)

# Setup baseline infra
Now that the Kinesis data stream has been created, we want to ingest historic taxi trip events into the data stream. To this end, we compile the kinesis replay Java application and load it onto an EC2 instance.

We start with creating an SSH key pair so that we can connect to the instance over SSH. You can skip to the next section if you have created an SSH key pair previously.

* Navigate to the EC2 service and choose Key Pairs in the navigation bar on the left.

![](images/04.png?raw=true)

* Click **Create Key Pair** and enter a name for the SSH key pair in the resulting dialog box, eg,  `initals-key-pair` and select **Create**.

![](images/05.png?raw=true)

* Confirm the download of the generated .pem file to your local machine.

![](images/06.png?raw=true)

Now that you have successfully created an SSH key pair, you can create the EC2 instance that you will use to ingest taxi trip events into the previously created Kinesis data stream. We will subsequently use a CloudFormation template, that provisions some of the resources required to complete the lab and compiles the Kinesis Replay Java application and the Flink Application.

* Follow [this link][11] to execute CloudFormation template that uses CodePipeline and CodeBuild to compile the Kinesis Replay Java application and to provision a EC2 instance. Select **Next** on the resulting dialog. 

![](images/07.png?raw=true)

* On the next page of the dialog, suggest a stack name, eg, `streaming-workshop-infrastructure`.
* Specify an appropriate CIDR range to that is able to connect to the EC2 instance over SSH as the **ClientIpAddressRange** parameter. For this workshop you can choose to use a open to world `0.0.0.0/0`
* Moreover, select the previously created SSH key pair from the **SshKeyName** dropdown menu.

![][´(images/08.png?raw=true)

* On the next dialog for **Step 3**, leave all parameters set to their default and select **Next**.

* On the last page of the dialog, confirm that CloudFormation may create IAM resource and create nested CloudFormation stacks by selecting the checkbox **I acknowledge that AWS CloudFormation might create IAM resources **and** I acknowledge that AWS CloudFormation might require the following capability: **CAPABILITY_AUTO_EXPAND**. Select **Create stack** at the bottom of the page. 

![](images/09.png?raw=true)

* Wait until the CloudFormation template has been successfully been created. This may take around 10 minutes. You man need to refresh the page to see the status change to **CREATE_COMPLETED**. 

![](images/10.png?raw=true)

Navigate to the **Outputs** section of the CloudFormation template and take a note of the outputs. We will need them to complete the subsequent steps. 

![](images/11.png?raw=true)

The CloudFormation template has created and configured an EC2 instance so that we can now start to ingest taxi trip events into the Kinesis data stream.

# Ingest data into a Kinesis Data Stream

* Connect to the EC2 instance via SSH. You can obtain the command including the correct parameters from the **Outputs** section of the CloudFromation template under **KinesisReplayInstance**.

```
ssh -i keyname.pem ec2-user@«Replay instance DNS name»
```

* Once the connection has been established, start ingesting events into the Kinesis data stream by executing the jar file that has already been downloaded to the Ec2 instance.

```
java -jar amazon-kinesis-replay-1.0-SNAPSHOT.jar -streamRegion «AWS region» -speedup 3600
```

> The command with prepopulated parameters is again available from the **Outputs** section of the CloudFromation template under **ProducerCommand**. This time you need to add the parameter streamName with the name of the stream you've created earlier, eg, `taxi-trip-events`.

* You have now successfully created the basic infrastructure and are ingesting data into the Kinesis data stream. In the next section, we will analyze and visualize the data in real time.


# Create a Kinesis Analytics for Java Application

To analyze the data in real time, we will now deploy an Apache Flink application to the Kinesis Data Analytics for Java Applications service. The Flink application has already been compiled and loaded into an S3 bucket by the CloudFormation template that has been executed in the previous step.

We start with creating an IAM role that can be used by the Kinesis Analytics service to obtain permissions and call services in our account, eg, to read event from the Kinesis stream you have just created.

* Navigate to IAM and select Roles in the navigation pane and press the blue **Create role** button.

![](images/12.png?raw=true)

* On the resulting page select **Kinesis** and then **Kinesis Analytics** at the bottom of the page before you press the blue **Next: Permission** button.

![](images/13.png?raw=true)

* Tick the box next to **AdministratorAccess** and press **Next: Tags**.

* For the purpose of this workshop, we'll use a policiy that has full permissions over the account. Note that for production workloads, you need to choose more fine grained policies. 

![](images/14.png?raw=true)

* On the following page, press **Next: Review**.
* On the review page, enter `streaming-workshop-role` as **Role name** and press the blue **Create role** button. 

![](images/15.png?raw=true)

Now that the role has been create, we can create the Kinesis Analytics for Java application. A Kinesis Analytics for Java application basically consists of a reference to the Flink application in S3 and some additional configuration data. Once the Kinesis Analytics for Java application has been created, it can be deployed and executed by the services in a fully managed environment.


* Navigate to the Kinesis services in the management console and press Create analytics application. 

![](images/16.png?raw=true)

* Enter `streaming-workshop-java-app` as the **Application name** and select **Apache Flink 1.6** as the **Runtime**. 

* **Choose from IAM roles that Kinesis Analytics can assume** as **Access permissions**, select the role that has been created previously, and press the blue **Create Application** button.

![](images/17.png?raw=true)

* On the resulting page press the blue **Configure** button to configure the Kinesis Analytics application.

* Enter the bucket and prefix of the compiled jar file under **Amazon S3 bucket** and **Path to Amazon S3 object**. You can obtain the correct values from the Output section of the CloudFormation template under **AmazonS3Bucket** and **PathToAmazonS3Object**.

![](images/18.png?raw=true)

* Expand the **Properties** section.
* Enter **Group ID** : `FlinkApplicationProperties` 
* add two key/value pairs:
1. `InputStreamName` with the name of the Kinesis stream you've created earlier, eg, `taxi-trip-events`
2. `ElasticsearchEndpoint` with the correct Elasticsearch https endpoint that can be obtained from the Output section of the CloudFormation template under **ElasticsearchEndpoint**.

![](images/19.png?raw=true)

* Finally, press the blue **Update** button at the bottom of the page to update the properties of the application.

* Once the update has completed, press **Run** on the resulting page and confirm that you want to run the application by choosing **Run** again.


The application will now start in the background, which can take a couple of minutes. Once it is running, you can inspect the operator graph of the Flink application. The application is continuously processing the data that is ingested into the Kinesis stream and sends derived insights to Elasticsearch for visualization.

# Visualizing Data

* Navigate to the Kibana dashboard, the URL can be obtained from the Output section of the CloudFormation template under **KibanaDashboard**. You can inspect the preloaded dashboard or even create your own visualizations.

![](images/20.png?raw=true)

# Scaling the Kinesis Data Stream and the Kinesis Data Analytics for Java Application

For this lab, the Kinesis data stream was deliberately underprovisioned so that the Kinesis Replay Java application is completely saturating the data stream. When you closely inspect the output of the Java application, you'll notice that the "replay lag" is continuously increasing. This means that the producer cannot ingest events as quickly as it is required according to the specified speedup parameter.

In this section, you will scale the Kinesis data stream to accommodate the throughput that is generated by the Java application ingesting events into the data stream. We then observe how the Kinesis Data Analytics for Java application automatically scales as well to adapt to the increased throughput.

* Navigate to the Kinesis services in the Management Console and click on the name of the Kinesis stream you have created earlier, eg, `taxi-trip-events`.

* Click on **Edit** on the right-hand side of the section labeled **Shards**. 

![](images/21.png?raw=true)

* Double the througput of the Kinesis stream by changing the number of **Open shards** to `16`. Click on the blue **Save** button to confirm the changes.



* While the service doubles the number of shards and hence the throughput of the stream, examine the metrics of the Kinesis stream by clicking on **Monitoring** at the top of the screen. 

![](images/22.png?raw=true)


* After 2-3 minutes, you should notice the effect of the scaling operation as the throughput of the stream substantially increases. 

![](images/23.png?raw=true)



* However, as the throughput of the stream was increased, the Kinesis Data Analytics for Java application now begins to fall behind, as it does no longer has enough resurces to keep up with the processing of the arriving data. This is visible in the **IteratorAgeMilliseconds** metric of the Kinesis data stream.



* Roughly 15 minutes after the scaling operation completed, you can notice the effect of the scaling operation. The millisBehindLatest metric starts to decrease until it reaches zero, when the processing has caught up with the tip of the Kinesis data stream.





# Let's step back for a moment and review what you just did:
* You created a fully managed, highly available, scalable streaming architecture.
* You ingested and analyzed up to 25k events per second.
* You doubled the throughput of the architecture by scaling the Kinesis data stream and the Kinesis Data Analytics for Java application with a couple of clicks.
* You did all this while the architecture remained fully functional and kept receiving and processing events, not losing a single event.


> Try to imagine what it would have taken you to build something similar from scratch.

# Clean Up 
- Delete the cloudformation stack. If it fails, delete the S3 bucket and delete the stack again
- Delete the Kinesis Stream
- Delete the Kinesis Analytics Java App
- Delete the IAM Role you created earlier



The sources of the application and the AWS CloudFormation template are [available from GitHub][29] for your reference. You can dive into all the details of the Flink application and the configuration of the underlying services.


[1]: https://flink.apache.org/

[2]: https://aws.amazon.com/kinesis/data-analytics/

[3]: https://aws.amazon.com/elasticsearch-service/

[4]: https://aws.amazon.com/elasticsearch-service/the-elk-stack/kibana/

[11]: https://console.aws.amazon.com/cloudformation/home#/stacks/new?stackName=streaming-workshop-infrastructure&templateURL=https://shausma-public.s3-eu-west-1.amazonaws.com/public/cfn-templates/streaming-workshop/streaming-workshop-infrastructure.yml

[29]: https://github.com/aws-samples/amazon-kinesis-analytics-taxi-consumer
