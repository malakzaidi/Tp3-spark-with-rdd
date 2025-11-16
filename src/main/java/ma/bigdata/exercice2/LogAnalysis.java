package ma.bigdata.exercice2;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalysis {

    // Log entry class to hold parsed data
    static class LogEntry {
        String ip;
        String dateTime;
        String method;
        String resource;
        int httpCode;
        int size;

        public LogEntry(String ip, String dateTime, String method, String resource, int httpCode, int size) {
            this.ip = ip;
            this.dateTime = dateTime;
            this.method = method;
            this.resource = resource;
            this.httpCode = httpCode;
            this.size = size;
        }

        @Override
        public String toString() {
            return String.format("IP: %s, DateTime: %s, Method: %s, Resource: %s, Code: %d, Size: %d",
                    ip, dateTime, method, resource, httpCode, size);
        }
    }

    public static void main(String[] args) {
        // Configure Spark for local execution
        SparkConf conf = new SparkConf()
                .setAppName("Web Server Log Analysis")
                .setMaster("local[*]");

        JavaSparkContext sc = new JavaSparkContext(conf);

        // Input path - change to HDFS path for cluster execution
        String inputPath = "ma/bigdata/exercice2/data/access.log";
        System.out.println(" Reading logs from: " + inputPath);

        // 1. Load the log file
        JavaRDD<String> logLines = sc.textFile(inputPath);

        // 2. Parse log lines and extract fields
        JavaRDD<LogEntry> logEntries = logLines.map(line -> parseLogLine(line))
                .filter(Objects::nonNull); // Filter out invalid lines

        // Cache the RDD as we'll use it multiple times
        logEntries.cache();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        WEB SERVER LOG ANALYSIS REPORT");
        System.out.println("=".repeat(60) + "\n");

        // 3. Basic Statistics
        System.out.println("📊 BASIC STATISTICS");
        System.out.println("-".repeat(60));

        long totalRequests = logEntries.count();
        System.out.println("Total Requests: " + totalRequests);

        long errorRequests = logEntries.filter(entry -> entry.httpCode >= 400).count();
        System.out.println("Total Errors (HTTP >= 400): " + errorRequests);

        double errorPercentage = (totalRequests > 0) ? (errorRequests * 100.0 / totalRequests) : 0;
        System.out.printf("Error Percentage: %.2f%%\n\n", errorPercentage);

        // 4. Top 5 IP Addresses with most requests
        System.out.println("🌐 TOP 5 IP ADDRESSES (Most Requests)");
        System.out.println("-".repeat(60));

        JavaPairRDD<String, Integer> ipCounts = logEntries
                .mapToPair(entry -> new Tuple2<>(entry.ip, 1))
                .reduceByKey((a, b) -> a + b);

        List<Tuple2<String, Integer>> topIPs = ipCounts
                .mapToPair(Tuple2::swap) // Swap to (count, ip)
                .sortByKey(false) // Sort by count descending
                .mapToPair(Tuple2::swap) // Swap back to (ip, count)
                .take(5);

        int rank = 1;
        for (Tuple2<String, Integer> ip : topIPs) {
            System.out.printf("%d. %-15s : %d requests\n", rank++, ip._1(), ip._2());
        }
        System.out.println();

        // 5. Top 5 Most Requested Resources
        System.out.println("📄 TOP 5 MOST REQUESTED RESOURCES");
        System.out.println("-".repeat(60));

        JavaPairRDD<String, Integer> resourceCounts = logEntries
                .mapToPair(entry -> new Tuple2<>(entry.resource, 1))
                .reduceByKey((a, b) -> a + b);

        List<Tuple2<String, Integer>> topResources = resourceCounts
                .mapToPair(Tuple2::swap)
                .sortByKey(false)
                .mapToPair(Tuple2::swap)
                .take(5);

        rank = 1;
        for (Tuple2<String, Integer> resource : topResources) {
            System.out.printf("%d. %-30s : %d requests\n", rank++, resource._1(), resource._2());
        }
        System.out.println();

        // 6. Distribution by HTTP Status Code
        System.out.println("📈 REQUESTS DISTRIBUTION BY HTTP STATUS CODE");
        System.out.println("-".repeat(60));

        JavaPairRDD<Integer, Integer> httpCodeCounts = logEntries
                .mapToPair(entry -> new Tuple2<>(entry.httpCode, 1))
                .reduceByKey((a, b) -> a + b);

        Map<Integer, Integer> httpCodeMap = httpCodeCounts.collectAsMap();

        // Sort by HTTP code
        List<Map.Entry<Integer, Integer>> sortedCodes = new ArrayList<>(httpCodeMap.entrySet());
        sortedCodes.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, Integer> entry : sortedCodes) {
            int code = entry.getKey();
            int count = entry.getValue();
            double percentage = (count * 100.0) / totalRequests;
            String status = getHttpStatusDescription(code);
            System.out.printf("HTTP %d (%s) : %d requests (%.2f%%)\n",
                    code, status, count, percentage);
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        END OF REPORT");
        System.out.println("=".repeat(60) + "\n");

        // Stop Spark context
        sc.close();
    }

    /**
     * Parse a log line and extract relevant fields
     */
    private static LogEntry parseLogLine(String line) {
        try {
            // Regex pattern to match Apache log format
            // IP - user [date:time +zone] "METHOD resource PROTOCOL" code size "referer" "user-agent"
            String pattern = "^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"(\\S+) (\\S+) \\S+\" (\\d+) (\\d+).*";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(line);

            if (m.find()) {
                String ip = m.group(1);
                String dateTime = m.group(2);
                String method = m.group(3);
                String resource = m.group(4);
                int httpCode = Integer.parseInt(m.group(5));
                int size = Integer.parseInt(m.group(6));

                return new LogEntry(ip, dateTime, method, resource, httpCode, size);
            }
        } catch (Exception e) {
            System.err.println("Error parsing line: " + line);
            System.err.println("Error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get HTTP status code description
     */
    private static String getHttpStatusDescription(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "Unknown";
        }
    }
}