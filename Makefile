.PHONY: help run test build format check-format clean

# Variables
MVNW = ./mvnw

help: ## Show this help message
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

run: ## Run the Spring Boot application
	$(MVNW) spring-boot:run

test: ## Run the unit tests
	$(MVNW) test

build: ## Clean and package the application (skipping tests)
	$(MVNW) clean package -DskipTests

build-test: ## Clean, test and package the application
	$(MVNW) clean package

format: ## Apply Spotless formatting to the code
	$(MVNW) spotless:apply

check-format: ## Check if the code is properly formatted
	$(MVNW) spotless:check

clean: ## Clean the target directory
	$(MVNW) clean
