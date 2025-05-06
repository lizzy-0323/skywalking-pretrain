package com.example.pprof;

import com.google.protobuf.CodedInputStream;
import com.google.perftools.profiles.ProfileProto;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * PProfAnalyzer reads and analyzes Go pprof profile files.
 * It supports reading CPU profiles, heap profiles, goroutine profiles, etc.
 */
public class PProfAnalyzer {
    private ProfileProto.Profile profile;

    /**
     * Reads a pprof file and parses it into a Profile object.
     * The file should be in the protocol buffer format, possibly gzipped.
     *
     * @param file The pprof file to read
     * @throws IOException If there is an error reading the file
     */
    public void readProfile(File file) throws IOException {
        // pprof files are typically gzipped
        try (FileInputStream fis = new FileInputStream(file);
                GZIPInputStream gzis = new GZIPInputStream(fis)) {

            // Parse the protocol buffer
            profile = ProfileProto.Profile.parseFrom(CodedInputStream.newInstance(gzis));
        }
    }

    /**
     * Gets the sampling period from the profile.
     * For CPU profiles, this is typically in nanoseconds.
     *
     * @return The sampling period
     */
    public long getPeriod() {
        return profile.getPeriod();
    }

    /**
     * Gets the sample type descriptions from the profile.
     *
     * @return List of ValueType objects describing the sample types
     */
    public List<ProfileProto.ValueType> getSampleTypes() {
        return profile.getSampleTypeList();
    }

    /**
     * Gets all samples from the profile.
     *
     * @return List of Sample objects
     */
    public List<ProfileProto.Sample> getSamples() {
        return profile.getSampleList();
    }

    /**
     * Gets all functions from the profile.
     *
     * @return List of Function objects
     */
    public List<ProfileProto.Function> getFunctions() {
        return profile.getFunctionList();
    }

    /**
     * Gets location information from the profile.
     *
     * @return List of Location objects
     */
    public List<ProfileProto.Location> getLocations() {
        return profile.getLocationList();
    }

    /**
     * Gets the mapping information from the profile.
     * This typically contains information about binary mappings.
     *
     * @return List of Mapping objects
     */
    public List<ProfileProto.Mapping> getMappings() {
        return profile.getMappingList();
    }

    /**
     * Gets the string table from the profile.
     * Profile messages use string table indices instead of storing strings
     * directly.
     *
     * @return List of strings in the string table
     */
    public List<String> getStringTable() {
        return profile.getStringTableList();
    }

    /**
     * Analyzes the profile and returns a summary of the top hotspots.
     * This is useful for CPU and heap profiles.
     *
     * @param maxEntries Maximum number of entries to return
     * @return Map containing both flat and cumulative values for each function
     */
    public Map<String, ProfileData> analyzeHotspots(int maxEntries) {
        Map<String, ProfileData> profileData = new HashMap<>();

        // Process all samples
        for (ProfileProto.Sample sample : profile.getSampleList()) {
            if (sample.getLocationIdList().isEmpty()) {
                continue;
            }

            // Get value (default to 0 if no values)
            long value = sample.getValueList().isEmpty() ? 0 : sample.getValue(0);

            // Get the leaf function (where the time was actually spent)
            ProfileProto.Location leafLocation = findLocation(sample.getLocationId(0));
            if (leafLocation != null && !leafLocation.getLineList().isEmpty()) {
                ProfileProto.Function leafFunction = findFunction(leafLocation.getLine(0).getFunctionId());
                if (leafFunction != null) {
                    String functionName = getStringFromTable(leafFunction.getName());
                    profileData.computeIfAbsent(functionName, k -> new ProfileData()).addFlatValue(value);
                }
            }

            // Add cumulative value to all functions in the call stack
            Set<String> processedFunctions = new HashSet<>();
            for (Long locationId : sample.getLocationIdList()) {
                ProfileProto.Location location = findLocation(locationId);
                if (location != null && !location.getLineList().isEmpty()) {
                    ProfileProto.Function function = findFunction(location.getLine(0).getFunctionId());
                    if (function != null) {
                        String functionName = getStringFromTable(function.getName());
                        if (processedFunctions.add(functionName)) {
                            // deduplicate for recursive function !!!
                            profileData.computeIfAbsent(functionName, k -> new ProfileData()).addCumValue(value);
                        }
                    }
                }
            }
        }

        // Sort by flat values and limit results
        return profileData.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().getFlatValue(), e1.getValue().getFlatValue()))
                .limit(maxEntries)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    /**
     * Class to hold both flat and cumulative values for a function
     */
    public static class ProfileData {
        private long flatValue = 0;
        private long cumValue = 0;

        public void addFlatValue(long value) {
            flatValue += value;
        }

        public void addCumValue(long value) {
            cumValue += value;
        }

        public long getFlatValue() {
            return flatValue;
        }

        public long getCumValue() {
            return cumValue;
        }
    }

    private ProfileProto.Location findLocation(long locationId) {
        return profile.getLocationList().stream()
                .filter(loc -> loc.getId() == locationId)
                .findFirst()
                .orElse(null);
    }

    private ProfileProto.Function findFunction(long functionId) {
        return profile.getFunctionList().stream()
                .filter(func -> func.getId() == functionId)
                .findFirst()
                .orElse(null);
    }

    private String getStringFromTable(long index) {
        if (index >= 0 && index < profile.getStringTableCount()) {
            return profile.getStringTable((int) index);
        }
        return "";
    }
}
