package ma.bigdata;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

public class App2 {
    public static void main(String[] args) {
        SparkConf conf = new SparkConf().setAppName("App2").setMaster("local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);

        JavaRDD<String> lignes = sc.textFile("ventes.txt");

        JavaPairRDD<Tuple2<String, String>, Double> ventes = lignes.mapToPair(l -> {
            String[] p = l.trim().split("\\s+");
            String annee = p[0].split("-")[0];
            return new Tuple2<>(new Tuple2<>(p[1], annee), Double.parseDouble(p[3]));
        });

        ventes.reduceByKey(Double::sum)
                .collect()
                .stream()
                .sorted((a, b) -> a._1()._2().compareTo(b._1()._2()) != 0 ?
                        a._1()._2().compareTo(b._1()._2()) : a._1()._1().compareTo(b._1()._1()))
                .forEach(t -> System.out.printf("%s | %s | %.2f DH%n", t._1()._1(), t._1()._2(), t._2()));

        sc.close();
    }
}