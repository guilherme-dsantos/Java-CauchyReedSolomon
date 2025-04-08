import io.vawlt.Cauchy256;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SampleEncoderCSV {
    // Class for storing performance metrics
    //mvn clean install -Dspotbugs.skip=true -Dpmd.skip=true -Dcpd.skip=true
    private static class PerformanceMetrics {
        private final int relativeTimeMs;
        private final double cpuUsage;
        private final long memoryUsageMB;

        public PerformanceMetrics(int relativeTimeMs, double cpuUsage, long memoryUsageMB) {
            this.relativeTimeMs = relativeTimeMs;
            this.cpuUsage = cpuUsage;
            this.memoryUsageMB = memoryUsageMB;
        }

        public String toCsvRow() {
            return relativeTimeMs + "," + 
                   String.format("%.2f", cpuUsage) + "," + 
                   memoryUsageMB;
        }
        
        public static String getCsvHeader() {
            return "TimeMs,CPU_Usage_Percent,Memory_Usage_MB";
        }
    }
    
    // Method to collect current system metrics
    private static PerformanceMetrics collectMetrics(long startTimeMs) {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Get CPU usage
        double cpuUsage = 0.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            cpuUsage = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100;
        }
        
        // Get memory usage in MB
        long heapMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long nonHeapMemory = memoryBean.getNonHeapMemoryUsage().getUsed();
        long totalMemoryUsageMB = (heapMemory + nonHeapMemory) / (1024 * 1024);
        
        // Calculate relative time in milliseconds since start
        int relativeTimeMs = (int)(System.currentTimeMillis() - startTimeMs);
        
        return new PerformanceMetrics(relativeTimeMs, cpuUsage, totalMemoryUsageMB);
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: SampleEncoder <file_path> [k] [m] [threads] [iterations] [metric_interval] [warmup_iterations]");
            System.out.println("  k: Number of data blocks (default: 4)");
            System.out.println("  m: Number of recovery blocks (default: 2)");
            System.out.println("  threads: Number of threads to use (default: available processors)");
            System.out.println("  iterations: Number of encoding iterations per thread (default: 5000)");
            System.out.println("  metric_interval: Interval in milliseconds for collecting metrics (default: 2)");
            System.out.println("  warmup_iterations: Number of warmup iterations per thread (default: 500)");
            return;
        }

        // Parse input parameters
        String filePath = args[0];
        int k = (args.length > 1) ? Integer.parseInt(args[1]) : 4;
        int m = (args.length > 2) ? Integer.parseInt(args[2]) : 2;
        int threads = (args.length > 3) ? Integer.parseInt(args[3]) : Runtime.getRuntime().availableProcessors();
        int iterations = (args.length > 4) ? Integer.parseInt(args[4]) : 5000;
        int metricInterval = (args.length > 5) ? Integer.parseInt(args[5]) : 2;
        int warmupIterations = (args.length > 6) ? Integer.parseInt(args[6]) : 500;
        
        System.out.println("Command-line parameters parsed:");
        System.out.println("  File path: " + filePath);
        System.out.println("  k: " + k);
        System.out.println("  m: " + m);
        System.out.println("  threads: " + threads);
        System.out.println("  iterations: " + iterations);
        System.out.println("  metric_interval: " + metricInterval);
        System.out.println("  warmup_iterations: " + warmupIterations);

        // Initialize the Cauchy256 library
        Cauchy256.init();

        try {
            // Read the input file
            File inputFile = new File(filePath);
            if (!inputFile.exists() || !inputFile.isFile()) {
                System.err.println("File not found or not a regular file: " + filePath);
                return;
            }

            byte[] fileData = Files.readAllBytes(inputFile.toPath());
            System.out.println("File size: " + fileData.length + " bytes");


            int initialBlockSize = (fileData.length + k - 1) / k;
            System.out.println("Initial block size calculation: " + initialBlockSize + " bytes");
            
            final int blockSize;
            if (initialBlockSize % 8 != 0) {
                blockSize = ((initialBlockSize / 8) + 1) * 8;  // Round up to next multiple of 8
                System.out.println("Adjusted block size to multiple of 8: " + initialBlockSize + " -> " + blockSize + " bytes");
            } else {
                blockSize = initialBlockSize;
                System.out.println("Block size is already a multiple of 8: " + blockSize + " bytes");
            }

            System.out.println("Using parameters:");
            System.out.println("  k = " + k + " (data blocks)");
            System.out.println("  m = " + m + " (recovery blocks)");
            System.out.println("  Block size = " + blockSize + " bytes");
            System.out.println("  Threads = " + threads);
            System.out.println("  Iterations per thread = " + iterations);
            System.out.println("  Metrics collection interval = " + metricInterval + " ms (relative timestamps)");
            System.out.println("  Warmup iterations = " + warmupIterations);

            // Create CSV file for performance metrics
            String metricsFileName = filePath + "_k_" + k + "_m_" + m + "_t_" + threads + "_metrics.csv";
            PrintWriter metricsWriter = new PrintWriter(new FileWriter(metricsFileName));
            metricsWriter.println(PerformanceMetrics.getCsvHeader());
            
            // Create scheduler for metrics collection
            ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor();
            
            // Perform warmup phase
            System.out.println("Starting warmup phase with " + warmupIterations + " iterations per thread...");
            CountDownLatch warmupLatch = new CountDownLatch(threads);
            

            byte[][][] threadDataBlocksArray = new byte[threads][k][blockSize];
            byte[][] threadRecoveryBlocksArray = new byte[threads][m * blockSize];
            
            // Pre-initialize all data blocks for all threads
            for (int t = 0; t < threads; t++) {
                // Fill with data
                for (int i = 0; i < k; i++) {
                    int copyLength = Math.min(blockSize, fileData.length - i * blockSize);
                    if (copyLength > 0) {
                        System.arraycopy(fileData, i * blockSize, threadDataBlocksArray[t][i], 0, copyLength);
                    }
                }
            }
            
            for (int t = 0; t < threads; t++) {
                final int threadId = t + 1;
                final byte[][] threadDataBlocks = threadDataBlocksArray[t];
                final byte[] threadRecoveryBlocks = threadRecoveryBlocksArray[t];
                
                new Thread(() -> {
                    try {

                        for (int i = 0; i < warmupIterations; i++) {
                            Cauchy256.encode(k, m, threadDataBlocks, threadRecoveryBlocks, blockSize);
                        }
                        
                        System.out.println("Thread " + threadId + " completed warmup");
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " warmup error: " + e.getMessage());
                    } finally {
                        warmupLatch.countDown();
                    }
                }).start();
            }
            

            warmupLatch.await();
            System.out.println("Warmup phase completed");
            

            System.gc();

            Thread.sleep(1000);
            

            CountDownLatch latch = new CountDownLatch(threads);
            

            long startTime = System.currentTimeMillis();
            
            // Start metrics collection
            System.out.println("Starting main benchmark phase...");
            metricsScheduler.scheduleAtFixedRate(() -> {
                try {
                    PerformanceMetrics metrics = collectMetrics(startTime);
                    metricsWriter.println(metrics.toCsvRow());
                    metricsWriter.flush();
                } catch (Exception e) {
                    System.err.println("Error collecting metrics: " + e.getMessage());
                }
            }, 0, metricInterval, TimeUnit.MILLISECONDS);
            

            for (int t = 0; t < threads; t++) {
                final int threadId = t + 1;
                final byte[][] threadDataBlocks = threadDataBlocksArray[t];
                final byte[] threadRecoveryBlocks = threadRecoveryBlocksArray[t];
                
                new Thread(() -> {
                    try {

                        for (int i = 0; i < iterations; i++) {
                            Cauchy256.encode(k,m, threadDataBlocks, threadRecoveryBlocks, blockSize);
                        }
                        
                        System.out.println("Thread " + threadId + " completed " + iterations + " encoding iterations");
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " encoding error: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();
            
            // Stop metrics collection
            metricsScheduler.shutdown();
            metricsScheduler.awaitTermination(5, TimeUnit.SECONDS);
            
            // Close metrics file
            metricsWriter.close();
            
            long endTime = System.currentTimeMillis();
            System.out.println("Encoding completed in " + (endTime - startTime) + " ms");
            System.out.println("Performance metrics saved to: " + metricsFileName);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}