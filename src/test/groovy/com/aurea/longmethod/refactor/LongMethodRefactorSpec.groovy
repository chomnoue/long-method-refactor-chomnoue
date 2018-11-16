package com.aurea.longmethod.refactor

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files

import static org.assertj.core.api.Assertions.assertThat

abstract class LongMethodRefactorSpec extends Specification {
    @Rule
    TemporaryFolder folder = new TemporaryFolder()

    String onClassCodeExpect(String code, String expectedTest) {
        File sourceDir = folder.newFolder("test_src")
        File sourceFile = createTestedCode(code, sourceDir)

        LongMethodRefactor longMethodRefactor = longMethodRefactor(sourceDir.getAbsolutePath())
        longMethodRefactor.refactorLongMethods()

        String resultingTest = sourceFile.text

        assertThat(resultingTest).isEqualToNormalizingWhitespace(expectedTest)
    }

    private static File createTestedCode(String code, File dir) {
        CompilationUnit compilationUnit = JavaParser.parse(code)
        String packageName = compilationUnit.getPackageDeclaration().map { it.getNameAsString() }.orElse("")
        String filePath = packageName.replaceAll("\\.", File.separator) + File.separator + compilationUnit.getType(0)
                .getNameAsString() + ".java"

        File testFile = new File(dir, filePath)
        Files.createDirectories(testFile.getParentFile().toPath())
        testFile.write(compilationUnit.toString())
        return testFile
    }

    static LongMethodRefactor longMethodRefactorWithLength(String srcDir, int maxLength) {
        RefactoringCandidatesProvider candidatesProvider = new RefactoringCandidatesProvider(3)
        ApplicableCandidateProvider applicableCandidateProvider = new ApplicableCandidateProvider(3, 0.1, 6)
        return new LongMethodRefactor(maxLength, srcDir, candidatesProvider, applicableCandidateProvider)
    }

    abstract LongMethodRefactor longMethodRefactor(String srcDir)
}
