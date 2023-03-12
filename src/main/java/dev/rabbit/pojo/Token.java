package dev.rabbit.pojo;

import dev.rabbit.record.Node;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Token {

    public static Token of(Node node) {
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
