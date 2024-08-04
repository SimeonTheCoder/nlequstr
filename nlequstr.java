package build;

import data.Array;
import data.ObjType;
import data.ReadableFile;
import data.WritableFile;
import nodes.Node;
import operations.Operation;
import parser.Interpreter;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

            str = str.replace("(", "( ");
            str = str.replace(")", " )");

            tokens = str.split("\\s+");

            Node root = new Node();

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
        currentToken = (tokenIndex < tokens.length) ? tokens[tokenIndex++] : "";
    }

    public void expression(Node pointer) {
        term(pointer);

        while (currentToken.equals("+") || currentToken.equals("-")) {
            Node operatorNode = new Node();
            operatorNode.instruction[0] = currentToken;

            if(operatorNode.childNodes.size() == 2) {
                operatorNode.childNodes.set(0, new Node(pointer));
                operatorNode.childNodes.set(1, new Node());
            } else {
                operatorNode.childNodes.add(new Node(pointer));
                nextToken();
                operatorNode.childNodes.add(new Node());
            }


            term(operatorNode.childNodes.get(1));

            copyNode(operatorNode, pointer);
        }
    }

    public void term(Node pointer) {
        factor(pointer);

        while (currentToken.equals("*") || currentToken.equals("/")) {
            Node operatorNode = new Node();
            operatorNode.instruction[0] = currentToken;

            operatorNode.childNodes.add(new Node(pointer));
            nextToken();
            operatorNode.childNodes.add(new Node());

            factor(operatorNode.childNodes.get(1));

            copyNode(operatorNode, pointer);
        }
    }

    public void factor(Node pointer) {
        if (currentToken.equals("(")) {
            nextToken();
            expression(pointer);
            nextToken();
        } else {
            pointer.instruction[0] = currentToken;

            nextToken();
        }
    }

    public void copyNode(Node source, Node destination) {
        destination.instruction[0] = source.instruction[0];

        destination.childNodes.addAll(source.childNodes);
    }

    public void createTree(Node root) {
        tokenIndex = 0;
        nextToken();
        expression(root);
    }

    public int varIndex = 0;

    public void printArg(Node pointer) {
        varIndex++;
        pointer.id = varIndex;

//        System.out.println("def " + pointer.varName);
//        System.out.println("dpr" + pointer.varName + "=" + pointer.operation);
        if(!Character.isDigit(((String) pointer.instruction[0]).charAt(0)) && !"xy".contains((String) pointer.instruction[0])) {
            String before = ((String) pointer.instruction[0]).substring(0, ((String) pointer.instruction[0]).indexOf("_"));
            String after = ((String) pointer.instruction[0]).substring(((String) pointer.instruction[0]).indexOf("_") + 1);

            System.out.println(before + " " + after);

            switch (before) {
                case "sin":
                    System.out.println("cos " + after);
                    varIndex++;
                    break;
                case "cos":
                    System.out.println("sin " + after);
                    System.out.println("mul . -1");
                    System.out.println("dpr ... .");
                    varIndex += 2;

                    return;
                case "log":
                    System.out.println("div 1 " + after);
                    varIndex++;
                    break;
            }

            System.out.println("dpr .. .");
            return;
        }

        String derivative;

        if(Character.isDigit(((String) pointer.instruction[0]).charAt(0))) {
            derivative = "0";
        } else {
            if(pointer.instruction[0].equals("x")) {
                derivative = "param";
            } else {
                derivative = "param2";
            }
        }

        System.out.println("dpr " + pointer.instruction[0] + " " + derivative);
    }

    public void printCode(Node pointer) {
        switch (pointer.childNodes.size()) {
            case 0:
                printArg(pointer);
                break;

            case 2:
                for (Node childNode : pointer.childNodes) {
                    printCode(childNode);
                }

                varIndex++;

                pointer.id = varIndex;

//                System.out.println(
//                        pointer.varName + "=" + pointer.arg1.varName + pointer.operation + pointer.arg2.varName
//                );

//                System.out.println(
//                        pointer.varName + "=" + ".".repeat(pointer.varName - pointer.arg1.varName) + pointer.operation + ".".repeat(pointer.varName - pointer.arg2.varName)
//                );

                String operation = switch ((String) pointer.instruction[0]) {
                    case "+" -> "dad";
                    case "-" -> "dsb";
                    case "*" -> "dml";
                    case "/" -> "ddv";
                    default -> "";
                };

                System.out.println(
                        operation + " " + ".".repeat(pointer.id - pointer.childNodes.get(0).id) + " " + ".".repeat(pointer.id - pointer.childNodes.get(1).id)
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
}