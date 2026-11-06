package clyvr.security;

import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Pattern;

public final class Validator {
    public static final int MAX_EMAIL = 254;
    public static final int MAX_NAME = 50;
    public static final int MAX_ADDRESS = 120;
    public static final int MAX_TEXT = 500;
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 64;

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'-]*$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]+$");
    private static final Set<String> COMMON = Set.of(
        "password", "password1", "password123", "12345678", "123456789", "1234567890",
        "qwerty123", "qwertyuiop", "iloveyou1", "admin123", "letmein123", "welcome123");

    private Validator() {}

    public static String email(String v) {
        if (v == null || v.isBlank()) return "Enter your email address.";
        if (v.length() > MAX_EMAIL) return "Email address must be " + MAX_EMAIL + " characters or fewer.";
        if (!EMAIL.matcher(v.trim()).matches()) return "Enter a valid email address.";
        return null;
    }

    public static String name(String label, String v) {
        if (v == null || v.isBlank()) return label + " is required.";
        String t = v.trim();
        if (t.length() > MAX_NAME) return label + " must be " + MAX_NAME + " characters or fewer.";
        if (!NAME.matcher(t).matches()) return label + " can only contain letters, spaces, hyphens, apostrophes and periods.";
        return null;
    }

    public static String phone(String v) {
        if (v == null || v.isBlank()) return "Phone number is required.";
        String t = v.trim();
        if (!PHONE.matcher(t).matches()) return "Phone number can only contain digits, spaces, +, - and parentheses.";
        int digits = t.replaceAll("\\D", "").length();
        if (digits < 7 || digits > 15) return "Phone number must have 7 to 15 digits.";
        return null;
    }

    public static String address(String v) {
        if (v == null || v.isBlank()) return "Address is required.";
        if (v.trim().length() > MAX_ADDRESS) return "Address must be " + MAX_ADDRESS + " characters or fewer.";
        return null;
    }

    public static String birthDate(LocalDate d) {
        if (d == null) return "Use YYYY-MM-DD for a valid birth date.";
        if (d.isAfter(LocalDate.now())) return "Birth date cannot be in the future.";
        if (d.isBefore(LocalDate.now().minusYears(120))) return "Enter a realistic birth date.";
        return null;
    }

    public static String longText(String label, String v) {
        if (v == null || v.isBlank()) return label + " is required.";
        int n = v.trim().length();
        if (n > MAX_TEXT) return label + " must be " + MAX_TEXT + " characters or fewer (currently " + n + ").";
        return null;
    }

    public static String password(String pw, String email) {
        if (pw == null || pw.isEmpty()) return "Please enter a password.";
        if (pw.length() < MIN_PASSWORD) return "Password must be at least " + MIN_PASSWORD + " characters long.";
        if (pw.length() > MAX_PASSWORD) return "Password must be " + MAX_PASSWORD + " characters or fewer.";
        if (!pw.chars().anyMatch(Character::isLetter)) return "Password must include at least one letter.";
        if (!pw.chars().anyMatch(Character::isDigit)) return "Password must include at least one number.";
        if (COMMON.contains(pw.toLowerCase())) return "That password is too common. Choose something harder to guess.";
        if (email != null && !email.isBlank()) {
            String local = email.trim().toLowerCase().split("@")[0];
            if (local.length() >= 4 && pw.toLowerCase().contains(local)) return "Password must not contain your email name.";
        }
        return null;
    }

    public static String firstError(String... results) {
        for (String r : results) if (r != null) return r;
        return null;
    }
}
