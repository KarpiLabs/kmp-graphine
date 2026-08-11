.DEFAULT_GOAL := help

GRADLEW := ./gradlew

.PHONY: help build clean test check lint format sample

help: ## Show available targets
	@printf "Available targets:\n"
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*##"}; {printf "  %-12s %s\n", $$1, $$2}'

build: ## Build the project
	@$(GRADLEW) build

check: ## Run verification tasks
	@$(GRADLEW) check

lint: ## Run static checks (Spotless + Detekt)
	@$(GRADLEW) :graphine:spotlessCheck :graphine:detekt

format: ## Format source code (Spotless)
	@$(GRADLEW) :graphine:spotlessApply

test: ## Run tests
	@$(GRADLEW) test

sample: ## Run the sample JVM application
	@$(GRADLEW) :sample:run

clean: ## Clean build outputs
	@$(GRADLEW) clean