Hadoop MapReduce PageRank Demo
Example code for CS6240

Author
-----------
- Joe Sackett (2018)
- Updated by Nikos Tziavelis (2023)
- Updated by Mirek Riedewald (2024)
- Updated by Jayantha Nanduri (2024)

Installation
------------
These components need to be installed first:
- OpenJDK 11
- Hadoop 3.3.5
- Maven (Tested with version 3.6.3)
- AWS CLI (Tested with version 1.22.34)

After downloading the hadoop installation, move it to an appropriate directory:

`mv hadoop-3.3.5 /usr/local/hadoop-3.3.5`

Environment
-----------
1) Example ~/.bash_aliases:
	```
	export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
	export HADOOP_HOME=/usr/local/hadoop-3.3.5
	export YARN_CONF_DIR=$HADOOP_HOME/etc/hadoop
	export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin
	```

2) Explicitly set `JAVA_HOME` in `$HADOOP_HOME/etc/hadoop/hadoop-env.sh`:

	`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home`

Execution
---------
All of the build & execution commands are organized in the Makefile.
1) Unzip project file.
2) Open command prompt.
3) Navigate to directory where project files unzipped.
4) Edit the Makefile to customize the environment at the top.
	Sufficient for standalone: hadoop.root, jar.name, local.input
	Other defaults acceptable for running standalone.
5) Standalone Hadoop:
	- `make switch-standalone`		-- set standalone Hadoop environment (execute once)
	- `make local` -- Run the program locally
6) Pseudo-Distributed Hadoop: (https://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SingleCluster.html#Pseudo-Distributed_Operation)
	- `make switch-pseudo`			-- set pseudo-clustered Hadoop environment (execute once)
	- `make pseudo`					-- first execution
	- `make pseudoq`				-- later executions since namenode and datanode already running 
7) AWS EMR Hadoop: (you must configure the emr.* config parameters at top of Makefile)
	- `aws configure --profile enter-username`
	- This will create .aws file in your home. Deleted configure file and update the information in credential file by copying and pasting the key generated when an aws lab instance is started.
	- Need to set region while creating the bucket, this should be updated in the makefile
    - command in makefile for make-bucket
	- `aws s3api create-bucket --bucket $(aws.bucket.name) --region $(aws.region)`
    - `make make-bucket`			-- only before first execution, used to create a s3 bucket with the bucket name mentioned in makefile
	- `make upload-input-aws`		-- only before first execution, uploads the input file to the bucket
	- `aws emr create-cluster \
	  --name "WordCount Spark Cluster" \
	  --release-label ${aws.emr.release} \
	  --instance-groups '[{"InstanceCount":${aws.num.nodes},"InstanceGroupType":"CORE","InstanceType":"${aws.instance.type}"},{"InstanceCount":1,"InstanceGroupType":"MASTER","InstanceType":"${aws.instance.type}"}]' \
	  --applications Name=Hadoop Name=Spark \
	  --steps Type=CUSTOM_JAR,Name="${app.name}",Jar="command-runner.jar",ActionOnFailure=TERMINATE_CLUSTER,Args=["spark-submit","--deploy-mode","cluster","--class","${job.name}","s3://${aws.bucket.name}/${jar.name}","s3://${aws.bucket.name}/${aws.input}","s3://${aws.bucket.name}/${aws.output}"] \
	  --log-uri s3://${aws.bucket.name}/${aws.log.dir} \
	  --configurations '[{"Classification": "hadoop-env", "Configurations": [{"Classification": "export","Configurations": [],"Properties": {"JAVA_HOME": "/usr/lib/jvm/java-11-amazon-corretto.x86_64"}}],"Properties": {}}, {"Classification": "spark-env", "Configurations": [{"Classification": "export","Configurations": [],"Properties": {"JAVA_HOME": "/usr/lib/jvm/java-11-amazon-corretto.x86_64"}}],"Properties": {}}]' \
	  --use-default-roles \
	  --enable-debugging \
	  --auto-terminate \
	  --region us-east-1`
		- sets the region for emr cluster to run the word count, this command is added in makefile under aws
    - `make aws`					-- check for successful execution with web interface (aws.amazon.com)
	- `make download-output-aws`		-- after successful execution & termination
