package com.cloudnative.ms_orders.service;
import jakarta.mail.internet.InternetAddress;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
public final class OwnerEmail {
    private OwnerEmail() {}
    public static String from(JwtAuthenticationToken auth) {
        for (String claim : new String[]{"email", "preferred_username", "upn"}) {
            Object value = auth.getToken().getClaims().get(claim);
            if (!(value instanceof String email) || email.length() > 320 || email.contains("\r") || email.contains("\n")) continue;
            try {
                var address = new InternetAddress(email, true);
                address.validate();
                if (email.equals(address.getAddress()) && email.contains("@") && !email.contains("#EXT#")) return email;
            } catch (jakarta.mail.internet.AddressException ignored) { }
        }
        return null;
    }
}
