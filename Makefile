# Copyright © Manetu, Inc.  All rights reserved

NAME=manetu-tokenizer-loadtest
BINDIR ?= /usr/local/bin
OUTPUT=target/$(NAME)
SHELL=/bin/bash -o pipefail
LEIN ?= ./lein

SRCS += $(shell find src -type f)

all: scan bin

bin: $(OUTPUT)

scan:
	$(LEIN) cljfmt check
	$(LEIN) bikeshed -m 120 -n false
	#$(LEIN) kibit
	$(LEIN) eastwood

$(OUTPUT): $(SRCS) Makefile project.clj
	@$(LEIN) bin

$(PREFIX)$(BINDIR):
	mkdir -p $@

install: $(OUTPUT) $(PREFIX)$(BINDIR)
	cp $(OUTPUT) $(PREFIX)$(BINDIR)

clean:
	@echo "Cleaning up.."
	@$(LEIN) clean
	-@rm -rf target
	-@rm -f *~

