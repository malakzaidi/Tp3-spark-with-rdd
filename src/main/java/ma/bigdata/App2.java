package ma.bigdata;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

public class App2 {
    public static void main(String[] args) {
        SparkConf conf = new SparkConf().setAppName("App2 - Ventes par Ville et Année");
        JavaSparkContext sc = new JavaSparkContext(conf);

        // Lecture depuis HDFS
        String inputPath = "hdfs://namenode:8020/data/ventes.txt";
        System.out.println("Lecture depuis : " + inputPath);

        JavaRDD<String> lignes = sc.textFile(inputPath);

        JavaPairRDD<Tuple2<String, String>, Double> ventes = lignes.mapToPair(l -> {
            String[] p = l.trim().split("\\s+");
            String annee = p[0].split("-")[0];
            return new Tuple2<>(new Tuple2<>(p[1], annee), Double.parseDouble(p[3]));
        });

        System.out.println("\n========== RÉSULTATS APP2 ==========");
        ventes.reduceByKey(Double::sum)
                .collect()
                .stream()
                .sorted((a, b) -> a._1()._2().compareTo(b._1()._2()) != 0 ?
                        a._1()._2().compareTo(b._1()._2()) : a._1()._1().compareTo(b._1()._1()))
                .forEach(t -> System.out.printf("%s | %s | %.2f DH%n", t._1()._1(), t._1()._2(), t._2()));
        System.out.println("====================================\n");

        sc.close();
    }
}
