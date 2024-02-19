.PHONY: start-localstack setup-queues stop-and-recreate-smppsim run-application

LOCALSTACK_CONTAINER_NAME=localstack_main
LOCALSTACK_IMAGE=localstack/localstack
SMPPSIM_CONTAINER_NAME=smppsim
SMPPSIM_IMAGE=smppsim:latest
AWS_ENDPOINT_URL=http://localhost:4566
QUEUE_NAMES=send-sms-queue received-sms-queue unsupported-pdu-queue delivery-receipt-queue

# Default target to run all steps
all: start-localstack setup-queues stop-and-recreate-smppsim run-application

# Start LocalStack container if not running
start-localstack:
	@if [ -z $$(docker ps --filter "name=$(LOCALSTACK_CONTAINER_NAME)" --filter "status=running" -q) ]; then \
		echo "Starting LocalStack container with port bindings..."; \
		docker run -d --name $(LOCALSTACK_CONTAINER_NAME) \
			-p 4566:4566 \
			-p 4571:4571 \
			$(LOCALSTACK_IMAGE); \
	else \
		echo "LocalStack container is already running."; \
	fi

# Setup queues
setup-queues:
	@export AWS_PAGER="";
	@for queue_name in $(QUEUE_NAMES); do \
		queue_url=$$(aws --endpoint-url=$(AWS_ENDPOINT_URL) sqs get-queue-url --queue-name $$queue_name --output text --query 'QueueUrl' 2>/dev/null); \
		if [ ! -z "$$queue_url" ]; then \
			echo "Purging queue: $$queue_name"; \
			aws --endpoint-url=$(AWS_ENDPOINT_URL) sqs purge-queue --queue-url $$queue_url; \
		else \
			echo "Creating queue: $$queue_name"; \
			aws --endpoint-url=$(AWS_ENDPOINT_URL) sqs create-queue --queue-name $$queue_name; \
		fi; \
	done

# Stop and recreate the smppsim container
stop-and-recreate-smppsim:
	@echo "Stopping and recreating $(SMPPSIM_CONTAINER_NAME) container..."
	@docker stop $(SMPPSIM_CONTAINER_NAME) || true
	@docker rm $(SMPPSIM_CONTAINER_NAME) || true
	@docker run --name $(SMPPSIM_CONTAINER_NAME) $(SMPPSIM_IMAGE)

# Override the AWS default endpoint for the Spring application and run Maven clean then run the Quarkus application
run-application:
	@export AWS_ENDPOINT_URL=$(AWS_ENDPOINT_URL); \
    mvn clean spring-boot:run

