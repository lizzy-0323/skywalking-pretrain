package com.example.pprof;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class PProfExample {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java PProfExample <pprof-file>");
            System.exit(1);
        }

        try {
            PProfAnalyzer analyzer = new PProfAnalyzer();
            analyzer.readProfile(new File(args[0]));

            // Print profile information
            long periodNanos = analyzer.getPeriod();
            System.out.printf("Profile Summary:%n");
            System.out.printf("----------------%n");
            System.out.printf("Sampling Period: %.2f ms%n", periodNanos / 1_000_000.0);

            System.out.printf("%nSample Types:%n");
            System.out.printf("-------------%n");
            analyzer.getSampleTypes().forEach(type -> {
                String typeName = analyzer.getStringTable().get((int) type.getType());
                String unit = analyzer.getStringTable().get((int) type.getUnit());
                System.out.printf("- %-15s (unit: %s)%n", typeName, unit);
            });

            System.out.printf("%nTop 10 Hotspots:%n");
            System.out.printf("---------------%n");
            System.out.printf("%-8s %-7s %-7s %-8s %-7s  %s%n", "flat", "flat%", "sum%", "cum", "cum%", "Stack Trace");
            System.out.printf("%-8s %-7s %-7s %-8s %-7s  %s%n", "--------", "-------", "-------", "--------", "-------",
                    "----------");

            Map<String, PProfAnalyzer.ProfileData> hotspots = analyzer.analyzeHotspots(10);

            long totalSamples = hotspots.values().stream()
                    .mapToLong(PProfAnalyzer.ProfileData::getFlatValue)
                    .sum();
            double sumPercentage = 0.0;

            // Get top-10 hotspots
            for (Map.Entry<String, PProfAnalyzer.ProfileData> entry : hotspots.entrySet()) {
                String functionName = entry.getKey();
                PProfAnalyzer.ProfileData data = entry.getValue();
                long flatValue = data.getFlatValue();
                long cumValue = data.getCumValue();

                double flatPercentage = flatValue * 100.0 / totalSamples;
                sumPercentage += flatPercentage;
                double cumPercentage = cumValue * 100.0 / totalSamples;

                // Format the time values with fixed decimal places (nanoseconds to seconds)
                String flatTime = String.format("%.2fs", flatValue * periodNanos / 1_000_000_000.0);
                String cumTime = String.format("%.2fs", cumValue * periodNanos / 1_000_000_000.0);

                // Format percentages with % sign
                String flatPercentStr = String.format("%.2f%%", flatPercentage);
                String sumPercentStr = String.format("%.2f%%", sumPercentage);
                String cumPercentStr = String.format("%.2f%%", cumPercentage);

                System.out.printf("%-8s %-7s %-7s %-8s %-7s  %s%n",
                        flatTime,
                        flatPercentStr,
                        sumPercentStr,
                        cumTime,
                        cumPercentStr,
                        functionName);
            }

        } catch (IOException e) {
            System.err.println("Error reading profile: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
