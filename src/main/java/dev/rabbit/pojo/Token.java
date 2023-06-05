package dev.rabbit.pojo;

import dev.rabbit.record.Tree;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
public class Token {

    String name;
    String symbol;
    @EqualsAndHashCode.Include
    String address;
    int decimals;

    public static Token of(Tree.Node node) {
        return new Token(node.name(), node.symbol(), node.address(), node.decimals());
    }

    public static Token of(Token token) {
        return new Token(token.getName(), token.getSymbol(), token.getAddress(), token.getDecimals());
    }
}
