package app.psst.chat.record;

import java.time.Instant;

public record MessageRecord(long id, long conversationId, long senderId,
                            String senderName, String text, Instant sentAt) {}
