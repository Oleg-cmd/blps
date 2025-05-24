#!/bin/bash

# --- Конфигурация ---
TRANSFER_SERVICE_BASE_URL="http://localhost:8080/api/v1"
NOTIFICATION_UI_URL="http://localhost:8082/ui/codes"

# Учетные данные (предполагаем, что JAAS username для обычного пользователя - это его номер телефона)
SENDER_PHONE_AS_USER="9991112222" # Это будет username для Basic Auth
USER_PASSWORD="userpass"
ADMIN_USER="admin"
ADMIN_PASSWORD="adminpass"

# Данные для переводов
RECIPIENT_PHONE_SBER="9993334444"
RECIPIENT_BANK_ID_SBER="100000002" # SberBank (Mock)
SUCCESS_AMOUNT="100.50"

RECIPIENT_PHONE_TINKOFF="9995556666"
RECIPIENT_BANK_ID_TINKOFF="100000004" # Tinkoff (Mock)
FAIL_AMOUNT="50.00"
INVALID_CODE="000000"
MAX_INVALID_ATTEMPTS=3

curl -X POST "http://localhost:8080/api/v1/transfers/initiate" \
     -u "user:userpass" \
     -H "Content-Type: application/json" \
     -d '{"recipientPhoneNumber": "9993334444","amount": '100',"bankId": "9995556666"}'

# --- Утилитарные функции ---
C_RESET='\033[0m'
C_RED='\033[0;31m'
C_GREEN='\033[0;32m'
C_YELLOW='\033[0;33m'
C_BLUE='\033[0;34m'
C_CYAN='\033[0;36m'

log_info() {
    echo -e "${C_BLUE}[INFO] $1${C_RESET}"
}

log_success() {
    echo -e "${C_GREEN}[SUCCESS] $1${C_RESET}"
}

log_error() {
    echo -e "${C_RED}[ERROR] $1${C_RESET}"
}

log_warn() {
    echo -e "${C_YELLOW}[WARN] $1${C_RESET}"
}

log_debug() {
    echo -e "${C_CYAN}[DEBUG] $1${C_RESET}"
}

print_separator() {
    echo "----------------------------------------------------------------------"
}

# Функция для выполнения curl запроса и вывода результата
# $1: Описание запроса
# $2: HTTP метод (GET, POST, etc.)
# $3: URL
# $4: Пользователь для Basic Auth (например, "user:pass" или пустая строка)
# $5: Данные для POST (JSON строка или пустая строка)
# $6: Ожидаемый HTTP статус
# Возвращает тело ответа, если статус ожидаемый, иначе пустую строку и печатает ошибку
execute_curl() {
    local description="$1"
    local method="$2"
    local url="$3"
    local auth_user="$4"
    local post_data="$5"
    local expected_status="$6"
    local response_body=""
    local http_status_code=""
    local output_file
    output_file=$(mktemp)

    log_info "$description"
    log_debug "Метод: $method, URL: $url"
    if [ -n "$auth_user" ]; then log_debug "Auth: $auth_user"; fi
    if [ -n "$post_data" ]; then log_debug "Данные: $post_data"; fi

    local curl_cmd="curl -X $method --connect-timeout 5 --max-time 15 --silent --show-error -o \"$output_file\" --write-out \"%{http_code}\""
    if [ -n "$auth_user" ]; then
        curl_cmd="$curl_cmd -u \"$auth_user\""
    fi
    if [[ "$method" == "POST" || "$method" == "PUT" ]]; then
        curl_cmd="$curl_cmd -H \"Content-Type: application/json\""
        if [ -n "$post_data" ]; then
            curl_cmd="$curl_cmd -d '$post_data'"
        fi
    fi
    curl_cmd="$curl_cmd \"$url\""

    log_debug "Команда Curl: $curl_cmd"
    http_status_code=$(eval "$curl_cmd")
    response_body=$(cat "$output_file")
    rm "$output_file" &> /dev/null

    log_debug "HTTP Статус: $http_status_code"
    log_debug "Тело ответа: $response_body"

    if [ "$http_status_code" -eq "$expected_status" ]; then
        log_success "Запрос выполнен успешно (Статус: $http_status_code)."
        echo "$response_body" # Возвращаем тело для дальнейшей обработки
    else
        log_error "Запрос не удался. Ожидался статус $expected_status, получен $http_status_code."
        log_error "Тело ответа: $response_body"
        echo "" # Возвращаем пустоту в случае ошибки
        return 1
    fi
    return 0
}

