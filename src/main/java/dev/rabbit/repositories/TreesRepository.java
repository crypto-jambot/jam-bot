package dev.rabbit.repositories;

import dev.rabbit.pojo.Trees;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TreesRepository extends MongoRepository<Trees, String> {
}
