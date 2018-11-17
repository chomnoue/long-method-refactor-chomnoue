# long-method-refactor
## Description
 Automatically detects long Java methods and suggest a refactoring for long methods by splitting the method into 
 multiple small methods without affecting the overall logic.
 Using the technique suggested in [this paper](https://www.cqse.eu/publications/2016-deriving-extract-method-refactoring-suggestions-for-long-methods.pdf) 
 to choose the best refactoring candidates.
 
 Please check this [tests file](https://github.com/chomnoue/long-method-refactor-chomnoue/blob/master/src/test/groovy/com/aurea/longmethod/refactor/LongMethodRefactorLenghtThirtySpec.groovy) for an example of long method and refactored output.

## Usage
### Build the tool
```
./gradlew clean build
```

### Run it
```
java -jar build/libs/long-method-refactor-0.0.1-SNAPSHOT.jar --srcPaths=<path_to_the_java_source_root>
```

For example

```
java -jar build/libs/long-method-refactor-0.0.1-SNAPSHOT.jar --srcPaths=/home/chomnoue/projects/bootcamp/okhttp/okhttp/src/main/java
```

#### Supported parameters
| Parameter        | Description   | Required   | Default value   |
| :-------------: |:-------------:|:-------------:|:-------------:|
| srcPaths      | Comma separated list of paths to Java source dirs | Yes | |
| maxLength      | Maximum length of a method, over which refactoring is considered      |No|30|
| minStatements | Minimum number of statements considered to be extracted to another method|No|3|
|minMethodLength| Minimum length of the refactored methods, to avoid too small methods |No|6|
|maxScoreLength| MAXscoreLength parameter defined in this [paper](https://www.cqse.eu/publications/2016-deriving-extract-method-refactoring-suggestions-for-long-methods.pdf)|No|3|
|lengthWeight| cl parameter defined in this [paper](https://www.cqse.eu/publications/2016-deriving-extract-method-refactoring-suggestions-for-long-methods.pdf)|No|0.1|

## Project status
Tested on :

| Project        | Files refactored   | Zip before   | Zip after   |
| :-------------: |:-------------:|:-------------:|:-------------:|
|[conditional-return reference project](https://github.com/chomnoue/bootcamp-conditional-return-reference-chomnoue)|1|[before](https://drive.google.com/open?id=16Bi-CzYlkf2c99h8JsQ7pwetfyDZAtwp)|[after](https://drive.google.com/open?id=1SyReO6Bx0X4cnbvuH_sVK248gaYoalpm)|
|[okhttp](https://github.com/square/okhttp)|33|[before](https://drive.google.com/open?id=11zZBj_Tj6t511FlU3C9GPWo5s11cF9iN)|[after](https://drive.google.com/open?id=1Q7xrkhqOMg0FmixsIdNrOo8wvs1S1KZS)|


Both projects compiled and existing tests passed after refactoring.

Please check this [demo](https://drive.google.com/file/d/1-nU_76ryy8iqLlSDY2VPeCwYHh8AgagU/view?usp=sharing) for more details

Still need to test on more projects/modules and make sure we don't break compilation or logic.

## Known limitations

* Does not refactor code containing variable that is not assigned upon declaration
* Does not refactor try-catch body, will require more time to handle catched exceptions properly
* Formatting issues: re-written java files might not match the original format of the source code, making it 
difficult to create a PR as the entire file will be reformatted.
* Extracted methods throw same exception as original ones, as for now it is hard to gues which part of the method 
actually throws the exception.
* Some generated methods declare useless variables, just to use them as parameter in consecutive method call

## Suggestions for future
 * Add maven and/or gradle dependency resolution, so that we can resolve the methods called in extracted blocks and 
 also resolve thrown exceptions. This will help fix the issue with try-catch and also make extracted method throw 
 specific exceptions
 * Most of the limitations listed above could be mitigated if we developped this tool as an IDE (Eclipes or Intellij)
  plugin. This is because the IDEs already havve solid refactoring features that we can leverage.
