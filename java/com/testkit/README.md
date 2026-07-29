# test-kit tái sử dụng

Copy nguyên folder `com/testkit` này (3 file .java) sang `src/test/java/com/testkit/`
của bất kỳ project Java nào để dùng lại pattern test đã áp dụng ở myShop.

## Điều kiện
Project đích cần có trong `pom.xml`:
```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.2</version>
    <scope>test</scope>
</dependency>
```
và `maven-surefire-plugin` (để `mvn test` nhận diện JUnit 5).

## Đi kèm test-kit này, các package sau ở myShop cũng portable 100%,
## copy luôn cả package + test tương ứng sang project mới không cần sửa gì:
- `com.validation` (Rules, FormValidator, ValidationRule) — dùng với `ValidationRuleAssertions`
- `com.permission` (Permission, PermissionSet, PermissionManager) — dùng với `PermissionTestFixtures`
- `com.security.CryptoUtil` — chỉ cần copy 1 file, không phụ thuộc gì khác

## Dùng SingletonTestSupport
Bất kỳ project Swing/Java nào có singleton mang state (giỏ hàng, session,
config cache...) đều nên reset ở cả `@BeforeEach` và `@AfterEach`, vì
singleton sống suốt JVM và các test class khác nhau đều dùng chung 1 instance:

```java
@BeforeEach
void reset() {
    SingletonTestSupport.resetAll(CartService.getInstance()::clear);
}

@AfterEach
void tearDown() {
    SingletonTestSupport.resetAll(CartService.getInstance()::clear);
}
```