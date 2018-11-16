package com.aurea.longmethod.refactor


class LongMethodRefactorLenghtThirtySpec extends LongMethodRefactorSpec{

    def "Should refactor long method with length > 10"(){
        expect:
        onClassCodeExpect """
        package com.aurea.testgenerator.conditionalreturn.reference.assignment3;
        
        public class NestedLongMethod {
        
            private boolean booleanField;
        
            public String deeplyNestedLongMethod(boolean cond1, boolean cond2, boolean cond3, boolean cond4,
                    boolean cond5, boolean cond6, boolean cond7) {
                if (cond1) {
                    if (cond2) {
                        System.out.printf("Cond1: %b, Cond2: %b\\n", cond1, cond2);
                        System.out.println("coud 2 met 1");
                        System.out.println("coud 2 met 2");
                        System.out.println("coud 2 met 3");
                        System.out.println("coud 2 met 4");
                        System.out.println("coud 2 met 5");
                        System.out.println("coud 2 met 6");
                        System.out.println("coud 2 met 7");
                        System.out.println("coud 2 met 8");
                        System.out.println("coud 2 met 9");
                        System.out.println("coud 2 met 10");
                        return "coud 2 met";
                    }
                    return "cond 2 not met";
                } else if (cond3) {
                    if (cond4) {
                        if (cond5) {
                            System.out.printf("Cond3: %b, Cond4: %b, Cond5: %b\\n", cond3, cond4, cond5);
                            System.out.println("cond 5 met 1");
                            System.out.println("cond 5 met 2");
                            System.out.println("cond 5 met 3");
                            System.out.println("cond 5 met 4");
                            System.out.println("cond 5 met 5");
                            System.out.println("cond 5 met 6");
                            System.out.println("cond 5 met 7");
                            System.out.println("cond 5 met 8");
                            return "cond 5 met";
                        }
                        System.out.println("cond 5 not met, not returning");
                        System.out.println("cond 5 not met, not returning 1");
                        System.out.println("cond 5 not met, not returning 2");
                        System.out.println("cond 5 not met, not returning 3");
                        System.out.println("cond 5 not met, not returning 4");
                        System.out.println("cond 5 not met, not returning 5");
                    }
                    if (cond6) {
                        System.out.printf("Cond6: %b\\n", cond6);
                        System.out.println("cond 6 met");
                        System.out.println("cond 6 met 1");
                        return "cond 6 met";
                    } else {
                        if (cond7) {
                            System.out.println("cond 7 met, not returning");
                            System.out.println("cond 7 met, not returning 1");
                            System.out.println("cond 7 met, not returning 2");
                            System.out.println("cond 7 met, not returning 3");
                            System.out.println("cond 7 met, not returning 4");
                            System.out.println("cond 7 met, not returning 5");
                            System.out.println("cond 7 met, not returning 6");
                        } else {
                            System.out.printf("Cond7: %b\\n", cond7);
                            System.out.println("Cond 7 not met");
                            System.out.println("Cond 7 not met 1");
                            System.out.println("Cond 7 not met 2");
                            System.out.println("Cond 7 not met 3");
                            System.out.println("Cond 7 not met 4");
                            System.out.println("Cond 7 not met 5");
                            System.out.println("Cond 7 not met 7");
                            System.out.println("Cond 7 not met 8");
                            System.out.println("Cond 7 not met 9");
                            return "Cond 7 not met";
                        }
                    }
                }
        
                if (this.booleanField) {
                    System.out.printf("boolean field: %b\\n", booleanField);
                    System.out.println("boolean field met");
                    System.out.println("boolean field met 1");
                    System.out.println("boolean field met 2");
                    System.out.println("boolean field met 3");
                    System.out.println("boolean field met 4");
                    System.out.println("boolean field met 5");
                    System.out.println("boolean field met 6");
                    return "boolean field met";
                }
        
                System.out.println("Reached the end");
                System.out.println("Reached the end 1");
                System.out.println("Reached the end 2");
                System.out.println("Reached the end 3");
                return "Reached the end";
            }
        
            public void setBooleanField(boolean booleanField) {
                this.booleanField = booleanField;
            }
        }
        """, """
        package com.aurea.testgenerator.conditionalreturn.reference.assignment3;
        
        public class NestedLongMethod {
        
            private boolean booleanField;
        
            public String deeplyNestedLongMethod(boolean cond1, boolean cond2, boolean cond3, boolean cond4, boolean cond5, boolean cond6, boolean cond7) {
                if (cond1) {
                    if (cond2) {
                        return deeplyNestedLongMethod10(cond2, cond1);
                    }
                    return "cond 2 not met";
                } else if (cond3) {
                    if (cond4) {
                        if (cond5) {
                            return deeplyNestedLongMethod11(cond5, cond4, cond3);
                        }
                        deeplyNestedLongMethod6();
                    }
                    if (cond6) {
                        deeplyNestedLongMethod8(cond6);
                        return "cond 6 met";
                    } else {
                        if (cond7) {
                            deeplyNestedLongMethod5();
                        } else {
                            return deeplyNestedLongMethod9(cond7);
                        }
                    }
                }
                if (this.booleanField) {
                    deeplyNestedLongMethod4();
                    return "boolean field met";
                }
                deeplyNestedLongMethod7();
                return "Reached the end";
            }
        
            public void setBooleanField(boolean booleanField) {
                this.booleanField = booleanField;
            }
        
            private void deeplyNestedLongMethod1() {
                System.out.println("coud 2 met 1");
                System.out.println("coud 2 met 2");
                System.out.println("coud 2 met 3");
                System.out.println("coud 2 met 4");
                System.out.println("coud 2 met 5");
                System.out.println("coud 2 met 6");
                System.out.println("coud 2 met 7");
                System.out.println("coud 2 met 8");
                System.out.println("coud 2 met 9");
                System.out.println("coud 2 met 10");
            }
        
            private void deeplyNestedLongMethod2() {
                System.out.println("Cond 7 not met");
                System.out.println("Cond 7 not met 1");
                System.out.println("Cond 7 not met 2");
                System.out.println("Cond 7 not met 3");
                System.out.println("Cond 7 not met 4");
                System.out.println("Cond 7 not met 5");
                System.out.println("Cond 7 not met 7");
                System.out.println("Cond 7 not met 8");
                System.out.println("Cond 7 not met 9");
            }
        
            private void deeplyNestedLongMethod3() {
                System.out.println("cond 5 met 1");
                System.out.println("cond 5 met 2");
                System.out.println("cond 5 met 3");
                System.out.println("cond 5 met 4");
                System.out.println("cond 5 met 5");
                System.out.println("cond 5 met 6");
                System.out.println("cond 5 met 7");
                System.out.println("cond 5 met 8");
            }
        
            private void deeplyNestedLongMethod4() {
                System.out.printf("boolean field: %b\\n", booleanField);
                System.out.println("boolean field met");
                System.out.println("boolean field met 1");
                System.out.println("boolean field met 2");
                System.out.println("boolean field met 3");
                System.out.println("boolean field met 4");
                System.out.println("boolean field met 5");
                System.out.println("boolean field met 6");
            }
        
            private void deeplyNestedLongMethod5() {
                System.out.println("cond 7 met, not returning");
                System.out.println("cond 7 met, not returning 1");
                System.out.println("cond 7 met, not returning 2");
                System.out.println("cond 7 met, not returning 3");
                System.out.println("cond 7 met, not returning 4");
                System.out.println("cond 7 met, not returning 5");
                System.out.println("cond 7 met, not returning 6");
            }
        
            private void deeplyNestedLongMethod6() {
                System.out.println("cond 5 not met, not returning");
                System.out.println("cond 5 not met, not returning 1");
                System.out.println("cond 5 not met, not returning 2");
                System.out.println("cond 5 not met, not returning 3");
                System.out.println("cond 5 not met, not returning 4");
                System.out.println("cond 5 not met, not returning 5");
            }
        
            private void deeplyNestedLongMethod7() {
                System.out.println("Reached the end");
                System.out.println("Reached the end 1");
                System.out.println("Reached the end 2");
                System.out.println("Reached the end 3");
            }
        
            private void deeplyNestedLongMethod8(boolean cond6) {
                System.out.printf("Cond6: %b\\n", cond6);
                System.out.println("cond 6 met");
                System.out.println("cond 6 met 1");
            }
        
            private String deeplyNestedLongMethod9(boolean cond7) {
                System.out.printf("Cond7: %b\\n", cond7);
                deeplyNestedLongMethod2();
                return "Cond 7 not met";
            }
        
            private String deeplyNestedLongMethod10(boolean cond2, boolean cond1) {
                System.out.printf("Cond1: %b, Cond2: %b\\n", cond1, cond2);
                deeplyNestedLongMethod1();
                return "coud 2 met";
            }
        
            private String deeplyNestedLongMethod11(boolean cond5, boolean cond4, boolean cond3) {
                System.out.printf("Cond3: %b, Cond4: %b, Cond5: %b\\n", cond3, cond4, cond5);
                deeplyNestedLongMethod3();
                return "cond 5 met";
            }
        }
        """
    }

    @Override
    LongMethodRefactor longMethodRefactor(String srcDir) {
        return longMethodRefactorWithLength(srcDir, 30)
    }
}
