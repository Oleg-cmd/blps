#!/bin/bash

# --- Configuration ---
SERVER_PORT="8080"
SENDER_PHONE_NUMBER="9991234567"
RECIPIENT_PHONE_NUMBER="9997654321"
AMOUNT="100.00"
HIGH_AMOUNT="1000000.00"
EXCEED_DAILY_LIMIT="150001.00"
BANK_ID="100000000001"
WRONG_CONFIRMATION_CODE="000000"
OTHER_PHONE_NUMBER="9991112233"

# --- Helper functions ---
post_json() {
    local url="$1"
    local phone_number="$2"
    local data="$3"
    curl -s -X POST "$url" \
        -H "Content-Type: application/json" \
        -H "X-Phone-Number: $phone_number" \
        -d "$data"
}

get_json() {
    local url="$1"
    local phone_number="$2"
    curl -v -X GET "$url" \
        -H "X-Phone-Number: $phone_number"
}

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo "Error: jq is required but not installed. Please install jq first."
    echo "On macOS, you can install it with: brew install jq"
    exit 1
fi

echo "Starting SBP Transfer Service tests..."

# --- 1. Initiate Transfer ---
echo -e "\n1. Testing transfer initiation..."
initiate_response=$(post_json \
    "http://localhost:${SERVER_PORT}/api/transfers" \
    "${SENDER_PHONE_NUMBER}" \
    "{\"recipientPhoneNumber\": \"${RECIPIENT_PHONE_NUMBER}\", \"amount\": ${AMOUNT}, \"bankId\": \"${BANK_ID}\"}")

echo "Initiation response: $initiate_response"

TRANSFER_ID=$(echo "$initiate_response" | jq -r '.transferId')
if [ "$TRANSFER_ID" = "null" ] || [ -z "$TRANSFER_ID" ]; then
    echo "Error: Failed to get transfer ID"
    exit 1
fi

echo "Transfer ID: $TRANSFER_ID"

# --- 2. Confirm Transfer ---
echo -e "\n2. Testing transfer confirmation..."
# Show the confirmation code from logs
echo "Please check the application logs for the confirmation code"
echo "It will be logged as: Confirmation code XXXXXX sent to $SENDER_PHONE_NUMBER"
read -p "Enter the confirmation code from logs: " CONFIRMATION_CODE

confirm_response=$(post_json \
    "http://localhost:${SERVER_PORT}/api/transfers/${TRANSFER_ID}/confirm" \
    "${SENDER_PHONE_NUMBER}" \
    "{\"confirmationCode\": \"${CONFIRMATION_CODE}\"}")

echo "Confirmation response: $confirm_response"

CONFIRM_STATUS=$(echo "$confirm_response" | jq -r '.status')
if [ "$CONFIRM_STATUS" != "SUCCESSFUL" ]; then
    echo "Error: Transfer confirmation failed. Status: $CONFIRM_STATUS"
    exit 1
fi

# --- 3. Check Transfer Status ---
echo -e "\n3. Testing transfer status check..."
status_response=$(get_json \
    "http://localhost:${SERVER_PORT}/api/transfers/${TRANSFER_ID}" \
    "${SENDER_PHONE_NUMBER}")

echo "Status response: $status_response"

STATUS=$(echo "$status_response" | jq -r '.status')
if [ "$STATUS" != "SUCCESSFUL" ]; then
    echo "Error: Unexpected transfer status: $STATUS"
    exit 1
fi

# --- 4. Test Error Scenarios ---
echo -e "\n4. Testing error scenarios..."

# 4.1 Insufficient funds
echo "4.1 Testing insufficient funds..."
insufficient_funds_response=$(post_json \
    "http://localhost:${SERVER_PORT}/api/transfers" \
    "${SENDER_PHONE_NUMBER}" \
    "{\"recipientPhoneNumber\": \"${RECIPIENT_PHONE_NUMBER}\", \"amount\": ${HIGH_AMOUNT}, \"bankId\": \"${BANK_ID}\"}")
echo "Insufficient funds response: $insufficient_funds_response"

# 4.2 Wrong confirmation code
echo "4.2 Testing wrong confirmation code..."
wrong_code_response=$(post_json \
    "http://localhost:${SERVER_PORT}/api/transfers/${TRANSFER_ID}/confirm" \
    "${SENDER_PHONE_NUMBER}" \
    "{\"confirmationCode\": \"${WRONG_CONFIRMATION_CODE}\"}")
echo "Wrong code response: $wrong_code_response"

# 4.3 Daily limit exceeded
echo "4.3 Testing daily limit exceeded..."
limit_exceeded_response=$(post_json \
    "http://localhost:${SERVER_PORT}/api/transfers" \
    "${SENDER_PHONE_NUMBER}" \
    "{\"recipientPhoneNumber\": \"${RECIPIENT_PHONE_NUMBER}\", \"amount\": ${EXCEED_DAILY_LIMIT}, \"bankId\": \"${BANK_ID}\"}")
echo "Limit exceeded response: $limit_exceeded_response"

# 4.4 Unauthorized access
echo "4.4 Testing unauthorized access..."
unauthorized_response=$(get_json \
    "http://localhost:${SERVER_PORT}/api/transfers/${TRANSFER_ID}" \
    "${OTHER_PHONE_NUMBER}")
echo "Unauthorized access response: $unauthorized_response"

echo -e "\nTest execution completed."