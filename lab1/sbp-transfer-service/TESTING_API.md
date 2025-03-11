# Тестовые сценарии для SBP Transfer Service

### 1. Инициация перевода

```bash
curl -X POST http://localhost:{{server.port}}/api/transfers \
  -H "Content-Type: application/json" \
  -H "X-Phone-Number: {{senderPhoneNumber}}" \
  -d '{
    "recipientPhoneNumber": "{{recipientPhoneNumber}}",
    "amount": {{amount}},
    "bankId": "{{bankId}}"
  }'
```

Ожидаемый ответ:
```json
{
    "transferId": "{{transferId}}",
    "status": "AWAITING_CONFIRMATION",
    "recipientBankName": "{{recipientBankName}}"
}
```

### 2. Подтверждение перевода

```bash
curl -X POST http://localhost:{{server.port}}/api/transfers/{{transferId}}/confirm \
  -H "Content-Type: application/json" \
  -H "X-Phone-Number: {{senderPhoneNumber}}" \
  -d '{
    "confirmationCode": "{{confirmationCode}}"
  }'
```

Ожидаемый ответ:
```json
{
    "transferId": "{{transferId}}",
    "status": "SUCCESSFUL",
    "message": "Transfer completed successfully"
}
```

### 3. Проверка статуса перевода

```bash
curl -X GET http://localhost:{{server.port}}/api/transfers/{{transferId}} \
  -H "X-Phone-Number: {{senderPhoneNumber}}"
```

Ожидаемый ответ:
```json
{
    "id": "{{transferId}}",
    "senderPhoneNumber": "{{senderPhoneNumber}}",
    "recipientPhoneNumber": "{{recipientPhoneNumber}}",
    "amount": {{amount}},
    "status": "SUCCESSFUL",
    "createdAt": "{{createdAt}}",
    "completedAt": "{{completedAt}}"
}
```

### 4. Получение истории переводов

```bash
curl -X GET "http://localhost:{{server.port}}/api/transfers?page=0&size=10" \
  -H "X-Phone-Number: {{senderPhoneNumber}}"
```

### 5. Тест ошибочных сценариев

#### 5.1. Попытка перевода с недостаточным балансом

```bash
curl -X POST http://localhost:{{server.port}}/api/transfers \
  -H "Content-Type: application/json" \
  -H "X-Phone-Number: {{senderPhoneNumber}}" \
  -d '{
    "recipientPhoneNumber": "{{recipientPhoneNumber}}",
    "amount": {{highAmount}},
    "bankId": "{{bankId}}"
  }'
```

#### 5.2. Неверный код подтверждения

```bash
curl -X POST http://localhost:{{server.port}}/api/transfers/{{transferId}}/confirm \
  -H "Content-Type: application/json" \
  -H "X-Phone-Number: {{senderPhoneNumber}}" \
  -d '{
    "confirmationCode": "{{wrongConfirmationCode}}"
  }'
```

#### 5.3. Превышение дневного лимита

```bash
curl -X POST http://localhost:{{server.port}}/api/transfers \
  -H "Content-Type: application/json" \
  -H "X-Phone-Number: {{senderPhoneNumber}}" \
  -d '{
    "recipientPhoneNumber": "{{recipientPhoneNumber}}",
    "amount": {{exceedDailyLimit}},
    "bankId": "{{bankId}}"
  }'
```

#### 5.4. Доступ к чужому переводу

```bash
curl -X GET http://localhost:{{server.port}}/api/transfers/{{transferId}} \
  -H "X-Phone-Number: {{otherPhoneNumber}}"
```

### Сохранение ID перевода в переменную для последующих запросов

```bash
TRANSFER_ID=$(curl -s -X POST http://localhost:{{server.port}}/api/transfers \
  -H "Content-Type: application/json" \
  -H "X-Phone-Number: {{senderPhoneNumber}}" \
  -d '{
    "recipientPhoneNumber": "{{recipientPhoneNumber}}",
    "amount": {{amount}},
    "bankId": "{{bankId}}"
  }' | jq -r '.transferId')

echo "Transfer ID: $TRANSFER_ID"
```
