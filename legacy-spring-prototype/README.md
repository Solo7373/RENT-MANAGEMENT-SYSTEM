# Legacy Spring Prototype (Archived)

These files are **not part of the JavaFX application** and are excluded from the Maven build.

They are Spring MVC/JPA prototypes that were copied into `src/main/java` on 2026-11-10 and caused the build to fail because this project intentionally does not include Spring Boot, Spring Data JPA or Spring Security web dependencies.

Archived contents:

- `com/rentals/config/SecurityConfig.java`
- `com/rentals/controllers/{Main,Property,Room,Tenant}Controller.java`
- `com/rentals/{properties,rooms,tenants,tenancies,users}/*Repository.java`
- `com/rentals/service/DashboardView.java` (old dashboard using removed `show(Stage)` methods)

The active application is JavaFX + SQLite:

- Entry point: `com.rentals.Launcher`
- Active dashboard: `com.rentals.ui.dashboard.DashboardView`
- Persistence: DAO classes in `com.rentals.dao`
- Build: `pom.xml` (JavaFX, SQLite JDBC, OpenPDF, BCrypt)

Keep this folder for reference only. Do not add it back to `src/main/java` unless the project is deliberately being converted to a Spring application.
