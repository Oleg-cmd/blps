#!/bin/bash

# set -x

echo "======================================"
echo " SBP Lab 2 - Полный тестовый скрипт  "
echo "======================================"
echo "Временная метка: $(date)"
echo "======================================"


# --- Конфигурация ---
BASE_URL="http://localhost:8080" # URL transfer-service
# SBP_ADAPTER_URL="http://localhost:8083" # URL sbp-adapter-service (не используется напрямую скриптом)
# NOTIFICATION_URL="http://localhost:8084" # URL notification-service (не используется напрямую скриптом)
# ACCOUNT_URL="http://localhost:8081" # URL account-service (не используется напрямую скриптом)

# --- Отправитель ---
SENDER_PHONE="9991112222"

# --- Получатели (На основе мок-данных SbpAdapterLogic) ---
# Получатель 1: Есть Сбер (Работает) и Альфа (Работает) - Целимся в Сбер
RECIPIENT_PHONE_SBER="9993334444"
RECIPIENT_BANK_ID_SBER="100000002" # SberBank (Mock)

# Получатель 2: Есть Тинькофф (Работает)
RECIPIENT_PHONE_TINKOFF="9995556666"
RECIPIENT_BANK_ID_TINKOFF="100000004" # Tinkoff (Mock)

# Получатель 3: Есть ВТБ (Работает) и Закрытый Банк (Не работает) - Целимся в ВТБ
RECIPIENT_PHONE_VTB="9997778888"
RECIPIENT_BANK_ID_VTB="100000003" # VTB (Mock)

# Получатель 4: Есть ТОЛЬКО Закрытый Банк (Не работает)
RECIPIENT_PHONE_CLOSED="9990000009"
RECIPIENT_BANK_ID_CLOSED="100000009" # Закрытый Банк (Mock) - Не поддерживает СБП


# --- Учетные данные ---
USER_CREDS="user:userpass"
ADMIN_CREDS="admin:adminpass"
INVALID_CREDS="user:wrongpassword"

# --- Суммы ---
SUCCESS_AMOUNT="100.50"
BUSINESS_ERROR_AMOUNT="422.00" # Для имитации SbpBusinessException
TECHNICAL_ERROR_AMOUNT="500.00" # Для имитации SbpTechnicalException
INVALID_CODE_AMOUNT="111.00" # Для тестов с неверным кодом
SMALL_AMOUNT="50.25"         # Другая небольшая сумма для разнообразия
LIMIT_TEST_AMOUNT_1="140000.00" # Для теста превышения лимита (часть 1)
LIMIT_TEST_AMOUNT_2="15000.00"  # Для теста превышения лимита (часть 2)


# --- Прочие настройки ---
MAX_CONFIRMATION_ATTEMPTS=3

# --- Функции ---
check_jq() {
  if ! command -v jq &> /dev/null; then
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" >&2
    echo "!! Ошибка: команда 'jq' не найдена.                       !!" >&2
    echo "!! Пожалуйста, установите jq.                             !!" >&2
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" >&2
    exit 1
  fi
  echo "[Инфо] jq найден."
}

get_confirmation_code_via_api() {
  local transfer_id=$1
  local code=""
  local local_rc=0 # Локальный код возврата

  echo "[Инфо] Попытка получить код подтверждения для $transfer_id через API (админ)..." >&2
  local output_file
  output_file=$(mktemp)
  local http_status
  http_status=$(curl -X GET "$BASE_URL/api/transfers/$transfer_id" \
           -u "$ADMIN_CREDS" \
           -H "X-Phone-Number: $SENDER_PHONE" \
           --connect-timeout 5 --max-time 10 \
           --silent --show-error --include --output "$output_file" --write-out "%{http_code}")

   local body
   body=$(awk 'BEGIN{found=0} /^(\r)?$/{found=1; next} found{print}' "$output_file")

  echo "[Отладка] Тело ответа Get Status для $transfer_id: $body" >&2

  if [ "$http_status" -eq 200 ]; then
      code=$(echo "$body" | jq -r '.confirmationCode // empty')
      if [ -z "$code" ] || [ "$code" == "null" ]; then
          echo -e "\033[0;31mОшибка: Не удалось извлечь confirmationCode из ответа API для $transfer_id! Тело: $body\033[0m" >&2
          local_rc=1
      else
          echo "[Инфо] Код подтверждения успешно извлечен: $code" >&2
          code=$(echo "$code" | tr -d '\r') # Убираем возможные символы возврата каретки
          echo "$code" # Выводим очищенный код
      fi
  else
      echo -e "\033[0;31mОшибка: Не удалось получить статус перевода для $transfer_id (Статус: $http_status). Невозможно получить код подтверждения.\033[0m" >&2
      echo "------- RAW GET STATUS OUTPUT (Admin) -------" >&2
      cat "$output_file" >&2
      echo "------------------------------------------" >&2
      local_rc=1
  fi
  rm "$output_file" &> /dev/null
  return $local_rc
}

