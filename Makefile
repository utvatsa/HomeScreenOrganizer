.PHONY: bootstrap doctor discover test lint

VENV := .venv/bin
PYTHON := $(VENV)/python
CLI := $(VENV)/home-studio

bootstrap:
	./scripts/bootstrap.sh

doctor:
	$(CLI) doctor

discover:
	$(CLI) discover

test:
	$(VENV)/pytest -q

apk:
	cd android/home-studio && ./gradlew assembleDebug
