package compiler.lib;

public class FOOLlib {

    public static String extractNodeName(String s) { // s is in the form compiler.AST$NameNode
        return s.substring(s.lastIndexOf('$') + 1, s.length() - 4);
    }

    public static String extractCtxName(String s) { // s is in the form compiler.FOOLParser$NameContext
        return s.substring(s.lastIndexOf('$') + 1, s.length() - 7);
    }

    public static String lowerizeFirstChar(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1, s.length());
    }

    public static int typeErrors = 0;

    // joins lines with \n, ignoring null arguments (useful for code generation)
    public static String nlJoin(String... lines) {
        String code = null;
        for (int i = 0; i < lines.length; i++)
            if (lines[i] != null) code = (code == null ? "" : code + "\n") + lines[i];
        return code;
    }

    private static int labCount = 0;

    public static String freshLabel() {
        return "label" + (labCount++);
    }

    private static int funlabCount = 0;

    public static String freshFunLabel() {
        return "function" + (funlabCount++);
    }

    private static String funCode = null;

    public static void putCode(String c) {
        funCode = nlJoin(funCode, "", c);
    }

    public static String getCode() {
        return funCode;
    }
}
