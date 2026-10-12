package com.netsend.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.netsend.model.ChatMessage;
import com.netsend.model.TargetType;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JsonRepositoryTest {
    @TempDir java.nio.file.Path directory;

    @Test void savesAndReadsHistoryJson() throws Exception {
        JsonRepository<ChatMessage> repository = new JsonRepository<>(directory.resolve("history.json"),
                new TypeReference<List<ChatMessage>>() {}, List::of);
        ChatMessage message = new ChatMessage("id", true, "Alice", "127.0.0.1", Instant.now(),
                TargetType.DIRECT, "127.0.0.1", "hello", "sent");
        repository.save(List.of(message));
        assertEquals(message, repository.load().get(0));
        assertTrue(Files.exists(directory.resolve("history.json")));
    }
}
