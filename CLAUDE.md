# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Set JDK 21 (required — record syntax, virtual threads)
export JAVA_HOME="D:\Develop\DevelopTool\StudyEnvironment\PhpWebStudy-Data\app\openjdk-21.0.9"

# Compile
cd Argus-backend && ./mvnw clean compile

# Run tests
./mvnw test

# Run single test
./mvnw test -Dtest=ClassName#methodName

# Package
./mvnw clean package -DskipTests

# Run app (local profile by default)
./mvnw spring-boot:run
```

## Tech Stack

- **Java 21** with Spring Boot 3.5.0, Spring MVC (no WebFlux)
- **MyBatis-Plus 3.5.15** (not JdbcTemplate) for all DB access
- **PostgreSQL** as the database (driver already in pom)
- **Maven** with wrapper (`mvnw`)
- **Lombok 1.18.34** (available but currently unused — entity getters/setters are hand-written)
- **JJWT 0.12.6** for JWT access tokens
- **Spring Security Crypto** (BCrypt) for password hashing
- **Knife4j 4.5.0** for API documentation

## Architecture

```
com.argus.rag
├── ArgusBackendApplication        # @SpringBootApplication entry point
├── common/                        # Shared infrastructure
│   ├── api/ApiResponse            # Unified response record: {success, data, message}
│   ├── enums/                     # SystemRole, UserStatus, GroupRole, etc.
│   └── exception/                 # BusinessException(400), ForbiddenException(403),
│                                  # UnauthorizedException(401), GlobalExceptionHandler
├── auth/                          # Authentication & authorization
│   ├── config/                    # AuthProperties (@ConfigurationProperties prefix: ddrag.auth),
│   │                              # AuthConfiguration (PasswordHasher bean, Clock bean),
│   │                              # DevAdminInitializer (@Profile("dev") seeds admin account)
│   ├── controller/AuthController  # /api/auth/{login,register,refresh,logout,me}
│   ├── service/                   # AuthService (login/register/refresh logic),
│   │                              # PasswordHasher interface, RefreshTokenRecord
│   ├── security/                  # JwtAuthenticationFilter (OncePerRequestFilter),
│   │                              # JwtAccessTokenService (issue/parse JWT),
│   │                              # RefreshTokenService (refresh token CRUD),
│   │                              # AuthCookieSupport (refresh token cookie)
│   ├── model/entity/UserRefreshToken  # MyBatis-Plus entity → user_refresh_tokens table
│   ├── mapper/UserRefreshTokenMapper  # MyBatis-Plus BaseMapper
│   └── CurrentUserService         # Extracts authenticated user from request attribute
└── user/                          # User management
    ├── controller/                # AccountController (/api/account/change-password),
    │                              # AdminUserController (/api/admin/users CRUD)
    ├── service/                   # AccountService, AdminUserService, UserQueryService
    ├── model/entity/User          # MyBatis-Plus entity → users table
    ├── model/dto/                 # Request records (ChangePassword, UpdateUserStatus, etc.)
    ├── model/vo/                  # Response records (AdminUserItemResponse)
    └── mapper/UserMapper          # MyBatis-Plus BaseMapper + custom XML methods
```

## Key Conventions

### MyBatis-Plus Pattern
- All DB access uses `BaseMapper` + `LambdaQueryWrapper`, never `JdbcTemplate`
- Entities extend nothing but use `@TableName` / `@TableId(type = IdType.AUTO)`
- Complex queries with `FOR UPDATE` row locking go in `src/main/resources/mappers/*.xml`
- Enums use `default-enum-type-handler: org.apache.ibatis.type.EnumTypeHandler` (stored as `name()` strings)

### API Response Pattern
- Every controller method returns `ApiResponse<T>` — a `record` with `success()`, `data()`, `message()`
- Never throw raw exceptions; throw `BusinessException`/`ForbiddenException`/`UnauthorizedException` which `GlobalExceptionHandler` maps to HTTP status codes

### Auth Flow
- `JwtAuthenticationFilter` extracts Bearer token → sets `AuthenticatedUser` as request attribute
- `CurrentUserService` reads that attribute to provide `CurrentUser` record
- Controllers call `currentUserService.getRequiredCurrentUser(request)` or `requireSystemAdmin(request)`
- Refresh tokens stored in `user_refresh_tokens` table, sent as httpOnly cookie

## Configuration

- **Active profile**: `local` (default in `application.yml`)
- **Auth properties prefix**: `ddrag.auth` (maps to `AuthProperties` class)
- **MyBatis-Plus XML mappers**: `classpath*:/mappers/**/*.xml`
- **Enum handling**: Stored as VARCHAR using enum `.name()` values
