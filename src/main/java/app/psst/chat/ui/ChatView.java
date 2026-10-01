package app.psst.chat.ui;

import app.psst.chat.record.MessageRecord;
import app.psst.chat.record.UserRecord;
import app.psst.chat.record.ConversationRecord;
import app.psst.chat.service.ChatService;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@Route("")
@RouteAlias("chat")
@PermitAll
public class ChatView extends AppLayout {
    private final ChatService chat;
    private final AuthenticationContext authentication;
    private final UserRecord me;
    private final VerticalLayout drawer = new VerticalLayout();
    private final Div emptyState = new Div(
            new H3("No conversation selected"),
            new Paragraph("Choose a contact to read your messages or say hello."));
    private final MessageList messages = new MessageList();
    private final MessageInput input = new MessageInput();
    private final LinkedHashMap<Long, MessageRecord> shown = new LinkedHashMap<>();
    private final Map<Long, List<Button>> contactButtons = new HashMap<>();
    private Long activeContactId;
    private Long activeConversation;
    private AutoCloseable subscription;

    public ChatView(ChatService chat, AuthenticationContext authentication) {
        this.chat = chat;
        this.authentication = authentication;
        this.me = chat.currentUser();

        addClassName("chat-shell");
        setPrimarySection(Section.DRAWER);
        setDrawerOpened(true);
        drawer.addClassName("chat-drawer");
        drawer.setPadding(false);
        drawer.setSpacing(false);
        drawer.setWidthFull();
        addToDrawer(drawer);
        refreshDrawer();

        messages.setMarkdown(false);
        messages.addClassName("message-stream");
        messages.setWidthFull();
        messages.setHeightFull();
        messages.setVisible(false);
        emptyState.addClassName("empty-state");
        input.setWidthFull();
        input.setEnabled(false);
        input.addSubmitListener(event -> {
            if (activeConversation == null) {
                return;
            }
            try {
                MessageRecord saved = chat.send(activeConversation, event.getValue());
                showMessage(saved);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage());
            }
        });
        VerticalLayout messageArea = new VerticalLayout(emptyState, messages);
        messageArea.addClassName("message-area");
        messageArea.setPadding(false);
        messageArea.setSpacing(false);
        messageArea.setSizeFull();
        messageArea.expand(messages);
        Div composer = new Div(input);
        composer.addClassName("composer-bar");
        VerticalLayout content = new VerticalLayout(messageArea, composer);
        content.addClassName("chat-content");
        content.setPadding(false);
        content.setSpacing(false);
        content.setSizeFull();
        content.expand(messageArea);
        setContent(content);
    }

    private void refreshDrawer() {
        drawer.removeAll();
        contactButtons.clear();

        String header = StringUtils.isBlank(me.username()) ? "Psst." : "Psst... " + me.username();
        H2 brand = new H2(header);
        brand.addClassName("drawer-brand");
        drawer.add(brand);

        H3 conversationsTitle = new H3("Conversations");
        conversationsTitle.addClassName("drawer-section-title");
        drawer.add(conversationsTitle);
        List<ConversationRecord> conversations = chat.conversations();
        if (conversations.isEmpty()) {
            Paragraph none = new Paragraph("No conversations yet");
            none.addClassName("drawer-muted");
            drawer.add(none);
        }
        for (ConversationRecord conversation : conversations) {
            addContactButton(conversation.otherUser(), () ->
                    selectConversation(conversation.id(), conversation.otherUser()));
        }

        H3 contactsTitle = new H3("Contacts");
        contactsTitle.addClassName("drawer-section-title");
        drawer.add(contactsTitle);
        Set<Long> inConversation = conversations.stream()
                .map(conversation -> conversation.otherUser().id()).collect(Collectors.toSet());
        List<UserRecord> newContacts = chat.contacts().stream()
                                           .filter(contact -> !inConversation.contains(contact.id())).toList();
        if (newContacts.isEmpty()) {
            Paragraph none = new Paragraph("No new contacts");
            none.addClassName("drawer-muted");
            drawer.add(none);
        }
        for (UserRecord contact : newContacts) {
            addContactButton(contact, () -> selectConversation(chat.createConversation(contact.id()), contact));
        }
        Button refresh = new Button("Refresh contacts", event -> refreshDrawer());
        refresh.addClassName("drawer-action");
        drawer.add(refresh);

        Button signOut = new Button("Sign out", event -> authentication.logout());
        signOut.addClassName("drawer-section-title");
        signOut.addClassName("drawer-sign-out");
        drawer.add(signOut);
    }

    private void addContactButton(UserRecord contact, Runnable open) {
        Button button = new Button(label(contact), event -> open.run());
        button.addClassName("contact-button");
        if (activeContactId != null && activeContactId == contact.id()) {
            button.addClassName("selected");
        }
        contactButtons.computeIfAbsent(contact.id(), ignored -> new ArrayList<>()).add(button);
        drawer.add(button);
    }

    private String label(UserRecord user) {
        return user.username() + (chat.isConnected(user.id()) ? " \u2022 online" : "");
    }

    private void selectConversation(long id, UserRecord other) {
        activeConversation = id;
        activeContactId = other.id();
        shown.clear();
        for (MessageRecord message : chat.history(id)) {
            shown.put(message.id(), message);
        }
        renderMessages();
        emptyState.setVisible(false);
        messages.setVisible(true);
        input.setEnabled(true);
        refreshDrawer();
    }

    private void showMessage(MessageRecord message) {
        if (activeConversation != null && activeConversation == message.conversationId()
                && shown.putIfAbsent(message.id(), message) == null) {
            if (shown.size() > ChatService.HISTORY_LIMIT) {
                shown.remove(shown.keySet().iterator().next());
            }
            renderMessages();
        }
    }

    private void renderMessages() {
        messages.setItems(shown.values().stream()
                .sorted(java.util.Comparator.comparingLong(MessageRecord::id))
                .map(message -> new MessageListItem(
                        message.text(), message.sentAt(), message.senderName()))
                .toList());
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        subscription = chat.subscribe(me.id(), event.getUI(), this::showMessage, this::refreshPresence);
    }

    @Override
    protected void onDetach(DetachEvent event) {
        try {
            if (subscription != null) {
                subscription.close();
            }
        } catch (Exception ignored) {
            // The close action only removes an in-memory listener.
        }
        super.onDetach(event);
    }

    private void refreshPresence() {
        Set<Long> setOnlineUsers = chat.connectedUserIds();
        contactButtons.forEach((id, buttons) -> buttons.forEach(button -> {
            String name = button.getText().replace(" \u2022 online", "");
            button.setText(name + (setOnlineUsers.contains(id) ? " \u2022 online" : ""));
        }));
    }
}
