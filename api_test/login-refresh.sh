#!/bin/bash

API_URL="https://memos.nannoda.com/api/v1/auth/refresh"
COOKIE_DATA_FILE="cookie_value.txt"

echo "--- 2. Attempting Token Refresh ---"

if [ ! -f "$COOKIE_DATA_FILE" ]; then
    echo "Error: $COOKIE_DATA_FILE not found. Run login script first."
    exit 1
fi

# Read the stored cookie value
COOKIE_VALUE=$(cat "$COOKIE_DATA_FILE")

# Inject it manually using -H "Cookie: ..."
curl -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -H "Cookie: $COOKIE_VALUE" \
  -H "Grpc-Metadata-Cookie: $COOKIE_VALUE" \
  -d '{}' 

echo -e "\n\n--- Refresh Complete ---"