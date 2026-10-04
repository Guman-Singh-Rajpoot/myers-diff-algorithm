# Myers Diff Assignment

Creative Problem Solving — Assignment 1

## Java

Required source: `src/Main.java`

Compile:

```powershell
javac -d out src\Main.java
```

Part A:

```powershell
java -cp out Main lines samples\old.txt samples\new.txt
```

Part B:

```powershell
java -cp out Main highlight samples\config_old.txt samples\config_new.txt
```

CPS tests:

```powershell
cpsdiff test --part A
cpsdiff test --part B
cpsdiff test
```

Do not submit until the public tests pass.
