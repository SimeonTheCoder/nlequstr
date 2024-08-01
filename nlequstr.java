package build;

import data.Array;
import data.ObjType;
import data.ReadableFile;
import data.WritableFile;
import operations.Operation;
import parser.Interpreter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Stack;

public enum nlequstr implements Operation {
    PING {
        @Override
        public ObjType[] getArguments() {
            return new ObjType[]{};
        }

        @Override
        public void execute(Object[] instruction, float[] memory, HashMap<String, WritableFile> writableFiles, HashMap<String, ReadableFile> readableFiles, HashMap<String, Array> arrays, String[] stringTable) throws IOException {
            System.out.println("Pong!");
        }

        @Override
        public String help() {
            return "";
        }
    }, EQCONV {
        @Override
        public ObjType[] getArguments() {
            return new ObjType[] {ObjType.STRING, ObjType.NUMBER};
        }

        @Override
        public void execute(Object[] instruction, float[] memory, HashMap<String, WritableFile> writableFiles, HashMap<String, ReadableFile> readableFiles, HashMap<String, Array> arrays, String[] stringTable) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
            int index = (int) Interpreter.getValue(instruction[2], memory);

            String input = (String) instruction[1];

            if(input.startsWith("#")) {
                input = stringTable[Integer.parseInt(input.substring(1))];
            }

            StringBuilder result = new StringBuilder();

            Stack<Character> stack = new Stack<>();

            for(int i = 0; i < input.length(); i ++) {
                char ch = input.charAt(i);

                if ("+-*/^".contains(String.valueOf(ch))) {
                    result.append(" ");
                    while(!stack.isEmpty() && (priority(ch) < priority(stack.peek()) ||
                            (priority(ch) == priority(stack.peek()) && associativity(ch) == 'L'))) {
                        result.append(stack.pop());
                        result.append(" ");
                    }

                    stack.push(ch);
                    result.append(" ");
                } else if (ch == '(') {
                    stack.push(ch);
                } else if (ch == ')') {
                    result.append(" ");
                    while(stack.peek() != '(') {
                        result.append(stack.pop());
                        result.append(" ");
                    }

                    stack.pop();
                } else {
                    result.append(ch);
                }
            }

            result.append(" ");
            while(!stack.empty()) {
                result.append(stack.pop());
                result.append(" ");
            }

            stringTable[index] = result.toString();
        }

