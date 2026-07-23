package edu.calpoly.csc364.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public final class Operation {
    public enum Operator {
        ADD("+"), SUBTRACT("-"), MULTIPLY("*"), DIVIDE("/");
        private final String symbol;
        Operator(String symbol) { this.symbol = symbol; }
        public String symbol() { return symbol; }
    }

    private String id;
    private int left;
    private int right;
    private Operator operator;
    private long createdAt;

    public Operation() { }

    public Operation(int left, int right, Operator operator) {
        this(UUID.randomUUID().toString(), left, right, operator, System.currentTimeMillis());
    }

    public Operation(String id, int left, int right, Operator operator, long createdAt) {
        this.id = Objects.requireNonNull(id);
        this.left = left;
        this.right = right;
        this.operator = Objects.requireNonNull(operator);
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public int getLeft() { return left; }
    public int getRight() { return right; }
    public Operator getOperator() { return operator; }
    public long getCreatedAt() { return createdAt; }

    public BigDecimal solve() {
        return switch (operator) {
            case ADD -> BigDecimal.valueOf(left + (long) right);
            case SUBTRACT -> BigDecimal.valueOf(left - (long) right);
            case MULTIPLY -> BigDecimal.valueOf(left).multiply(BigDecimal.valueOf(right));
            case DIVIDE -> {
                if (right == 0) throw new ArithmeticException("division by zero");
                yield BigDecimal.valueOf(left).divide(BigDecimal.valueOf(right), 4, RoundingMode.HALF_UP).stripTrailingZeros();
            }
        };
    }

    public String expression() { return left + " " + operator.symbol() + " " + right; }
    @Override public String toString() { return expression() + " [" + id.substring(0, 8) + "]"; }
}
