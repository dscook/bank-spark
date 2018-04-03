package uk.co.bitcat;

import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.ClientConfiguration;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.mapreduce.AccumuloInputFormat;
import org.apache.accumulo.core.client.mapreduce.lib.impl.ConfiguratorBase;
import org.apache.accumulo.core.client.mapreduce.lib.impl.InputConfigurator;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.user.WholeRowIterator;
import org.apache.hadoop.conf.Configuration;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.SparkConf;
import scala.Tuple2;
import uk.co.bitcat.dto.Transaction;
import uk.co.bitcat.dto.TransactionInput;
import uk.co.bitcat.dto.TransactionOutput;

import java.io.IOException;
import java.util.*;

public class App {

    public static void main(String[] args) throws AccumuloSecurityException {
        SparkConf conf = new SparkConf().setAppName("balances").setMaster("local");
        JavaSparkContext sc = new JavaSparkContext(conf);
        Configuration hadoopConf = sc.hadoopConfiguration();
        ClientConfiguration zkConfig = new ClientConfiguration().withInstance("miniInstance").withZkHosts("localhost:50274");

        ConfiguratorBase.setConnectorInfo(AccumuloInputFormat.class, hadoopConf, "root", new PasswordToken("password"));
        ConfiguratorBase.setZooKeeperInstance(AccumuloInputFormat.class, hadoopConf, zkConfig);
        InputConfigurator.setInputTableName(AccumuloInputFormat.class, hadoopConf, "blockchain");

        IteratorSetting iteratorSetting = new IteratorSetting(50, WholeRowIterator.class);
        InputConfigurator.addIterator(AccumuloInputFormat.class, hadoopConf, iteratorSetting);

        JavaPairRDD<Key, Value> pairRdd =
                sc.newAPIHadoopRDD(hadoopConf, AccumuloInputFormat.class, Key.class, Value.class);

        JavaRDD<Transaction> rdd = pairRdd.map(App::convertRowToTransaction);

        rdd.flatMapToPair(tx -> {

            List<Tuple2<String, Integer>> moneyMovement = new ArrayList<>();

            for (TransactionInput input : tx.getInputs().values()) {
                moneyMovement.add(new Tuple2<>(input.getAddress(), -input.getAmount()));
            }

            for (TransactionOutput output : tx.getOutputs().values()) {
                moneyMovement.add(new Tuple2<>(output.getAddress(), output.getAmount()));
            }

            return moneyMovement.iterator();

        }).reduceByKey((a, b) -> a + b).collect().forEach(System.out::println);

        sc.stop();
    }

    private static Transaction convertRowToTransaction(final Tuple2<Key, Value> row) throws IOException {
        SortedMap<Key, Value> decodedRow = WholeRowIterator.decodeRow(row._1, row._2);

        String txId = "";
        Map<Integer, TransactionInput> txInputs = new HashMap<>();
        Map<Integer, TransactionOutput> txOutputs = new HashMap<>();

        for (Map.Entry<Key,Value> tuple : decodedRow.entrySet()) {
            txId = tuple.getKey().getRow().toString();

            String cf = tuple.getKey().getColumnFamily().toString();
            String cq = tuple.getKey().getColumnQualifier().toString();

            if (cf.equals("inputs") || cf.equals("outputs")) {
                String[] splitCq = cq.split(":");
                Integer index = Integer.valueOf(splitCq[0]);
                String column = splitCq[1];
                String value = tuple.getValue().toString();

                if (cf.equals("inputs")) {
                    TransactionInput txInput = txInputs.getOrDefault(index, new TransactionInput());
                    switch (column.toLowerCase()) {
                        case "txid":
                            txInput.setTxId(value);
                            break;
                        case "utxoindex":
                            txInput.setUtxoIndex(Integer.valueOf(value));
                            break;
                        case "amount":
                            txInput.setAmount(Integer.valueOf(value));
                            break;
                        case "address":
                            txInput.setAddress(value);
                            break;
                        default:
                            break;
                    }
                    txInputs.put(index, txInput);
                } else {
                    TransactionOutput txOutput = txOutputs.getOrDefault(index, new TransactionOutput());
                    switch (column.toLowerCase()) {
                        case "amount":
                            txOutput.setAmount(Integer.valueOf(value));
                            break;
                        case "address":
                            txOutput.setAddress(value);
                            break;
                        default:
                            break;
                    }
                    txOutputs.put(index, txOutput);
                }
            }
        }
        return new Transaction(txId, txInputs, txOutputs);
    }

}
