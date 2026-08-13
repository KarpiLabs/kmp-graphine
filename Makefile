.DEFAULT_GOAL := help

GRADLEW := ./gradlew

.PHONY: help build clean test check lint format sample sample-basic sample-showcase sample-graph-view sample-web sample-web-build sample-web-dev

help: ## Show available targets
	@printf "Available targets:\n"
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*##"}; {printf "  %-18s %s\n", $$1, $$2}'

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

sample: sample-basic ## Run the default sample (alias for sample-basic)

sample-basic: ## Run the basic org chart sample
	@$(GRADLEW) :sample:run

sample-showcase: ## Run the showcase sample with multiple graph types
	@$(GRADLEW) :sample-showcase:run

sample-graph-view: ## Run the Graph View sample (DOT · 1000+ nodes)
	@$(GRADLEW) :sample-graph-view:run

sample-web: sample-web-build ## Build and prepare web sample
	@echo "Web sample built successfully!"
	@echo "See: sample-web/build/distributions/ for output"

sample-web-build: ## Build the web sample (Kotlin → JS compilation)
	@$(GRADLEW) :sample-web:build -x test

sample-web-dev: ## Build web sample in development mode
	@$(GRADLEW) :sample-web:compileKotlinJs

clean: ## Clean build outputs
	@$(GRADLEW) clean
