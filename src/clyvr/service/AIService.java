package clyvr.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AIService {

    private static final String API_KEY = System.getenv("GROQ_API_KEY");
    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "openai/gpt-oss-20b";

    private static final List<String[]> conversationHistory = new ArrayList<>();

    public static void clearHistory() {
        synchronized (conversationHistory) {
            conversationHistory.clear();
        }
    }

    private static String buildSystemPrompt(String activePage, String userRole) {
        return "You are the CLYVR Navigation Assistant for a desktop Clinic Appointment Management System.\\n" +
               "CRITICAL RULES:\\n" +
               "1. Refer to the product strictly as a 'system' or 'appointment management system'. NEVER call it an 'app' or 'application'.\\n" +
               "2. Assist all user roles (Patients, Doctors, Receptionists, Administrators) ONLY with end-user navigation, screen usage, and button guides.\\n" +
               "3. NEVER reveal, output, or share account passwords or confidential credentials under any circumstances.\\n" +
               "4. DO NOT give coding advice, software development steps, SQL schema guidance, or unit testing advice.\\n" +
               "5. If the user mentions 'This action is not available yet' or feature unavailable popups, inform them directly that the feature is currently unimplemented in this system build.\\n" +
               "6. Use ONLY clean plain text. DO NOT use backticks (`), double asterisks (**), hashtags (#), or code blocks.\\n" +
               "7. Strictly refuse medical symptoms/advice, homework help, and off-topic questions.\\n" +
               "8. Keep responses direct, helpful, and concise.\\n\\n" +
               "CURRENT CONTEXT:\\n" +
               "- User Role: " + (userRole != null ? userRole : "Unknown") + "\\n" +
               "- Active Screen: " + (activePage != null ? activePage : "Dashboard") + "\\n\\n" +
               SystemKnowledge.SYSTEM_DOCUMENTATION.replace("\n", "\\n").replace("\"", "\\\"");
    }

    public static CompletableFuture<String> askAI(String userMessage, String activePage, String userRole) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (API_KEY.contains("YOUR_ACTUAL_KEY_HERE")) {
                    return "Error: Please update API_KEY in AIService.java with a valid Groq API key starting with gsk_";
                }

                String cleanKey = API_KEY.trim();
                String safeInput = escapeJson(userMessage);

                StringBuilder messagesJson = new StringBuilder();
                messagesJson.append("[\n");
                messagesJson.append("  {\"role\": \"system\", \"content\": \"").append(buildSystemPrompt(activePage, userRole)).append("\"},\n");

                synchronized (conversationHistory) {
                    int maxMessages = 6;
                    int startIndex = Math.max(0, conversationHistory.size() - maxMessages);
                    for (int i = startIndex; i < conversationHistory.size(); i++) {
                        String[] pair = conversationHistory.get(i);
                        messagesJson.append("  {\"role\": \"user\", \"content\": \"").append(escapeJson(pair[0])).append("\"},\n");
                        messagesJson.append("  {\"role\": \"assistant\", \"content\": \"").append(escapeJson(pair[1])).append("\"},\n");
                    }
                }

                messagesJson.append("  {\"role\": \"user\", \"content\": \"").append(safeInput).append("\"}\n");
                messagesJson.append("]");

                String jsonPayload = """
                    {
                      "model": "%s",
                      "messages": %s
                    }
                    """.formatted(MODEL_NAME, messagesJson.toString());

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ENDPOINT))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + cleanKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String reply = extractContentFromJson(response.body());
                    reply = reply.replace("**", "").replace("`", "").replace("* ", "• ");

                    synchronized (conversationHistory) {
                        conversationHistory.add(new String[]{userMessage, reply});
                    }

                    return reply;
                } else if (response.statusCode() == 429) {
                    return "The AI rate limit was reached. Please wait a few seconds before sending another message.";
                } else {
                    return "Groq Error (" + response.statusCode() + "): " + response.body();
                }

            } catch (Exception e) {
                e.printStackTrace();
                return "Failed to connect to AI Assistant. Check network connection.";
            }
        });
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private static String extractContentFromJson(String rawJson) {
        try {
            String targetKey = "\"content\":\"";
            int startIndex = rawJson.indexOf(targetKey);
            if (startIndex == -1) {
                targetKey = "\"content\": \"";
                startIndex = rawJson.indexOf(targetKey);
            }
            if (startIndex == -1) return "No response text found.";

            startIndex += targetKey.length();
            StringBuilder content = new StringBuilder();
            boolean escaped = false;
            for (int i = startIndex; i < rawJson.length(); i++) {
                char c = rawJson.charAt(i);
                if (escaped) {
                    if (c == 'n') content.append('\n');
                    else if (c == '"') content.append('"');
                    else if (c == '\\') content.append('\\');
                    else content.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    content.append(c);
                }
            }
            return content.toString();
        } catch (Exception e) {
            return "Error reading response format.";
        }
    }
}