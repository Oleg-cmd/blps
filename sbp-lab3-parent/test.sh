curl -X POST "http://localhost:8080/api/v1/transfers/initiate" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"recipientPhoneNumber": "9993334444","amount": '100',"bankId": "1000000001"}'


curl -X POST "http://localhost:8080/api/v1/transfers/631e4f51-240f-4a53-9b8b-152e0526efae/confirm" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"confirmationCode": "394079"}'


# admin
curl -X GET "http://localhost:8080/api/v1/transfers/631e4f51-240f-4a53-9b8b-152e0526efae" \
     -u "admin:adminpass" \
     -H "Content-Type: application/json"



# различные ошибки

# Сумма не является числом
curl -X POST "http://localhost:8080/api/v1/transfers/initiate" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"recipientPhoneNumber": "9993334444","amount": "сто рублей","bankId": "1000000001"}'


# Отсутствует ID банка
curl -X POST "http://localhost:8080/api/v1/transfers/initiate" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"recipientPhoneNumber": "9993334444","amount": 100}'


# Неверный ID банка (вымышленный, может пройти валидацию формата, но не найдется в адаптере)
curl -X POST "http://localhost:8080/api/v1/transfers/initiate" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"recipientPhoneNumber": "9993334444","amount": 100,"bankId": "НЕ_СУЩЕСТВУЮЩИЙ_БАНК_ID"}'


# Неавторизованный доступ
curl -X POST "http://localhost:8080/api/v1/transfers/initiate" \
     -u "wtf:wtf" \
     -H "Content-Type: application/json" \
     -d '{"recipientPhoneNumber": "9993334444","amount": 100,"bankId": "1000000001"}'

# Несуществующий перевод
curl -X POST "http://localhost:8080/api/v1/transfers/что_за_перевод/confirm" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"confirmationCode": "123456"}'


# Уже существующий перевод (попытка подтверждения)
curl -X POST "http://localhost:8080/api/v1/transfers/631e4f51-240f-4a53-9b8b-152e0526efae/confirm" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{"confirmationCode": "123"}'


# Нет кода подтверждения
curl -X POST "http://localhost:8080/api/v1/transfers/5efdc61f-8d36-47d4-9739-19daa412787/confirm" \
     -u "9991112222:userpass" \
     -H "Content-Type: application/json" \
     -d '{}'