print_result() {
    local test_name=$1
    local http_status=$2
    local expected_status=$3
    local response_body=$4
    local result_status=1 # Предполагаем FAIL

    http_status_cleaned=$(echo "$http_status" | tr -cd '0-9')

    echo -n "$test_name - Ожидаемый: $expected_status, Получен: $http_status_cleaned -> "
    if [[ "$http_status_cleaned" == "$expected_status" ]]; then
        echo -e "\033[0;32mPASS\033[0m"
        result_status=0 # PASS
    else
        echo -e "\033[0;31mFAIL\033[0m"
        result_status=1 # FAIL
    fi

    # Всегда выводим тело ответа, если оно не пустое
    if [ ! -z "$response_body" ]; then
      local body_to_print="$response_body"
      body_to_print=$(echo "$body_to_print" | tr -d '\n\r')
      echo "       Тело ответа: $body_to_print"
    fi

    sleep 0.2
    return $result_status
}

# --- Функция для запуска полного цикла перевода ---
# $1: run_index (Индекс запуска)
# $2: recipient_phone (Телефон получателя)
# $3: recipient_bank_id (ID банка получателя)
# $4: amount (Сумма)
# $5: confirmation_code_mode (Режим кода: CORRECT, INVALID, INVALID_MAX)
# $6: expected_confirm_http_status (Ожидаемый HTTP статус подтверждения)
# $7: expected_final_transfer_status (Ожидаемый финальный статус перевода)
# $8: test_prefix (Префикс теста)
run_transfer_flow() {
    local run_index=$1
    local recipient_phone=$2
    local recipient_bank_id=$3
    local amount=$4
    local confirmation_code_mode=$5
    local expected_confirm_http_status=$6
    local expected_final_transfer_status=$7
    local test_prefix=$8

    echo ""
    echo "--- ЗАПУСК ЦИКЛА ${test_prefix} ($run_index) Получатель: $recipient_phone, Банк: $recipient_bank_id, Сумма: $amount ---"
    local TRANSFER_ID=""
    local ACTUAL_CONFIRMATION_CODE=""
    local CODE_TO_SEND=""
    local RUN_FAILED=0

    # 1. Инициация перевода (USER)
    echo "[Тест ${test_prefix}-Init.$run_index] Инициация перевода (Пользователь)"
    INITIATE_OUTPUT_FILE=$(mktemp)
    INITIATE_STATUS=$(curl -X POST "$BASE_URL/api/transfers" \
         -u "$USER_CREDS" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" \
         -d '{"recipientPhoneNumber": "'$recipient_phone'","amount": '$amount',"bankId": "'$recipient_bank_id'"}' \
         --connect-timeout 5 --max-time 15 \
         --silent --show-error -o "$INITIATE_OUTPUT_FILE" --write-out "%{http_code}")
    INITIATE_BODY=$(cat "$INITIATE_OUTPUT_FILE"); rm "$INITIATE_OUTPUT_FILE" &> /dev/null
    if ! print_result "[Тест ${test_prefix}-Init.$run_index]" "$INITIATE_STATUS" 201 "$INITIATE_BODY"; then RUN_FAILED=1; fi

    if [ "$RUN_FAILED" -eq 0 ]; then
        TRANSFER_ID=$(echo "$INITIATE_BODY" | jq -r '.transferId // empty')
        if [ -z "$TRANSFER_ID" ] || [ "$TRANSFER_ID" == "null" ]; then echo -e "\033[0;31mОшибка: Не удалось извлечь transferId!\033[0m"; RUN_FAILED=1; else echo "       Получен Transfer ID: $TRANSFER_ID"; fi
    else echo -e "\033[0;31mОшибка: Инициация перевода не удалась.\033[0m"; fi

    # 2. Получение статуса (USER) - Проверка безопасности
    if [ "$RUN_FAILED" -eq 0 ]; then
        echo "[Тест ${test_prefix}-GetStatusUser.$run_index] Получение статуса $TRANSFER_ID (Пользователь)"
        STATUS_4=$(curl -X GET "$BASE_URL/api/transfers/$TRANSFER_ID" -u "$USER_CREDS" -H "X-Phone-Number: $SENDER_PHONE" --connect-timeout 5 --max-time 10 --silent --output /dev/null --write-out "%{http_code}")
        if ! print_result "[Тест ${test_prefix}-GetStatusUser.$run_index]" "$STATUS_4" 403; then RUN_FAILED=1; fi
    fi

    # 3. Получение кода (ADMIN)
    if [ "$RUN_FAILED" -eq 0 ]; then
        echo "[Тест ${test_prefix}-GetCode.$run_index] Получение статуса $TRANSFER_ID (Админ) для извлечения кода"
        ACTUAL_CONFIRMATION_CODE=$(get_confirmation_code_via_api "$TRANSFER_ID")
        GET_CODE_RC=$?
        if [ $GET_CODE_RC -ne 0 ] || [ -z "$ACTUAL_CONFIRMATION_CODE" ]; then
           echo -e "\033[0;31mОшибка: Не удалось получить код подтверждения! Пропуск теста подтверждения.\033[0m"
           RUN_FAILED=1
        else
           echo "       Реальный код подтверждения: [$ACTUAL_CONFIRMATION_CODE]"
           if [[ "$confirmation_code_mode" == "CORRECT" ]]; then CODE_TO_SEND="$ACTUAL_CONFIRMATION_CODE";
           elif [[ "$confirmation_code_mode" == "INVALID" || "$confirmation_code_mode" == "INVALID_MAX" ]]; then CODE_TO_SEND="000000"; if [[ "$CODE_TO_SEND" == "$ACTUAL_CONFIRMATION_CODE" ]]; then CODE_TO_SEND="111111"; fi
           else echo -e "\033[0;31mОшибка: Неизвестный режим '$confirmation_code_mode'!\033[0m"; RUN_FAILED=1; CODE_TO_SEND=""; fi
           if [[ "$RUN_FAILED" -eq 0 ]]; then echo "       Код для отправки в тесте: [$CODE_TO_SEND]"; fi
        fi
    fi

    # 4. Подтверждение (USER)
    if [ "$RUN_FAILED" -eq 0 ] && [ ! -z "$CODE_TO_SEND" ] ; then
        if [[ "$confirmation_code_mode" == "INVALID_MAX" ]]; then
            echo "[Тест ${test_prefix}-Confirm.$run_index] Подтверждение перевода $TRANSFER_ID (Пользователь) - Отправка НЕВЕРНОГО кода $MAX_CONFIRMATION_ATTEMPTS раз"
            local attempt confirm_output_file_inv json_data_inv confirm_status_inv confirm_body_inv expected_status_inv expected_message actual_message
            for attempt in $(seq 1 $MAX_CONFIRMATION_ATTEMPTS); do
                 confirm_output_file_inv=$(mktemp)
                 json_data_inv="{\"confirmationCode\": \"$CODE_TO_SEND\"}"
                 echo "       Отладка: Неверная попытка $attempt/$MAX_CONFIRMATION_ATTEMPTS JSON: ->$json_data_inv<-" >&2
                 confirm_status_inv=$(curl -X POST "$BASE_URL/api/transfers/$TRANSFER_ID/confirm" -u "$USER_CREDS" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" -d "$json_data_inv" --connect-timeout 5 --max-time 15 --silent --show-error -o "$confirm_output_file_inv" --write-out "%{http_code}")
                 confirm_body_inv=$(cat "$confirm_output_file_inv"); rm "$confirm_output_file_inv" &> /dev/null
                 expected_status_inv=200
                 if ! print_result "[Тест ${test_prefix}-Confirm-Attempt$attempt.$run_index]" "$confirm_status_inv" "$expected_status_inv" "$confirm_body_inv"; then RUN_FAILED=1; break; fi
                 if [[ $attempt -lt $MAX_CONFIRMATION_ATTEMPTS ]]; then expected_message="Invalid confirmation code. Attempts left: $(($MAX_CONFIRMATION_ATTEMPTS - attempt))"; else expected_message="Invalid confirmation code. Max attempts exceeded."; fi
                 actual_message=$(echo "$confirm_body_inv" | jq -r '.message // ""')
                 echo -n "       Проверка сообщения ответа: Ожидается '$expected_message', Получено '$actual_message' -> "
                 if [[ "$actual_message" == "$expected_message" ]]; then echo -e "\033[0;32mPASS\033[0m"; else echo -e "\033[0;31mFAIL\033[0m"; RUN_FAILED=1; break; fi
                 sleep 1
            done
            expected_confirm_http_status=200 # Общий ожидаемый статус для шага (последний запрос)
        else
            echo "[Тест ${test_prefix}-Confirm.$run_index] Подтверждение перевода $TRANSFER_ID (Пользователь)"
            CONFIRM_OUTPUT_FILE=$(mktemp)
            JSON_DATA="{\"confirmationCode\": \"$CODE_TO_SEND\"}"
            echo "       Отладка: JSON данные для запроса подтверждения: ->$JSON_DATA<-" >&2
            CONFIRM_STATUS=$(curl -X POST "$BASE_URL/api/transfers/$TRANSFER_ID/confirm" -u "$USER_CREDS" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" -d "$JSON_DATA" --connect-timeout 5 --max-time 15 --silent --show-error -o "$CONFIRM_OUTPUT_FILE" --write-out "%{http_code}")
            CONFIRM_BODY=$(cat "$CONFIRM_OUTPUT_FILE"); rm "$CONFIRM_OUTPUT_FILE" &> /dev/null
            if ! print_result "[Тест ${test_prefix}-Confirm.$run_index]" "$CONFIRM_STATUS" "$expected_confirm_http_status" "$CONFIRM_BODY"; then RUN_FAILED=1; fi
            if [[ "$CONFIRM_STATUS" =~ ^5[0-9]{2}$ ]] || [[ "$CONFIRM_STATUS" == "000" ]]; then echo "       Тело ошибки: $CONFIRM_BODY"; fi
        fi
    fi

    # 5. Финальная проверка статуса (ADMIN)
    if [ ! -z "$TRANSFER_ID" ]; then
        echo "[Тест ${test_prefix}-FinalStatus.$run_index] Получение финального статуса $TRANSFER_ID (Админ)"
        sleep 1 # Небольшая пауза перед проверкой финального статуса
        FINAL_OUTPUT_FILE=$(mktemp)
        FINAL_STATUS_CODE=$(curl -X GET "$BASE_URL/api/transfers/$TRANSFER_ID" \
             -u "$ADMIN_CREDS" -H "X-Phone-Number: $SENDER_PHONE" \
             --connect-timeout 5 --max-time 10 \
             --silent --show-error -o "$FINAL_OUTPUT_FILE" --write-out "%{http_code}")
        FINAL_BODY=$(cat "$FINAL_OUTPUT_FILE"); rm "$FINAL_OUTPUT_FILE" &> /dev/null
        print_result "[Тест ${test_prefix}-FinalStatus.$run_index]" "$FINAL_STATUS_CODE" 200 "$FINAL_BODY"

        if [ "$FINAL_STATUS_CODE" -eq 200 ]; then
            actual_final_status=$(echo "$FINAL_BODY" | jq -r '.status // "UNKNOWN"')
            echo -n "       Проверка финального статуса перевода: Ожидается '$expected_final_transfer_status', Получено '$actual_final_status' -> "
            if [ "$actual_final_status" == "$expected_final_transfer_status" ]; then echo -e "\033[0;32mPASS\033[0m"; else echo -e "\033[0;31mFAIL\033[0m"; RUN_FAILED=1; fi
        else
             RUN_FAILED=1
        fi
    else
        echo "Пропуск финальной проверки статуса, так как TRANSFER_ID отсутствует."
        RUN_FAILED=1
    fi


    if [ "$RUN_FAILED" -ne 0 ]; then
      FAILED_RUNS=$((FAILED_RUNS + 1))
      echo -e "\033[0;31m--- ЗАПУСК ЦИКЛА ${test_prefix} ($run_index) ЗАВЕРШИЛСЯ С ОШИБКОЙ ---\033[0m"
      return 1
    else
      echo -e "\033[0;32m--- ЗАПУСК ЦИКЛА ${test_prefix} ($run_index) ПРОШЕЛ УСПЕШНО ---\033[0m"
      return 0
    fi
}


# --- Основное выполнение скрипта ---
check_jq

# --- Начальные проверки безопасности ---
echo ""
echo "--- Запуск начальных проверок безопасности ---"
echo "[Тест Sec-1.0] Инициация перевода (Неавторизован)"
STATUS_1=$(curl -X POST "$BASE_URL/api/transfers" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" -d '{"recipientPhoneNumber": "'$RECIPIENT_PHONE_SBER'","amount": 1.00,"bankId": "sec-check-1"}' --connect-timeout 5 --max-time 10 --silent --output /dev/null --write-out "%{http_code}")
print_result "[Тест Sec-1.0]" "$STATUS_1" 401 ""
echo "[Тест Sec-2.0] Инициация перевода (Неверные учетные данные)"
STATUS_2=$(curl -X POST "$BASE_URL/api/transfers" -u "$INVALID_CREDS" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" -d '{"recipientPhoneNumber": "'$RECIPIENT_PHONE_SBER'","amount": 2.00,"bankId": "sec-check-2"}' --connect-timeout 5 --max-time 10 --silent --output /dev/null --write-out "%{http_code}")
print_result "[Тест Sec-2.0]" "$STATUS_2" 401 ""

# --- Запуск сценариев ---
echo ""
echo "--- Запуск основных сценариев ---"
FAILED_RUNS=0

# Сценарий 1: Успешный перевод (Получатель SBER)
run_transfer_flow 1 "$RECIPIENT_PHONE_SBER" "$RECIPIENT_BANK_ID_SBER" "$SUCCESS_AMOUNT" "CORRECT" 200 "SUCCESSFUL" "SuccessSber"
if [[ $? -ne 0 ]]; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi

# Сценарий 2: Ошибка бизнес-логики SBP (Получатель TINKOFF)
run_transfer_flow 2 "$RECIPIENT_PHONE_TINKOFF" "$RECIPIENT_BANK_ID_TINKOFF" "$BUSINESS_ERROR_AMOUNT" "CORRECT" 200 "FAILED" "BizErrTinkoff"
if [[ $? -ne 0 ]]; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi

# Сценарий 3: Техническая ошибка SBP (Получатель VTB)
run_transfer_flow 3 "$RECIPIENT_PHONE_VTB" "$RECIPIENT_BANK_ID_VTB" "$TECHNICAL_ERROR_AMOUNT" "CORRECT" 200 "FAILED" "TechErrVtb"
if [[ $? -ne 0 ]]; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi

# Сценарий 4: Неверный код подтверждения (1 раз) (Получатель SBER)
run_transfer_flow 4 "$RECIPIENT_PHONE_SBER" "$RECIPIENT_BANK_ID_SBER" "$INVALID_CODE_AMOUNT" "INVALID" 200 "AWAITING_CONFIRMATION" "InvCodeSber"
if [[ $? -ne 0 ]]; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi

# Сценарий 5: Неверный код подтверждения (максимум попыток) (Получатель TINKOFF)
run_transfer_flow 5 "$RECIPIENT_PHONE_TINKOFF" "$RECIPIENT_BANK_ID_TINKOFF" "$INVALID_CODE_AMOUNT" "INVALID_MAX" 200 "FAILED" "InvCodeMaxTinkoff"
if [[ $? -ne 0 ]]; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi

# Сценарий 6: Успешный перевод другому получателю (VTB)
run_transfer_flow 6 "$RECIPIENT_PHONE_VTB" "$RECIPIENT_BANK_ID_VTB" "$SMALL_AMOUNT" "CORRECT" 200 "SUCCESSFUL" "SuccessVtb"
if [[ $? -ne 0 ]]; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi


# --- Сценарий 7: Превышение дневного лимита ---
echo ""
echo "--- ЗАПУСК ЦИКЛА LimitExceed (7) ---"
# Используем получателя SBER для теста лимита
echo "[Тест LimitExceed-Setup.7] Выполнение первого крупного перевода (Получатель: $RECIPIENT_PHONE_SBER, Сумма: $LIMIT_TEST_AMOUNT_1)..."
run_transfer_flow 7 "$RECIPIENT_PHONE_SBER" "$RECIPIENT_BANK_ID_SBER" "$LIMIT_TEST_AMOUNT_1" "CORRECT" 200 "SUCCESSFUL" "LimitSetupSber"
SETUP_PASSED=$?
if [[ $SETUP_PASSED -ne 0 ]]; then
    echo -e "\033[0;31mОшибка: Перевод для настройки лимита не удался. Пропуск теста превышения лимита.\033[0m"
    FAILED_RUNS=$((FAILED_RUNS + 1))
else
    echo "[Тест LimitExceed-Check.7] Попытка перевода, превышающего лимит (Получатель: $RECIPIENT_PHONE_SBER, Сумма: $LIMIT_TEST_AMOUNT_2)"
    LIMIT_OUTPUT_FILE=$(mktemp)
    LIMIT_STATUS=$(curl -X POST "$BASE_URL/api/transfers" \
         -u "$USER_CREDS" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" \
         -d '{"recipientPhoneNumber": "'$RECIPIENT_PHONE_SBER'","amount": '$LIMIT_TEST_AMOUNT_2',"bankId": "'$RECIPIENT_BANK_ID_SBER'"}' \
         --connect-timeout 5 --max-time 15 \
         --silent --show-error -o "$LIMIT_OUTPUT_FILE" --write-out "%{http_code}")
    LIMIT_BODY=$(cat "$LIMIT_OUTPUT_FILE"); rm "$LIMIT_OUTPUT_FILE" &> /dev/null
    # Ожидаем 400 Bad Request, так как TransferLimitExceededException должен обрабатываться GlobalExceptionHandler
    if ! print_result "[Тест LimitExceed-Check.7]" "$LIMIT_STATUS" 400 "$LIMIT_BODY"; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi
    if [[ "$LIMIT_STATUS" -eq 400 ]]; then
        if echo "$LIMIT_BODY" | jq -e '.message | test("Daily transfer limit exceeded")' > /dev/null; then
            echo "       Проверка сообщения об ошибке: Найдено 'Daily transfer limit exceeded' -> PASS"
        else
            echo "       Проверка сообщения об ошибке: Ожидаемое сообщение не найдено -> FAIL"
            # FAILED_RUNS=$((FAILED_RUNS + 1)) # Можно раскомментировать, если считаем это провалом
        fi
    fi
fi


# --- Сценарий 8: Попытка перевода получателю с банком, не поддерживающим СБП ---
echo ""
echo "--- ЗАПУСК ЦИКЛА NonSbpBank (8) ---"
echo "[Тест NonSbpBank-Init.8] Инициация перевода получателю с банком без поддержки СБП (Телефон: $RECIPIENT_PHONE_CLOSED, Банк: $RECIPIENT_BANK_ID_CLOSED)"
NON_SBP_OUTPUT_FILE=$(mktemp)
NON_SBP_STATUS=$(curl -X POST "$BASE_URL/api/transfers" \
     -u "$USER_CREDS" -H "Content-Type: application/json" -H "X-Phone-Number: $SENDER_PHONE" \
     -d '{"recipientPhoneNumber": "'$RECIPIENT_PHONE_CLOSED'","amount": '$SMALL_AMOUNT',"bankId": "'$RECIPIENT_BANK_ID_CLOSED'"}' \
     --connect-timeout 5 --max-time 15 \
     --silent --show-error -o "$NON_SBP_OUTPUT_FILE" --write-out "%{http_code}")
NON_SBP_BODY=$(cat "$NON_SBP_OUTPUT_FILE"); rm "$NON_SBP_OUTPUT_FILE" &> /dev/null
# Ожидаем ошибку 400 или 422, так как SbpAdapter вернет Optional.empty() и TransferService должен выдать исключение (например, BankNotFoundException или RecipientBankNotAvailableException)
# Точный код зависит от реализации обработчика исключений. Предположим 400.
if ! print_result "[Тест NonSbpBank-Init.8]" "$NON_SBP_STATUS" 400 "$NON_SBP_BODY"; then FAILED_RUNS=$((FAILED_RUNS + 1)); fi
# Дополнительная проверка сообщения (опционально)
if [[ "$NON_SBP_STATUS" -eq 400 ]]; then
    if echo "$NON_SBP_BODY" | jq -e '.message | test("Recipient bank .* does not support SBP")' > /dev/null || \
       echo "$NON_SBP_BODY" | jq -e '.message | test("Bank with ID .* not found or does not support SBP")' > /dev/null || \
       echo "$NON_SBP_BODY" | jq -e '.message | test("Recipient bank not available")' > /dev/null ; then # Проверяем разные возможные сообщения
        echo "       Проверка сообщения об ошибке: Найдено ожидаемое сообщение об ошибке банка -> PASS"
    else
        echo "       Проверка сообщения об ошибке: Ожидаемое сообщение не найдено -> FAIL"
    fi
fi


# --- Итоговый результат ---
echo ""
echo "======================================"
echo " Тестовый скрипт завершен           "
echo "======================================"
if [ $FAILED_RUNS -gt 0 ]; then
  echo -e "\033[0;31m $FAILED_RUNS сценарий(ев) завершились с ошибкой.\033[0m"
  exit 1 # Завершаем скрипт с кодом ошибки
else
  echo -e "\033[0;32m Все сценарии прошли успешно.\033[0m"
  exit 0 # Завершаем скрипт успешно
fi

# set +x