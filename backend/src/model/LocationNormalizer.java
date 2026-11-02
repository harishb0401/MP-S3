package model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalization utility for complaint locations and categories.
 * Enforces safe, documented normalization rules:
 * - Trims whitespace and normalizes case
 * - Strips punctuation safely
 * - Normalizes municipal street/address abbreviations (Road/Rd, Street/St, Avenue/Ave, etc.)
 * - Rejects malformed, blank, or placeholder locations (does not treat them as official locations)
 * - Extracts open-issue references (e.g., GRV-2026-0001)
 */
public class LocationNormalizer {

    private static final Pattern ISSUE_REF_PATTERN = Pattern.compile("\\b(GRV-\\d{4}-\\d{4})\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Checks if a location string is a valid official municipal location.
     * Rejects empty, whitespace-only, punctuation-only, or obvious placeholder values.
     */
    public static boolean isValidLocation(String location) {
        if (location == null) return false;
        String trimmed = location.trim();
        if (trimmed.length() < 3) return false;

        // Strip non-alphanumeric characters
        String alphanumeric = trimmed.replaceAll("[^a-zA-Z0-9]", "");
        if (alphanumeric.length() < 3) return false;

        // Must contain at least one letter
        boolean hasLetter = false;
        for (char ch : alphanumeric.toCharArray()) {
            if (Character.isLetter(ch)) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) return false;

        // Reject obvious placeholders and malformed inputs
        String lower = trimmed.toLowerCase();
        if (lower.equals("n/a") || lower.equals("na") || lower.equals("none") ||
            lower.equals("null") || lower.equals("unknown") || lower.equals("test") ||
            lower.equals("somewhere") || lower.equals("nil") || lower.equals("city")) {
            return false;
        }

        return true;
    }

    /**
     * Normalizes a location string for consistent candidate duplicate comparison.
     * Returns null if location is invalid or malformed.
     */
    public static String normalizeLocation(String location) {
        if (!isValidLocation(location)) {
            return null;
        }

        String loc = location.trim().toLowerCase();

        // Replace punctuation with spaces
        loc = loc.replaceAll("[,.;:!#?_/\\\\|\\-\\[\\](){}\"']", " ");

        // Normalize common municipal street/address abbreviations with word boundaries
                // Normalize ordinal street numbers (1st -> 1, 2nd -> 2, 3rd -> 3, 4th -> 4, etc.)
        loc = loc.replaceAll("\\b(\\d+)(?:st|nd|rd|th)\\b", "$1");

loc = loc.replaceAll("\\bstreet\\b", "st");
        loc = loc.replaceAll("\\bst\\b", "st");
        loc = loc.replaceAll("\\broad\\b", "rd");
        loc = loc.replaceAll("\\brd\\b", "rd");
        loc = loc.replaceAll("\\bavenue\\b", "ave");
        loc = loc.replaceAll("\\bave\\b", "ave");
        loc = loc.replaceAll("\\blane\\b", "ln");
        loc = loc.replaceAll("\\bln\\b", "ln");
        loc = loc.replaceAll("\\bcross\\b", "cross");
        loc = loc.replaceAll("\\bcr\\b", "cross");
        loc = loc.replaceAll("\\bmain\\b", "main");
        loc = loc.replaceAll("\\bnagar\\b", "nagar");
        loc = loc.replaceAll("\\bwest\\b", "west");
        loc = loc.replaceAll("\\bw\\b", "west");
        loc = loc.replaceAll("\\beast\\b", "east");
        loc = loc.replaceAll("\\be\\b", "east");
        loc = loc.replaceAll("\\bnorth\\b", "north");
        loc = loc.replaceAll("\\bn\\b", "north");
        loc = loc.replaceAll("\\bsouth\\b", "south");
        loc = loc.replaceAll("\\bs\\b", "south");
        loc = loc.replaceAll("\\bblock\\b", "block");
        loc = loc.replaceAll("\\bblk\\b", "block");

        // Collapse whitespace
        loc = loc.replaceAll("\\s+", " ").trim();

        if (loc.length() < 3) return null;
        return loc;
    }

    /**
     * Normalizes category names for consistent comparison across aliases.
     */
    public static String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) return "";
        String c = category.trim().toLowerCase();
        if (c.contains("water")) return "water supply";
        if (c.contains("road")) return "road damage";
        if (c.contains("waste") || c.contains("sanitation")) return "waste management";
        if (c.contains("light") || c.contains("electr")) return "streetlight";
        return c;
    }

    /**
     * Extracts an existing open-issue reference (e.g., GRV-2026-0001) from text.
     * Returns the complaint ID if found, or null otherwise.
     */
    public static String extractIssueReference(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Matcher matcher = ISSUE_REF_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * The exactly five valid canonical demonstration locations for Makkal Nagar.
     */
    public static final java.util.List<String> CANONICAL_DEMO_LOCATIONS = java.util.Collections.unmodifiableList(
            java.util.Arrays.asList(
                    "Central Market",
                    "North Street",
                    "South Colony",
                    "Lake Road",
                    "Government Hospital Area"
            )
    );

    public static java.util.List<String> getCanonicalDemoLocations() {
        return CANONICAL_DEMO_LOCATIONS;
    }

    public static boolean isCanonicalDemoLocation(String location) {
        if (location == null) return false;
        String trimmed = location.trim();
        for (String canon : CANONICAL_DEMO_LOCATIONS) {
            if (canon.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves an arbitrary or alias location string to one of the 5 canonical demo locations
     * if a sensible mapping exists, otherwise returns the normalized location or null.
     */
    public static String resolveToCanonicalDemoLocation(String location) {
        if (location == null) return null;
        String trimmed = location.trim();
        for (String canon : CANONICAL_DEMO_LOCATIONS) {
            if (canon.equalsIgnoreCase(trimmed)) {
                return canon;
            }
        }
        String norm = normalizeLocation(location);
        if (norm == null) return null;

        if (norm.contains("market") || norm.contains("bgiuerg")) {
            return "Central Market";
        } else if (norm.contains("north") || norm.contains("main")) {
            return "North Street";
        } else if (norm.contains("south") || norm.contains("gandhi") || norm.contains("cross")) {
            return "South Colony";
        } else if (norm.contains("lake")) {
            return "Lake Road";
        } else if (norm.contains("hospital") || norm.contains("anna")) {
            return "Government Hospital Area";
        }
        return null;
    }

    public static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Calculates the Haversine distance in meters between two GPS coordinates.
     * Returns -1.0 if any coordinate is null or invalid.
     */
    public static double calculateGpsDistanceMeters(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return -1.0;
        }
        if (Double.isNaN(lat1) || Double.isNaN(lon1) || Double.isNaN(lat2) || Double.isNaN(lon2)) {
            return -1.0;
        }
        if (lat1 < -90.0 || lat1 > 90.0 || lat2 < -90.0 || lat2 > 90.0) {
            return -1.0;
        }
        if (lon1 < -180.0 || lon1 > 180.0 || lon2 < -180.0 || lon2 > 180.0) {
            return -1.0;
        }

        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2.0) * Math.sin(deltaPhi / 2.0) +
                   Math.cos(phi1) * Math.cos(phi2) *
                   Math.sin(deltaLambda / 2.0) * Math.sin(deltaLambda / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Checks if two coordinate pairs are within a given proximity threshold in meters.
     */
    public static boolean isGpsProximityMatch(Double lat1, Double lon1, Double lat2, Double lon2, double thresholdMeters) {
        double dist = calculateGpsDistanceMeters(lat1, lon1, lat2, lon2);
        return dist >= 0.0 && dist <= thresholdMeters;
    }

    /**
     * Determines whether two location strings match after normalization and token-level comparison.
     * Enforces numeric token exactness (e.g. street 5 vs street 50) and high lexical similarity.
     */
    public static boolean isNormalizedLocationMatch(String locA, String locB) {
        if (locA == null || locB == null) return false;
        String normA = normalizeLocation(locA);
        String normB = normalizeLocation(locB);
        if (normA == null || normB == null) return false;

        if (normA.equals(normB)) return true;

        // Token set comparison (handles reordering like "Anna Nagar Street 5" vs "Street 5 Anna Nagar")
        java.util.Set<String> tokensA = new java.util.HashSet<>(java.util.Arrays.asList(normA.split("\\s+")));
        java.util.Set<String> tokensB = new java.util.HashSet<>(java.util.Arrays.asList(normB.split("\\s+")));

        if (tokensA.equals(tokensB)) return true;

        // Numeric tokens (street numbers, block numbers) must match exactly
        java.util.Set<String> numsA = new java.util.HashSet<>();
        java.util.Set<String> numsB = new java.util.HashSet<>();
        for (String t : tokensA) {
            String num = t.replaceAll("[^0-9]", "");
            if (!num.isEmpty()) numsA.add(num);
        }
        for (String t : tokensB) {
            String num = t.replaceAll("[^0-9]", "");
            if (!num.isEmpty()) numsB.add(num);
        }
        if (!numsA.equals(numsB)) return false;

        java.util.Set<String> intersection = new java.util.HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        int unionSize = tokensA.size() + tokensB.size() - intersection.size();
        if (unionSize > 0) {
            double jaccard = (double) intersection.size() / unionSize;
            if (jaccard >= 0.70) return true;
        }

        return false;
    }

    private static final java.util.Set<String> STOP_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "the", "a", "an", "is", "are", "was", "were", "in", "on", "at", "to", "for", "of", "with",
            "by", "from", "and", "or", "not", "this", "that", "these", "those", "there", "here", "it",
            "its", "my", "our", "your", "their", "we", "i", "you", "he", "she", "they", "me", "us",
            "him", "her", "them", "please", "kindly", "help", "issue", "problem", "complaint", "near",
            "opposite", "beside", "behind", "front", "facing", "corner", "area", "causing", "causes",
            "having", "very", "too", "much", "many", "some", "any", "all", "been", "being"
    ));

    /**
     * Stems words to canonical roots for robust semantic matching.
     */
    public static String stemWord(String word) {
        if (word == null) return "";
        String w = word.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (w.length() <= 3) return w;

        // Domain synonym mappings
        if (w.equals("potholes") || w.equals("crater") || w.equals("craters") || w.equals("pit") || w.equals("pits")) {
            return "pothole";
        }
        if (w.equals("burst") || w.equals("bursting") || w.equals("leak") || w.equals("leaking") || w.equals("leakage") || w.equals("seep") || w.equals("seepage")) {
            return "leak";
        }
        if (w.equals("garbage") || w.equals("trash") || w.equals("waste") || w.equals("debris") || w.equals("litter")) {
            return "waste";
        }
        if (w.equals("overflow") || w.equals("overflowing") || w.equals("dumping") || w.equals("dumped") || w.equals("piled")) {
            return "overflow";
        }
        if (w.equals("broken") || w.equals("damaged") || w.equals("damaging") || w.equals("damage") || w.equals("destruction")) {
            return "damage";
        }
        if (w.equals("streetlight") || w.equals("streetlights") || w.equals("lamp") || w.equals("lamps") || w.equals("light") || w.equals("lights") || w.equals("bulb") || w.equals("bulbs") || w.equals("tubelight") || w.equals("fixture")) {
            return "light";
        }

        // Standard suffix stripping
        if (w.endsWith("ing") && w.length() > 5) {
            return w.substring(0, w.length() - 3);
        }
        if (w.endsWith("ed") && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("es") && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && w.length() > 3 && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }

        return w;
    }

    /**
     * Extracts meaningful content stems from description text and optional category context.
     */
    /**
     * Extracts meaningful content stems from description text.
     */
    public static java.util.Set<String> extractContentTokens(String text) {
        java.util.Set<String> tokens = new java.util.HashSet<>();
        if (text == null || text.trim().isEmpty()) return tokens;

        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        for (String word : clean.split("\\s+")) {
            String w = word.trim();
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                String stem = stemWord(w);
                if (stem.length() >= 3 && !STOP_WORDS.contains(stem)) {
                    tokens.add(stem);
                }
            }
        }
        return tokens;
    }

    public static java.util.Set<String> extractContentTokens(String text, String category) {
        return extractContentTokens(text);
    }

    /**
     * Computes similarity between two complaint descriptions (0.0 to 1.0) using Sorensen-Dice coefficient
     * over content stems and domain vocabulary.
     */
    public static double calculateDescriptionSimilarity(String descA, String descB) {
        java.util.Set<String> tokensA = extractContentTokens(descA);
        java.util.Set<String> tokensB = extractContentTokens(descB);

        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }

        java.util.Set<String> intersection = new java.util.HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        int total = tokensA.size() + tokensB.size();
        if (total == 0) return 0.0;

        return (2.0 * intersection.size()) / (double) total;
    }

    public static double calculateDescriptionSimilarity(String descA, String descB, String category) {
        return calculateDescriptionSimilarity(descA, descB);
    }
}
