package com.sunmoon.bo.domain.board;

import java.util.List;
import java.util.Optional;

public interface PostRepository {

    List<Post> findAll();

    Optional<Post> find(long id);

    Post insert(String title, String content, String author);

    boolean update(long id, String title, String content);

    boolean delete(long id);
}