# Функция для получения кода подтверждения с UI
# $1: correlationId
get_confirmation_code_from_ui() {
    local transfer_correlation_id="$1"
    local code=""
    local attempt_delay=3 # Задержка между попытками
    local max_attempts=5  # Максимальное количество попыток

    log_info "Попытка получить код подтверждения для correlationId $transfer_correlation_id с UI ($NOTIFICATION_UI_URL)..."

    for ((i=1; i<=max_attempts; i++)); do
        log_debug "Попытка $i/$max_attempts получить код с UI..."
        local output_file
        output_file=$(mktemp)
        local http_status
        http_status=$(curl -X GET "$NOTIFICATION_UI_URL" \
                    --connect-timeout 5 --max-time 10 \
                    --silent --show-error --output "$output_file" --write-out "%{http_code}")

        if [ "$http_status" -eq 200 ]; then
            # Очень хрупкий парсинг HTML. Адаптируйте, если структура вашей страницы отличается.
            # Ищем строку, содержащую correlationId, затем из этой строки извлекаем код
            local matching_row
            matching_row=$(grep "$transfer_correlation_id" "$output_file")

            if [ -n "$matching_row" ]; then
                # Извлекаем текст из <td> с классом "code" в этой строке
                code=$(echo "$matching_row" | grep -oP '<td class="code"[^>]*>\K[^<]+' | head -n 1 | tr -d '[:space:]')
                if [ -n "$code" ] && [ "$code" != "null" ]; then
                    log_success "Код подтверждения успешно извлечен из UI: $code"
                    rm "$output_file" &> /dev/null
                    echo "$code"
                    return 0
                fi
            fi
            log_debug "Код для $transfer_correlation_id не найден на странице (попытка $i). Тело страницы сохранено в $output_file (будет удалено)."
        else
            log_warn "Не удалось получить страницу кодов от Notification Service (Статус: $http_status) на попытке $i."
            log_debug "Тело ответа с UI: $(cat "$output_file")"
        fi
        rm "$output_file" &> /dev/null
        if [ $i -lt $max_attempts ]; then
            log_info "Ожидание $attempt_delay секунд перед следующей попыткой..."
            sleep $attempt_delay
        fi
    done

    log_error "Не удалось извлечь код подтверждения для $transfer_correlation_id с UI после $max_attempts попыток."
    return 1
}


# --- Сценарий 1: Успешный перевод ---
run_successful_transfer() {
    print_separator
    log_info "--- СТАРТ СЦЕНАРИЯ 1: УСПЕШНЫЙ ПЕРЕВОД ---"
    local transfer_id=""
    local correlation_id="" # Будем пытаться извлечь, если ваш API его возвращает
    local confirmation_code=""

    # 1. Инициация перевода
    local initiate_payload='{"recipientPhoneNumber": "'"$RECIPIENT_PHONE_SBER"'","amount": '"$SUCCESS_AMOUNT"',"bankId": "'"$RECIPIENT_BANK_ID_SBER"'"}'
    local initiate_response
    initiate_response=$(execute_curl "Инициация успешного перевода" \
                                   "POST" \
                                   "$TRANSFER_SERVICE_BASE_URL/transfers/initiate" \
                                   "${SENDER_PHONE_AS_USER}:${USER_PASSWORD}" \
                                   "$initiate_payload" \
                                   202) # Ожидаем 202 Accepted
    if [ $? -ne 0 ]; then log_error "Сценарий 1 провален на этапе инициации."; return 1; fi

    transfer_id=$(echo "$initiate_response" | jq -r '.transferId // empty')
    correlation_id=$(echo "$initiate_response" | jq -r '.correlationId // empty')

    if [ -z "$transfer_id" ] || [ "$transfer_id" == "null" ]; then
        log_error "Не удалось извлечь transferId из ответа на инициацию."
        return 1
    fi
    log_info "Успешный перевод инициирован. Transfer ID: $transfer_id, Correlation ID: $correlation_id"

    # 2. Получение кода подтверждения
    if [ -z "$correlation_id" ] || [ "$correlation_id" == "null" ]; then
        log_warn "Correlation ID не был получен из ответа на инициацию. Попытка получить его через админский GET /transfers/{id}"
        # Если correlation_id не пришел в ответе, можно попробовать его получить через админский запрос
        # Это усложнение, лучше если initiate_response сразу его отдает
        local admin_get_response
        admin_get_response=$(execute_curl "Получение Correlation ID (Admin)" \
                                         "GET" \
                                         "$TRANSFER_SERVICE_BASE_URL/transfers/$transfer_id" \
                                         "${ADMIN_USER}:${ADMIN_PASSWORD}" \
                                         "" \
                                         200)
        if [ $? -eq 0 ]; then
            correlation_id=$(echo "$admin_get_response" | jq -r '.correlationId // empty')
        fi

        if [ -z "$correlation_id" ] || [ "$correlation_id" == "null" ]; then
             log_error "Не удалось извлечь correlationId даже через админский запрос. Невозможно получить код с UI."
             return 1
        fi
        log_info "Correlation ID получен админским запросом: $correlation_id"
    fi

    confirmation_code=$(get_confirmation_code_from_ui "$correlation_id")
    if [ $? -ne 0 ]; then log_error "Сценарий 1 провален: не удалось получить код подтверждения."; return 1; fi

    # 3. Подтверждение перевода
    local confirm_payload='{"confirmationCode": "'"$confirmation_code"'"}'
    local confirm_response
    confirm_response=$(execute_curl "Подтверждение успешного перевода (ID: $transfer_id)" \
                                  "POST" \
                                  "$TRANSFER_SERVICE_BASE_URL/transfers/$transfer_id/confirm" \
                                  "${SENDER_PHONE_AS_USER}:${USER_PASSWORD}" \
                                  "$confirm_payload" \
                                  200)
    if [ $? -ne 0 ]; then log_error "Сценарий 1 провален на этапе подтверждения."; return 1; fi
    log_info "Перевод подтвержден. Ответ: $confirm_response"

    # 4. Проверка финального статуса (Админ)
    log_info "Ожидание 5 секунд для обработки перевода..."
    sleep 5
    local final_status_response
    final_status_response=$(execute_curl "Проверка финального статуса успешного перевода (ID: $transfer_id, Admin)" \
                                      "GET" \
                                      "$TRANSFER_SERVICE_BASE_URL/transfers/$transfer_id" \
                                      "${ADMIN_USER}:${ADMIN_PASSWORD}" \
                                      "" \
                                      200)
    if [ $? -ne 0 ]; then log_error "Сценарий 1 провален: не удалось получить финальный статус."; return 1; fi

    local final_status
    final_status=$(echo "$final_status_response" | jq -r '.status // "UNKNOWN"')
    if [ "$final_status" == "SUCCESSFUL" ]; then
        log_success "Сценарий 1 УСПЕШНО ЗАВЕРШЕН. Финальный статус: $final_status"
    else
        log_error "Сценарий 1 ПРОВАЛЕН. Ожидался статус SUCCESSFUL, получен: $final_status"
        log_debug "Полный финальный ответ: $final_status_response"
        return 1
    fi
    print_separator
    return 0
}

