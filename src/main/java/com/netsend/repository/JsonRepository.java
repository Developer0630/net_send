package com.netsend.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRepository<T> {
    private static final Logger log = LoggerFactory.getLogger(JsonRepository.class);
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final TypeReference<List<T>> type;
    private final Supplier<List<T>> defaults;

    public JsonRepository(Path path, TypeReference<List<T>> type, Supplier<List<T>> defaults) {
        this.path = path; this.type = type; this.defaults = defaults;
    }

    public synchronized List<T> load() throws IOException {
        if (!Files.exists(path)) return defaults.get();
        return mapper.readValue(path.toFile(), type);
    }

    public synchronized void save(List<T> values) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), values);
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            log.debug("Atomic move unavailable for {}; using replacement move", path);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
