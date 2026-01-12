# bank-agent-jade

To set up JADE in the maven project, open where jade.jar is located and run the below command: (this is for windows)
$ mvn install:install-file ^
-Dfile=jade.jar ^
-DgroupId=com.tilab.jade ^
-DartifactId=jade ^
-Dversion=4.5.0 ^
-Dpackaging=jar

To build the maven project run:
$ mvn clean package
