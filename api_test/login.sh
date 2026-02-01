#!/bin/bash

# Configuration
API_URL="https://memos.nannoda.com/api/v1/auth/signin"
USERNAME="yamada"
PASSWORD="llycgt4Jh7LNm23xvmV38yHktDFkCjnP"
COOKIE_FILE="cookies.txt"
HEADER_DUMP="headers.txt"
COOKIE_DATA_FILE="cookie_value.txt"

# JSON Payload
PAYLOAD=$(cat <<EOF
{
  "passwordCredentials": {
    "username": "$USERNAME",
    "password": "$PASSWORD"
  }
}
EOF
)

echo "--- 1. Attempting Login ---"

# We use -D to dump headers so we can parse the non-standard cookie later
curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  -D "$HEADER_DUMP" \
  -o login_response.json

# Check if the header dump contains the gRPC cookie
if grep -q "grpc-metadata-set-cookie" "$HEADER_DUMP"; then
    echo "Login Successful. Extracting gRPC cookie..."
    
    # 1. Grep the line
    # 2. Cut after the colon space (: )
    # 3. Cut before the first semicolon (;) to get just the key=value pair
    COOKIE_VALUE=$(grep -i "grpc-metadata-set-cookie" "$HEADER_DUMP" | sed 's/^.*: //' | cut -d';' -f1)
    
    # Save to file
    echo "$COOKIE_VALUE" > "$COOKIE_DATA_FILE"
    
    echo "Cookie saved to: $COOKIE_DATA_FILE"
    echo "Token content: $COOKIE_VALUE"
else
    echo "Login Failed or Cookie missing."
    cat "$HEADER_DUMP"
fi