package com.bogdan3000.dintegrate.donation;

import java.util.function.Consumer;

public interface DonationProvider {
    void connect();
    void disconnect();
    boolean isConnected();
    void onDonation(Consumer<DonationEvent> handler);

    class DonationEvent {
        private final String source;
        private final String eventType;
        private final String username;
        private final double amount;
        private final String currency;
        private final String message;
        private final int id;

        public DonationEvent(String username, double amount, String message, int id) {
            this("donatepay", "donation", username, amount, "RUB", message, id);
        }

        public DonationEvent(String source, String eventType, String username, double amount, String currency, String message, int id) {
            this.source = source != null ? source : "unknown";
            this.eventType = eventType != null ? eventType : "donation";
            this.username = username != null ? username : "Unknown";
            this.amount = amount;
            this.currency = currency != null ? currency : "";
            this.message = message != null ? message : "";
            this.id = id;
        }

        public String getSource() { return source; }
        public String getEventType() { return eventType; }
        public String getUsername() { return username; }
        public double getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getMessage() { return message; }
        public int getId() { return id; }

        @Override
        public String toString() {
            return "DonationEvent{" +
                    "source='" + source + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", username='" + username + '\'' +
                    ", amount=" + amount +
                    ", currency='" + currency + '\'' +
                    ", message='" + message + '\'' +
                    ", id=" + id +
                    '}';
        }
    }
}
