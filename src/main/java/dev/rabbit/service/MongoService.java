package dev.rabbit.service;

import dev.rabbit.pojo.Trees;
import dev.rabbit.repositories.TreesRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class MongoService {
    TreesRepository treesRepository;

    public Trees pullTrees(String id, String ordered) {
        return treesRepository.findById(id + ordered).orElseThrow();
    }
}