        @Override
        public String help() {
            return "Converts an infix equation to postfix";
        }
    }, EQEVAL {
        @Override
        public ObjType[] getArguments() {
            return new ObjType[]{ObjType.NUMBER};
        }

        @Override
        public void execute(Object[] instruction, float[] memory, HashMap<String, WritableFile> writableFiles, HashMap<String, ReadableFile> readableFiles, HashMap<String, Array> arrays, String[] stringTable) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
            int index = (int) Interpreter.getValue(instruction[1], memory);
            String str = stringTable[index];

            Stack<String> stack = new Stack<>();

            String[] tokens = str.trim().split("\\s+");

            for (String token : tokens) {
                if ("+-*/^".contains(token)) {
                    float b = Float.parseFloat(String.valueOf(stack.pop()));
                    float a = Float.parseFloat(String.valueOf(stack.pop()));

                    float result = switch (token) {
                        case "+" -> a + b;
                        case "-" -> a - b;
                        case "*" -> a * b;
                        case "/" -> a / b;
                        default -> 0;
                    };

                    stack.push(String.valueOf(result));
                } else {
                    stack.push(token);
                }
            }

            memory[(Integer) instruction[8]] = Float.parseFloat(stack.pop());
        }

        @Override
        public String help() {
            return "Evaluates a postfix expression";
        }
    }, EQCODEGEN {
        @Override
        public ObjType[] getArguments() {
            return new ObjType[] {ObjType.NUMBER};
        }

        @Override
        public void execute(Object[] instruction, float[] memory, HashMap<String, WritableFile> writableFiles, HashMap<String, ReadableFile> readableFiles, HashMap<String, Array> arrays, String[] stringTable) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, InstantiationException {
            int index = (int) Interpreter.getValue(instruction[1], memory);
            String str = stringTable[index];

            tokens = str.split(" ");

            ExpressionNode root = new ExpressionNode();

            createTree(root);
            printCode(root);
        }

        @Override
        public String help() {
            return "Generates pseudocode from a given expression";
        }
    };

    public String[] tokens;
    public String currentToken;
    public int tokenIndex = 0;

    public HashMap<String, Float> params;

    public void nextToken() {
        currentToken = (tokenIndex < tokens.length) ? tokens[tokenIndex++] : "\0";
    }

    public void expression(ExpressionNode expressionNode) {
        term(expressionNode);

        while (currentToken.equals("+") || currentToken.equals("-")) {
            ExpressionNode operatorExpressionNode = new ExpressionNode(2, currentToken);

            operatorExpressionNode.arg1 = new ExpressionNode(expressionNode);
            nextToken();
            operatorExpressionNode.arg2 = new ExpressionNode();

            term(operatorExpressionNode.arg2);

            copyNode(operatorExpressionNode, expressionNode);
        }
    }

    public void term(ExpressionNode expressionNode) {
        factor(expressionNode);

        while (currentToken.equals("*") || currentToken.equals("/")) {
            ExpressionNode operatorExpressionNode = new ExpressionNode(2, currentToken);

            operatorExpressionNode.arg1 = new ExpressionNode(expressionNode);
            nextToken();
            operatorExpressionNode.arg2 = new ExpressionNode();

            factor(operatorExpressionNode.arg2);

            copyNode(operatorExpressionNode, expressionNode);
        }
    }

    public void factor(ExpressionNode expressionNode) {
        if (currentToken.equals("(")) {
            nextToken();
            expression(expressionNode);
            nextToken();
        } else {
            expressionNode.type = 0;
            expressionNode.operation = currentToken;

            nextToken();
        }
    }

    public void copyNode(ExpressionNode source, ExpressionNode destination) {
        destination.type = source.type;
        destination.operation = source.operation;
        destination.arg1 = source.arg1;
        destination.arg2 = source.arg2;
    }

    public void createTree(ExpressionNode root) {
        tokenIndex = 0;
        nextToken();
        expression(root);
    }

    public int varIndex = 0;

    public void printArg(ExpressionNode pointer) {
        varIndex++;
        pointer.varName = "t" + varIndex;
        System.out.println(pointer.varName + "=" + pointer.operation);
    }

    public void printCode(ExpressionNode pointer) {
        switch (pointer.type) {
            case 0:
                printArg(pointer);
                break;

            case 2:
                printCode(pointer.arg1);
                printCode(pointer.arg2);

                varIndex++;

                pointer.varName = "t" + varIndex;

                System.out.println(
                        pointer.varName + "=" + pointer.arg1.varName + pointer.operation + pointer.arg2.varName
                );

                break;
        }
    }

    public nlequstr value(String str) {
        switch (str) {
            case "PING":
                return PING;

            default:
                return null;
        }
    }

    int priority(char c) {
        if (c == '^') return 3;
        else if (c == '/' || c == '*') return 2;
        else if (c == '+' || c == '-') return 1;
        else return -1;
    }

    char associativity(char c)
    {
        if (c == '^') return 'R';
        return 'L'; // Default to left-associative
    }

    nlequstr() {
    }

    public class ExpressionNode {
        public int type; // 0: leaf node, 2: operator node

        public String operation;
        public double value;

        public String varName;
        public ExpressionNode arg1, arg2;

        public ExpressionNode() {
            type = 0;
            value = 0.0;

            varName = "";
            operation = "\0";

            arg1 = null;
            arg2 = null;
        }

        public ExpressionNode(ExpressionNode expressionNode) {
            this.type = expressionNode.type;
            this.value = expressionNode.value;

            this.varName = expressionNode.varName;
            this.operation = expressionNode.operation;

            this.arg1 = expressionNode.arg1;
            this.arg2 = expressionNode.arg2;
        }

        public ExpressionNode(int type, String operation) {
            this.type = type;
            this.operation = operation;

            varName = "";
            operation = "\0";

            arg1 = null;
            arg2 = null;
        }
    }
}