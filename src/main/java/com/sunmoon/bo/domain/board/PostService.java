package com.sunmoon.bo.domain.board;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PostService {

    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    public List<Post> list() {
        return repository.findAll();
    }

    public Optional<Post> read(long id) {
        return repository.find(id);
    }

    /** The author comes from the session, not the caller's payload. */
    public Post create(PostRequest request, String author) {
        return repository.insert(request.title(), request.content(), author);
    }

    public boolean save(long id, PostRequest request) {
        return repository.update(id, request.title(), request.content());
    }

    public boolean delete(long id) {
        return repository.delete(id);
    }
}