# --- Сценарий 2: Неуспешный перевод (неверный код) ---
run_failed_transfer_invalid_code() {
    print_separator
    log_info "--- СТАРТ СЦЕНАРИЯ 2: НЕУСПЕШНЫЙ ПЕРЕВОД (неверный код) ---"
    local transfer_id=""
    local correlation_id=""

    # 1. Инициация перевода
    local initiate_payload='{"recipientPhoneNumber": "'"$RECIPIENT_PHONE_TINKOFF"'","amount": '"$FAIL_AMOUNT"',"bankId": "'"$RECIPIENT_BANK_ID_TINKOFF"'"}'
    local initiate_response
    initiate_response=$(execute_curl "Инициация перевода для теста с неверным кодом" \
                                   "POST" \
                                   "$TRANSFER_SERVICE_BASE_URL/transfers/initiate" \
                                   "${SENDER_PHONE_AS_USER}:${USER_PASSWORD}" \
                                   "$initiate_payload" \
                                   202)
    if [ $? -ne 0 ]; then log_error "Сценарий 2 провален на этапе инициации."; return 1; fi

    transfer_id=$(echo "$initiate_response" | jq -r '.transferId // empty')
    correlation_id=$(echo "$initiate_response" | jq -r '.correlationId // empty')


    if [ -z "$transfer_id" ] || [ "$transfer_id" == "null" ]; then
        log_error "Не удалось извлечь transferId из ответа на инициацию."
        return 1
    fi
     log_info "Перевод для теста с неверным кодом инициирован. Transfer ID: $transfer_id, Correlation ID: $correlation_id"

    # 2. Попытка подтверждения с неверным кодом (до MAX_INVALID_ATTEMPTS раз)
    local confirm_status_code
    local confirm_response_body
    local attempt_message
    local last_status="UNKNOWN"

    for ((attempt=1; attempt<=$MAX_INVALID_ATTEMPTS; attempt++)); do
        print_separator
        log_info "Попытка подтверждения $attempt/$MAX_INVALID_ATTEMPTS с неверным кодом ($INVALID_CODE) для Transfer ID: $transfer_id"
        local confirm_payload='{"confirmationCode": "'"$INVALID_CODE"'"}'
        confirm_response_body=$(execute_curl "Подтверждение с неверным кодом (попытка $attempt)" \
                                           "POST" \
                                           "$TRANSFER_SERVICE_BASE_URL/transfers/$transfer_id/confirm" \
                                           "${SENDER_PHONE_AS_USER}:${USER_PASSWORD}" \
                                           "$confirm_payload" \
                                           200) # Ожидаем 200 OK с сообщением об ошибке

        if [ $? -ne 0 ]; then log_error "Сценарий 2 провален на этапе подтверждения (попытка $attempt)."; return 1; fi

        attempt_message=$(echo "$confirm_response_body" | jq -r '.message // ""')
        last_status=$(echo "$confirm_response_body" | jq -r '.status // "UNKNOWN"')
        log_info "Ответ на попытку $attempt: Статус '$last_status', Сообщение: '$attempt_message'"

        if [ $attempt -lt $MAX_INVALID_ATTEMPTS ]; then
            local expected_msg_part="Attempts left: $(($MAX_INVALID_ATTEMPTS - $attempt))"
            if [[ "$attempt_message" != *"$expected_msg_part"* ]]; then
                log_error "Неверное сообщение об оставшихся попытках. Ожидалось что-то вроде '...$expected_msg_part...', получено: '$attempt_message'"
                # return 1 # Можно сделать фатальной ошибкой
            fi
            if [[ "$last_status" != "AWAITING_CONFIRMATION" ]]; then
                log_error "Ожидался статус AWAITING_CONFIRMATION после $attempt неверной попытки, получен: $last_status"
                # return 1
            fi
        else # Последняя попытка
            if [[ "$attempt_message" != *"Max attempts reached"* && "$attempt_message" != *"Max attempts exceeded"* ]]; then
                log_error "Неверное сообщение о максимальном количестве попыток. Ожидалось '...Max attempts reached/exceeded...', получено: '$attempt_message'"
                # return 1
            fi
             # После последней неверной попытки статус должен измениться
            if [[ "$last_status" != "CONFIRMATION_FAILED" && "$last_status" != "FAILED" ]]; then
                 log_error "Ожидался статус CONFIRMATION_FAILED или FAILED после $attempt неверной попытки, получен: $last_status"
                 # return 1
            fi
        fi
        log_info "Ожидание 1 секунду..."
        sleep 1
    done

    # 3. Проверка финального статуса (Админ)
    log_info "Ожидание 3 секунды для обработки..."
    sleep 3
    local final_status_response
    final_status_response=$(execute_curl "Проверка финального статуса перевода с неверным кодом (ID: $transfer_id, Admin)" \
                                      "GET" \
                                      "$TRANSFER_SERVICE_BASE_URL/transfers/$transfer_id" \
                                      "${ADMIN_USER}:${ADMIN_PASSWORD}" \
                                      "" \
                                      200)
    if [ $? -ne 0 ]; then log_error "Сценарий 2 провален: не удалось получить финальный статус."; return 1; fi

    local final_status
    final_status=$(echo "$final_status_response" | jq -r '.status // "UNKNOWN"')
    if [[ "$final_status" == "CONFIRMATION_FAILED" || "$final_status" == "FAILED" ]]; then # В зависимости от вашей логики
        log_success "Сценарий 2 УСПЕШНО ЗАВЕРШЕН (как и ожидалось, с ошибкой). Финальный статус: $final_status"
    else
        log_error "Сценарий 2 ПРОВАЛЕН. Ожидался статус CONFIRMATION_FAILED или FAILED, получен: $final_status"
        log_debug "Полный финальный ответ: $final_status_response"
        return 1
    fi
    print_separator
    return 0
}


# --- Запуск тестов ---
TOTAL_SCENARIOS=0
PASSED_SCENARIOS=0

run_successful_transfer
if [ $? -eq 0 ]; then PASSED_SCENARIOS=$((PASSED_SCENARIOS + 1)); fi
TOTAL_SCENARIOS=$((TOTAL_SCENARIOS + 1))

echo "" # Пустая строка для разделения логов сценариев
sleep 2 # Небольшая пауза между сценариями

run_failed_transfer_invalid_code
if [ $? -eq 0 ]; then PASSED_SCENARIOS=$((PASSED_SCENARIOS + 1)); fi
TOTAL_SCENARIOS=$((TOTAL_SCENARIOS + 1))


# --- Итог ---
print_separator
log_info "--- ИТОГИ ТЕСТИРОВАНИЯ ---"
if [ "$PASSED_SCENARIOS" -eq "$TOTAL_SCENARIOS" ]; then
    log_success "Все $TOTAL_SCENARIOS сценария(ев) прошли успешно!"
    exit 0
else
    log_error "$((TOTAL_SCENARIOS - PASSED_SCENARIOS)) из $TOTAL_SCENARIOS сценария(ев) провалены."
    exit 1
fi