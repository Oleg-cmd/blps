#!/bin/bash


echo "======================================"
echo " SBP Lab 2 - Full Test Script        "
echo "======================================"
echo "Timestamp: $(date)"
echo "======================================"


# --- Configuration ---
BASE_URL="http://localhost:8080" # URL transfer-service
SBP_ADAPTER_URL="http://localhost:8083" # URL sbp-adapter-service (предполагаемый порт)
NOTIFICATION_URL="http://localhost:8084" # URL notification-service (предполагаемый порт)
ACCOUNT_URL="http://localhost:8081" # URL account-service (предполагаемый порт)

SENDER_PHONE="9991112222"
RECIPIENT_PHONE="9993334444" # Привязан к SberBank (Mock) 100000002
RECIPIENT_BANK_ID="100000002" # Ожидаемый ID для SberBank (Mock)

USER_CREDS="user:userpass"
ADMIN_CREDS="admin:adminpass"
INVALID_CREDS="user:wrongpassword"
RUN_COUNT=10 # Уменьшим до 1 для отладки, потом можно увеличить

# --- Function to check if jq is installed ---
check_jq() {
  if ! command -v jq &> /dev/null; then
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "!! Error: 'jq' command not found.                         !!"
    echo "!! Please install jq to parse JSON responses.             !!"
    echo "!! macOS: brew install jq                                 !!"
    echo "!! Debian/Ubuntu: sudo apt-get install jq                 !!"
    echo "!! CentOS/Fedora: sudo yum/dnf install jq                 !!"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit 1
  fi
  echo "[Info] jq found."
}

# --- Function to extract confirmation code using GET /transfers/{id} as admin ---
get_confirmation_code_via_api() {
  local transfer_id=$1
  local code=""

  # Выводим информационные сообщения в stderr (>_2)
  echo "[Info] Attempting to get confirmation code for $transfer_id via API (as admin)..." >&2
  local output_file
  output_file=$(mktemp)
  local http_status
  http_status=$(curl -X GET "$BASE_URL/api/transfers/$transfer_id" \
           -u "$ADMIN_CREDS" \
           -H "X-Phone-Number: $SENDER_PHONE" \
           --silent --show-error --include --output "$output_file" --write-out "%{http_code}")

   local body
   body=$(awk 'BEGIN{found=0} /^(\r)?$/{found=1; next} found{print}' "$output_file")

  # Отладочный вывод тела в stderr
  echo "[Debug] Get Status Response Body for $transfer_id: $body" >&2

  if [ "$http_status" -eq 200 ]; then
      code=$(echo "$body" | jq -r '.confirmationCode // empty')
      if [ -z "$code" ] || [ "$code" == "null" ]; then
          # Выводим ошибку в stderr
          echo -e "\033[0;31mError: Could not extract confirmationCode from API response for $transfer_id! Body: $body\033[0m" >&2
          rm "$output_file"
          return 1 # Indicate failure
      else
           # Выводим информационное сообщение в stderr
          echo "[Info] Successfully extracted confirmation code: $code" >&2
          rm "$output_file"
          echo "$code"
          return 0 # Indicate success
      fi
  else
      # Выводим ошибку в stderr
      echo -e "\033[0;31mError: Failed to get transfer status for $transfer_id (Status: $http_status). Cannot get confirmation code.\033[0m" >&2
      echo "------- RAW GET STATUS OUTPUT -------" >&2
      cat "$output_file" >&2
      echo "------------------------------------" >&2
      rm "$output_file"
      return 1 # Indicate failure
  fi
}



