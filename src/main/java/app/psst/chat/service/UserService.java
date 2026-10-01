package app.psst.chat.service;

import app.psst.chat.record.UserRecord;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class UserService implements UserDetailsService {
    private final ChatService chat;

    public UserService(ChatService chat) {
        this.chat = chat;
    }

    public static String validateUsername(String suppliedName) {
        String name = suppliedName == null ? "" : suppliedName.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{3,15}")) {
            throw new IllegalArgumentException("Unsuitable username, use 3-15 letters, digits, or underscores");
        }
        return name;
    }

    @Override
    public UserDetails loadUserByUsername(String suppliedName) throws UsernameNotFoundException {
        String name;
        try {
            name = validateUsername(suppliedName);
        } catch (IllegalArgumentException invalid) {
            throw new UsernameNotFoundException("Invalid username", invalid);
        }
        UserRecord user = chat.findOrCreateUser(name);
        // OTT uses this UserDetails record but never uses a password.
        return new User(user.username(), "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
