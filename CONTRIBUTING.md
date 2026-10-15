# Contributing to SHBS

We welcome contributions! Please follow these steps:

1. Fork the repository and create a new branch.
2. Ensure you have Docker, Java 17, and Maven installed.
3. Write unit tests for your changes.
4. Verify your changes do not break existing functionality:
   ```bash
   cd backend
   ./mvnw test
   ```
5. Submit a pull request with a detailed description of your changes.

## Code Style
- Use standard Java formatting conventions.
- Prefer immutability and final fields where possible.
- Document any new public methods or significant logic changes.

## Bug Reports
Please include steps to reproduce, expected behavior, and actual behavior.
