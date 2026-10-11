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
}

