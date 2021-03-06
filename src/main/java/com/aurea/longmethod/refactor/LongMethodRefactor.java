package com.aurea.longmethod.refactor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LongMethodRefactor {

    private static final String JAVA_SUFFIX = ".java";

    private final int maxLength;
    private final String srcPaths;
    private final RefactoringCandidatesProvider candidatesProvider;
    private final ApplicableCandidateProvider applicableCandidateProvider;

    LongMethodRefactor(@Value("${maxLength:30}") int maxLength, @Value("${srcPaths}") String srcPaths,
            RefactoringCandidatesProvider candidatesProvider,
            ApplicableCandidateProvider applicableCandidateProvider) {
        this.maxLength = maxLength;
        this.srcPaths = srcPaths;
        this.candidatesProvider = candidatesProvider;
        this.applicableCandidateProvider = applicableCandidateProvider;
    }

    void refactorLongMethods() throws IOException {
        for (String srcPath : srcPaths.split(",")) {
            refactorLongMethods(Paths.get(srcPath));
        }

    }

    private void refactorLongMethods(Path rootPath) throws IOException {
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(new JavaParserTypeSolver(rootPath));
        List<Path> javaFiles = Files.walk(rootPath)
                .filter(path -> Files.isRegularFile(path) && path.toFile().getName().endsWith(JAVA_SUFFIX))
                .collect(Collectors.toList());
        int total = javaFiles.size();
        log.info("Performing Long Method refactoring for {} files in {}", total, rootPath);
        for (int i = 0; i < total; i++) {
            float percent = 100f * (i + 1) / total;
            log.info("Refactoring {}: {}%s", javaFiles.get(i), percent);
            try {
                refactorLonMethods(javaFiles.get(i), symbolSolver);
            } catch (Exception ex) { //NOPMD should not stop if failed to refactor a file for any reason
                log.error("Failed to refactor file {}", javaFiles.get(i), ex);
            }
        }
        log.info("Completed Long Method refactoring for {}", rootPath);
    }

    private void refactorLonMethods(Path path, JavaSymbolSolver symbolSolver) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        CompilationUnit compilationUnit = getCompilationUnit(symbolSolver, content);
        log.debug("Initial Methods: {}", countMethods(compilationUnit));
        boolean refactored = false;
        int round = 1;
        while (refactorLongMethod(compilationUnit)) {
            //re-init positions, set start and end lines to new added and modified methods
            compilationUnit = getCompilationUnit(symbolSolver, compilationUnit.toString());
            refactored = true;
            log.debug("Methods after round {} : {}", round, countMethods(compilationUnit));
            round++;
        }
        if (refactored) {
            Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
        }

    }

    private static int countMethods(Node node) {
        return node.findAll(MethodDeclaration.class).size();
    }

    private static CompilationUnit getCompilationUnit(JavaSymbolSolver symbolSolver, String content) {
        CompilationUnit compilationUnit = JavaParser.parse(content);
        compilationUnit.setData(Node.SYMBOL_RESOLVER_KEY, symbolSolver);
        return compilationUnit;
    }

    private boolean refactorLongMethod(CompilationUnit compilationUnit) {
        return compilationUnit.getTypes().stream().filter(type -> type instanceof ClassOrInterfaceDeclaration)
                .anyMatch(type -> refactorLongMethod(type.asClassOrInterfaceDeclaration()));
    }

    private boolean refactorLongMethod(ClassOrInterfaceDeclaration type) {
        return type.getMethods().stream().anyMatch(method -> refactorLongMethod(type, method));
    }

    private boolean refactorLongMethod(ClassOrInterfaceDeclaration type, MethodDeclaration method) {
        Optional<Position> begin = method.getBegin();
        Optional<Position> end = method.getEnd();
        if (!begin.isPresent() || !end.isPresent()) {
            return false;
        }
        int length = end.get().line - begin.get().line + 1;
        if (length <= maxLength) {
            return false;
        }
        List<RefactoringCandidate> candidates = method.getBody()
                .map(body -> candidatesProvider.refactorLongStatement(body,
                        Collections.emptyList(), Collections.emptyList())).orElse(Collections.emptyList());
        Optional<ApplicableCandidate> bestRefactoring = applicableCandidateProvider
                .chooseBestCandidate(candidates, method, type);
        if (bestRefactoring.isPresent()) {
            applyRefactoring(bestRefactoring.get(), type, method);
            return true;
        } else {
            return false;
        }
    }

    private static void applyRefactoring(ApplicableCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        type.addMember(candidate.getCandidateMethod());
        method.replace(candidate.getRemainingMethod());
    }

}
