# 增量注入构建

不跑 Gradle 全量构建：只用 javac 编译改动/新增的类，按需做 SRG 重映射，再把它们覆盖进 base jar，其余条目原样保留。

## 用法

```bash
# 1. 编译改动类（MCP 命名，classpath 指向 base jar）
javac -proc:none -encoding UTF-8 --release 21 \
  -cp build/libs/Unfair-MCP-1.0.0.jar \
  -d .harness/inj/classes \
  src/main/java/cn/unfair/module/modules/combat/velocity/B4Mode.java \
  src/main/java/cn/unfair/module/modules/combat/BackTrack.java \
  src/main/java/cn/unfair/module/modules/combat/Velocity.java \
  src/main/java/cn/unfair/module/modules/combat/AimAssist.java \
  src/main/java/cn/unfair/management/DelayManager.java

# 2. 注入（第 3 个参数传 - 表示不重映射）
java -cp tools/inject/asm.jar:tools/inject Inject \
  build/libs/Unfair-MCP-1.0.0.jar \
  .harness/inj/Unfair-injected.jar \
  - \
  .harness/inj/classes
```

输出形如 `kept=41227 replaced=7 added=0`。

## SRG 重映射

第 3 个参数传 srg 映射文件时，编译产物先经 ASM 重映射再写入：

```
CL net/minecraft/client/Minecraft net/minecraft/client/Minecraft
FD net/minecraft/client/Minecraft/thePlayer net/minecraft/client/Minecraft/field_71439_g
MD net/minecraft/client/Minecraft/getMinecraft ()Lnet/minecraft/client/Minecraft; func_71410_x ()Lnet/minecraft/client/Minecraft;
```

字段/方法名按 base jar 的继承链回溯解析，所以 `EntityPlayer` 上调用 `Entity 声明的成员` 也能映射。
对 MCP 命名的 jar（例如 Unfair 客户端自己的 jar）不需要映射，直接传 `-`。

## 依赖

`asm.jar` 是 ASM 9.10.1，取自 FCL 的 `libraries/org/ow2/asm/asm/9.10.1/`。
旧的 `asm-all-5.2.jar` 读不了 Java 21+ 的 class 文件（major 65）。
