package dev.rabbit.pojo;

import dev.rabbit.record.Tree;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class Token {

    public static Token of(Tree.Node node) {
        return new Token(node.name(), node.symbol(), node.address(), node.decimals());
    }
    public static Token of(Token token) {
        return new Token(token.getName(), token.getSymbol(), token.getAddress(), token.getDecimals());
    }
    String name;
    String symbol;
    String address;
    int decimals;
}
