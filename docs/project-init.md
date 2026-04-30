# Argus 项目初始化文档

## 技术栈

| 项目 | 版本 |
|------|------|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Spring Framework | 7.x（随 Spring Boot 4.0.6 引入） |
| Maven | 3.9.14（wrapper） |
| Lombok | 随 Spring Boot parent POM 管理 |

## 引入的依赖

### spring-boot-starter-webmvc（编译期）

Spring Boot Web MVC 启动器，提供构建 RESTful Web 应用所需的核心能力：

- **Spring MVC**：控制器、请求映射、参数解析、返回值处理
- **嵌入式 Tomcat**：内嵌 Servlet 容器，无需外部部署
- **Jackson**：JSON 序列化/反序列化，Java 对象与 JSON 自动互转
- **Validation**：请求参数校验（配合 `@Valid` / `@Validated`）

### lombok（编译期，optional）

通过注解消除 Java 样板代码。`@Data`、`@Getter`、`@Builder` 等在编译时自动生成代码，不依赖运行时反射。

本项目实际未使用 Lombok 注解，而是用 Java 21 原生 `record` 替代。

### spring-boot-starter-webmvc-test（测试期）

提供 `@SpringBootTest`、`MockMvc`、`Mockito` 等测试支持。

---

## Record 语法详解

### 定义

`record` 是 Java 14 引入的预览特性，Java 16 正式发布的一种**不可变数据载体**。

```java
// 一行定义，编译器自动生成构造函数、getter、equals、hashCode、toString
public record ApiResponse<T>(boolean success, T data, String message) {}
```

定义中的 `(boolean success, T data, String message)` 称为 **record 组件（components）**，每个组件自动对应一个 `private final` 字段和同名公开访问器。

### 编译器自动生成的内容

以上述 `ApiResponse` 为例，编译器自动生成：

```java
// 1. 全参规范构造器（canonical constructor）
public ApiResponse(boolean success, T data, String message) { ... }

// 2. 每个组件的访问器方法（方法名 = 组件名，无 get 前缀）
public boolean success() { return this.success; }
public T data()        { return this.data; }
public String message(){ return this.message; }

// 3. equals / hashCode / toString
@Override public boolean equals(Object o) { ... }
@Override public int hashCode() { ... }
@Override public String toString() { ... }
```

### 自定义方法

```java
// 静态工厂方法
public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
}

// 实例方法
public boolean isSuccess() {
    return success;
}
```

---

## 什么时候用 Record

| 场景 | 适合 | 原因 |
|------|------|------|
| 数据传输对象（DTO） | 适合 | 数据从 A 传到 B，不应被中途修改 |
| API 响应体 / 请求体 | 适合 | 不可变性保证数据一致性 |
| 方法返回值（多字段） | 适合 | 临时组合数据，无需定义完整类 |
| 配置/常量载体 | 适合 | 创建后不应变更 |
| Map 的复合 Key | 适合 | 依赖 `equals`/`hashCode` 的正确实现 |
| JPA Entity | **不适合** | 需要无参构造、setter、懒加载代理 |
| 需要继承的类 | **不适合** | record 隐式为 final，不能继承 |
| 字段需要修改的对象 | **不适合** | 所有字段为 final |
| Bean 风格对象（getXxx/setXxx） | **不适合** | 访问器无 `get` 前缀，无 setter |

---

## Record vs 传统方式对比

### 方案 1：手写 JavaBean（最原始）

```java
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final String message;

    public ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getMessage() { return message; }

    @Override public boolean equals(Object o) { /* 15+ 行 */ }
    @Override public int hashCode() { /* 5+ 行 */ }
    @Override public String toString() { /* 3+ 行 */ }
}
```

> 约 30 行，重复劳动，每新增一个字段需同步改多处。

### 方案 2：Lombok @Data

```java
@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
}
```

| 优点 | 缺点 |
|------|------|
| 代码量少 | 生成的是 setter 而非不可变对象 |
| 不需要安装额外 IDE 插件 | 字段可变，DTO 中途可能被意外修改 |

### 方案 3：Lombok @Value

```java
@Value
public class ApiResponse<T> {
    boolean success;
    T data;
    String message;
}
```

> `@Value` 生成不可变类：字段 private final、无 setter、全参构造、equals/hashCode/toString。与 record 最接近的 Lombok 方案。

### 方案 4：Java Record（本项目采用）

```java
public record ApiResponse<T>(boolean success, T data, String message) {}
```

| 维度 | Lombok @Value | Java Record |
|------|--------------|-------------|
| 依赖 | 需要 lombok 包 + IDE 插件 | 零依赖，语言原生 |
| 编译 | 依赖注解处理器 | 直接编译，无额外处理 |
| 访问器命名 | `getXxx()` | `xxx()` |
| 继承 | 可 extends 其他类 | 隐式 final，不可继承 |
| 反射/序列化 | 类中可见生成的方法 | 标准 Java API 可获取 record 组件元数据 |
| 团队协作 | 新人需安装 Lombok 插件 | 无需任何额外配置 |

### 核心优势总结

1. **零依赖**：语言级特性，不需要任何三方库或 IDE 插件
2. **不可变性保证**：所有字段 `final`，创建后不可修改，线程安全
3. **语义清晰**：看到 `record` 关键字就知道这是数据载体，不包含业务逻辑
4. **减少出错**：不需要手动维护 `equals`/`hashCode`/`toString` 与字段的一致性
