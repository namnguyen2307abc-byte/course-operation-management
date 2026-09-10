# Course Operation Management

Base web foundation for a multi-discipline training center management system.

Course Operation business modules are not implemented yet.

## Current Scope

This project currently contains only the basic MVC foundation:

- Session-based authentication foundation
- Registration and login pages
- Basic role authorization
- Minimal user management
- User status support
- Profile and change password
- Admin user list, search, filter, details, activation, deactivation, and role change
- Thymeleaf fragments and error pages
- Focused base tests

It does not implement Program, Course, Class, Enrollment, Schedule, Attendance, Assessment, Payment, CRM, or other business modules yet.

## Tech Stack

- Java 17
- Spring Boot 4.1.1
- Maven
- Spring MVC / Spring Web MVC
- Thymeleaf
- Spring Security
- Spring Data JPA
- Spring Validation
- Microsoft SQL Server
- Lombok
- Spring Boot DevTools
- JUnit
- Mockito

## SQL Server Setup

Create a local development database:

```sql
CREATE DATABASE course_operation_management;
```

Default local connection:

```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=course_operation_management;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=123
```

You can override these values with environment variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

## Run

```bat
mvnw.cmd spring-boot:run
```

On PowerShell, if the wrapper has trouble starting, run it through `cmd`:

```bat
cmd /c mvnw.cmd spring-boot:run
```

## Test

```bat
cmd /c mvnw.cmd test
```

The tests are designed to run without requiring a live SQL Server database.

## Default Routes

- `GET /` home
- `GET /register` registration form
- `POST /register` submit registration
- `GET /login` login form
- `POST /login` Spring Security login
- `POST /logout` logout
- `GET /profile` authenticated user profile
- `GET /profile/change-password` change password form
- `POST /profile/change-password` submit password change
- `GET /admin/users` admin user list
- `GET /admin/users/{id}` admin user details
- `POST /admin/users/{id}/activate` activate user
- `POST /admin/users/{id}/deactivate` deactivate user
- `POST /admin/users/{id}/role` change user role

## First Admin Account

Registration creates normal `STUDENT` users. To create the first admin in local development, update a registered user directly in SQL Server:

```sql
UPDATE users
SET role = 'ADMIN'
WHERE email = 'your-email@example.com';
```