# --- Helper function to print test result ---
print_result() {
    local test_name=$1
    local http_status=$2
    local expected_status=$3
    local response_body=$4

    # Очищаем статус от возможных нечисловых символов (на всякий случай)
    http_status_cleaned=$(echo "$http_status" | tr -cd '0-9')

    echo -n "$test_name - Expected: $expected_status, Got: $http_status_cleaned -> "
    if [[ "$http_status_cleaned" -eq "$expected_status" ]]; then
        echo -e "\033[0;32mPASS\033[0m"
        # Возвращаем 0 при успехе
        return 0
    else
        echo -e "\033[0;31mFAIL\033[0m"
        # Возвращаем 1 при ошибке
        return 1
    fi
    if [ ! -z "$response_body" ]; then
      # Обрезаем длинные ответы для читаемости
      if [ ${#response_body} -gt 200 ]; then
        response_body="${response_body:0:200}..."
      fi
      echo "       Response Body: $response_body"
    fi
    sleep 0.5
}

# --- Main Script ---

check_jq

# --- Initial Security Checks (Run Once) ---
echo ""
echo "--- Running Initial Security Checks ---"

# Тест 1: Доступ без аутентификации
echo "[Test 1.0] Initiate Transfer (Unauthorized)"
STATUS_1=$(curl -X POST "$BASE_URL/api/transfers" \
     -H "Content-Type: application/json" \
     -H "X-Phone-Number: $SENDER_PHONE" \
     -d '{"recipientPhoneNumber": "'$RECIPIENT_PHONE'","amount": 1.00,"bankId": "sec-check-1"}' \
     --silent --output /dev/null --write-out "%{http_code}")
print_result "[Test 1.0]" "$STATUS_1" 401

# Тест 2: Доступ с неверными кредами
echo "[Test 2.0] Initiate Transfer (Invalid Credentials)"
STATUS_2=$(curl -X POST "$BASE_URL/api/transfers" \
     -u "$INVALID_CREDS" \
     -H "Content-Type: application/json" \
     -H "X-Phone-Number: $SENDER_PHONE" \
     -d '{"recipientPhoneNumber": "'$RECIPIENT_PHONE'","amount": 2.00,"bankId": "sec-check-2"}' \
     --silent --output /dev/null --write-out "%{http_code}")
print_result "[Test 2.0]" "$STATUS_2" 401


# --- Full Transfer Flow Loop ---
echo ""
echo "--- Running Full Transfer Flow ($RUN_COUNT times) ---"

FAILED_RUNS=0
for (( i=1; i<=RUN_COUNT; i++ )); do
  echo ""
  echo "--- FLOW RUN $i / $RUN_COUNT ---"
  TRANSFER_ID=""
  CONFIRMATION_CODE=""
  RUN_FAILED=0

  # 1. Инициация перевода (USER)
  echo "[Test 3.$i] Initiate Transfer (User)"
  INITIATE_OUTPUT_FILE=$(mktemp)
  INITIATE_STATUS=$(curl -X POST "$BASE_URL/api/transfers" \
       -u "$USER_CREDS" \
       -H "Content-Type: application/json" \
       -H "X-Phone-Number: $SENDER_PHONE" \
       -d '{
         "recipientPhoneNumber": "'$RECIPIENT_PHONE'",
         "amount": '1$i'.50,
         "bankId": "'$RECIPIENT_BANK_ID'"
       }' \
       --silent --show-error -o "$INITIATE_OUTPUT_FILE" --write-out "%{http_code}")
  INITIATE_BODY=$(cat "$INITIATE_OUTPUT_FILE")
  rm "$INITIATE_OUTPUT_FILE"

  print_result "[Test 3.$i]" "$INITIATE_STATUS" 201 "$INITIATE_BODY"
  if [[ $? -ne 0 ]]; then RUN_FAILED=1; fi # Проверяем результат print_result

  # Извлекаем ID, если статус успешный
  if [ "$RUN_FAILED" -eq 0 ]; then
      TRANSFER_ID=$(echo "$INITIATE_BODY" | jq -r '.transferId // empty')
      if [ -z "$TRANSFER_ID" ] || [ "$TRANSFER_ID" == "null" ]; then
          echo -e "\033[0;31mError: Could not extract transferId from response in run $i!\033[0m"
          RUN_FAILED=1
      else
           echo "       Captured Transfer ID: $TRANSFER_ID"
      fi
  else
      echo -e "\033[0;31mError: Transfer initiation failed in run $i, skipping dependent tests.\033[0m"
  fi

  # Выполняем следующие тесты только если предыдущие шаги успешны
  if [ "$RUN_FAILED" -eq 0 ]; then

      # 2. Получение статуса (USER) - Ожидаем 403
      echo "[Test 4.$i] Get Status $TRANSFER_ID (User)"
      STATUS_4=$(curl -X GET "$BASE_URL/api/transfers/$TRANSFER_ID" \
           -u "$USER_CREDS" \
           -H "X-Phone-Number: $SENDER_PHONE" \
           --silent --output /dev/null --write-out "%{http_code}")
      print_result "[Test 4.$i]" "$STATUS_4" 403
      if [[ $? -ne 0 ]]; then RUN_FAILED=1; fi

  fi # End check RUN_FAILED before Get Status Admin

  if [ "$RUN_FAILED" -eq 0 ]; then
      # 3. Получение статуса (ADMIN) - Используем для получения кода
      echo "[Test 5.$i] Get Status $TRANSFER_ID (Admin)"
      CONFIRMATION_CODE=$(get_confirmation_code_via_api "$TRANSFER_ID")
      GET_CODE_RC=$? # Получаем код возврата функции

      # Проверяем и код возврата, и что переменная не пустая
      if [ $GET_CODE_RC -ne 0 ] || [ -z "$CONFIRMATION_CODE" ]; then
         echo -e "\033[0;31mError: Could not get confirmation code for $TRANSFER_ID in run $i! Skipping confirmation.\033[0m"
         RUN_FAILED=1
      else
        echo "       Using Confirmation Code: [$CONFIRMATION_CODE]"
      fi
  fi # End check RUN_FAILED before Confirmation

  if [ "$RUN_FAILED" -eq 0 ]; then
        # 4. Подтверждение перевода (USER)
        echo "[Test 6.$i] Confirm Transfer $TRANSFER_ID (User)"
        CONFIRM_OUTPUT_FILE=$(mktemp)
        JSON_DATA="{\"confirmationCode\": \"$CONFIRMATION_CODE\"}"
        echo "       DEBUG: JSON data for confirm request: ->$JSON_DATA<-"
        CONFIRM_STATUS=$(curl -X POST "$BASE_URL/api/transfers/$TRANSFER_ID/confirm" \
             -u "$USER_CREDS" \
             -H "Content-Type: application/json" \
             -H "X-Phone-Number: $SENDER_PHONE" \
             -d "$JSON_DATA" \
             --silent --show-error -o "$CONFIRM_OUTPUT_FILE" --write-out "%{http_code}")
        CONFIRM_BODY=$(cat "$CONFIRM_OUTPUT_FILE")
        rm "$CONFIRM_OUTPUT_FILE"

        print_result "[Test 6.$i]" "$CONFIRM_STATUS" 200 "$CONFIRM_BODY"
        if [[ $? -ne 0 ]]; then
          RUN_FAILED=1
           # Опционально: вывести тело при ошибке 500
           if [[ "$CONFIRM_STATUS" =~ ^5[0-9]{2}$ ]]; then # Check if status is 5xx
                echo "       Error Body: $CONFIRM_BODY"
           fi
        fi
  fi # End check RUN_FAILED before ending loop iteration

  if [ "$RUN_FAILED" -ne 0 ]; then
    FAILED_RUNS=$((FAILED_RUNS + 1))
    echo -e "\033[0;31m--- FLOW RUN $i FAILED ---\033[0m"
  else
    echo -e "\033[0;32m--- FLOW RUN $i PASSED ---\033[0m"
  fi

done # End of loop



echo ""
echo "====================="
echo " Test Script Finished "
if [ $FAILED_RUNS -gt 0 ]; then
  echo -e "\033[0;31m $FAILED_RUNS run(s) failed.\033[0m"
  exit 1
else
  echo -e "\033[0;32m All $RUN_COUNT run(s) passed.\033[0m"
  exit 0
fi