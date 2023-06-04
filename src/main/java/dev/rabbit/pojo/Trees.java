package dev.rabbit.pojo;

import dev.rabbit.record.Tree;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "trees")
@Data
@AllArgsConstructor
public class Trees {
    @Id
    @Field("_id")
    private String id;
    @Field("trees")
    private List<Tree> trees;
}