# Bank to the Future - Spark

## Steps

1. Complete the main() method of the App class.  The goal is to calculate all account balances. Because the Spark job runs as a batch job you should stop the Kafka producer so that the balances are not changing as the job runs, leave the Mini Accumulo Cluster running as this is where Spark retrieves the data from.  Also ensure your Mini Accumulo Cluster has a transaction history beginning from tx1 otherwise the balances will not be accurate.
1. Run your Spark job by following the instructions in the 'To Run' section below.
1. Within the console output you will find the account balances, compare these to the values within the file `balances.txt` present in the root of your `bank-kafka` directory.  If your code is implemented correctly the balances should match.

## To Run

* Right click the App class in an IDE such as IntelliJ or Eclipse and click run.
* NOT RECOMMENDED DUE TO A SHUTDOWN BUG: Alternatively run `mvn exec:java` in a terminal in the same directory as this README after a `mvn clean install`.