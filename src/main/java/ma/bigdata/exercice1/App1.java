package ma.bigdata.exercice1;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
import java.util.Arrays;
import java.util.Collections;

public class App1 {
    public static void main(String[] args) {
        SparkConf conf = new SparkConf().setAppName("App1 - Ventes par Ville");
        JavaSparkContext sc = new JavaSparkContext(conf);

        // Lecture depuis HDFS
        String inputPath = "hdfs://namenode:8020/data/ventes.txt";
        System.out.println("📂 Lecture depuis : " + inputPath);

        JavaRDD<String> lignes = sc.textFile(inputPath);

        JavaPairRDD<String, Double> paires = lignes.flatMapToPair(line -> {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 4) {
                String ville = parts[1];
                double prix = Double.parseDouble(parts[3]);
                return Arrays.asList(new Tuple2<>(ville, prix)).iterator();
            }
            return Collections.emptyIterator();
        });

        JavaPairRDD<String, Double> result = paires.reduceByKey((a, b) -> a + b);

        System.out.println("\n========== RÉSULTATS APP1 ==========");
        result.collect().forEach(t ->
                System.out.printf("%s : %.2f DH%n", t._1(), t._2())
        );
        System.out.println("====================================\n");

        sc.close();
    }
}
