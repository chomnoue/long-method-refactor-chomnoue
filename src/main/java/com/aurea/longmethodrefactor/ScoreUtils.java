package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.utils.Utils;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ScoreUtils {

    private static final int MAX_SCORE_PARAM = 4;

    static boolean reducesLength(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        int length = methodLength(method);
        return length > methodLength(candidateMethod) && length > methodLength(remainingMethod);
    }

    static int paramsScore(MethodDeclaration candidateMethod) {
        int returns = candidateMethod.getType() instanceof VoidType ? 0 : 1;
        return MAX_SCORE_PARAM - returns - candidateMethod.getParameters().size();
    }

    static boolean isLengthEnough(MethodDeclaration method, int minLength){
        return methodLength(method)>=minLength;
    }

    static int nestingDepthSocre(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        int methodDepth = nestingDepth(method);
        int candidateDepth = nestingDepth(candidateMethod);
        int remainingDepth = nestingDepth(remainingMethod);
        return Math.min(methodDepth - remainingDepth, methodDepth - candidateDepth);
    }

    static float nestingAreaScore(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        int methodNestArea = nestingArea(method);
        int candidateNestingArea = nestingArea(candidateMethod);
        int remainingNestArea = nestingArea(remainingMethod);
        int areaReduction = Math.min(methodNestArea - candidateNestingArea, methodNestArea - remainingNestArea);
        int methodDepth = nestingDepth(method);
        return 2f * methodDepth * areaReduction / methodNestArea;
    }

    private static int nestingArea(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getBody().map(ScoreUtils::nestingArea).orElse(0);
    }

    private static int nestingArea(BlockStmt blockStmt) {
        return blockStmt.getChildNodes().stream().mapToInt(ScoreUtils::nestingDepth).sum();
    }

    private static int nestingDepth(Node node) {
        int addedDepth = node instanceof BlockStmt ? 1 : 0;
        return addedDepth + node.getChildNodes().stream().mapToInt(ScoreUtils::nestingDepth).max()
                .orElse(0);
    }

    static int methodLength(MethodDeclaration method) {
        return method.toString().split(Utils.EOL).length;
    }
}
