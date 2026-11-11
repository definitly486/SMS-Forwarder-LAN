# SMS Forwarder LAN

Простое Android-приложение + сервер для FreeBSD.  
Перехватывает входящие SMS (или только сообщения с кодами) и отправляет их на твой FreeBSD-сервер по локальной сети (HTTP или HTTPS).

## Состав

```
SMS-Forwarder-LAN/
├── android/                 # Android-приложение (Kotlin)
├── freebsd-server/          # Python-сервер (Flask + SQLite)
├── sms-forwarder-port/      # Порт / пакет для FreeBSD
└── .github/workflows/       # GitHub Actions (сборка debug APK)
```

## Возможности

- Пересылка только SMS с OTP-кодами **или** всех входящих SMS
- HTTP (только домашняя сеть) / HTTPS (с самоподписанным сертификатом)
- Очередь сообщений при отсутствии сети
- Локальная история (Room)
- Автозапуск после перезагрузки
- Постоянное уведомление о работе службы
- Авторизация Bearer-токеном
- Веб-интерфейс на сервере для просмотра SMS

## Быстрый старт

### 1. FreeBSD-сервер

```bash
cd freebsd-server
python3.11 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Сгенерировать самоподписанный сертификат
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem \
  -days 3650 -nodes -subj "/CN=192.168.8.102"

# Отредактировать config.json (токен!)
cp config.json.sample config.json
# ...

python server.py
```

Или установить как службу (см. `sms-forwarder-port/`).

### 2. Android

1. Открой папку `android/` в Android Studio.
2. Положи свой `cert.pem` в `app/src/main/assets/cert.pem` (для HTTPS).
3. Собери debug APK или используй GitHub Actions.
4. Установи, выдай разрешения SMS + уведомления.
5. Укажи IP сервера, порт, токен и включи пересылку.

### 3. GitHub Actions

При пуше в `main`/`master` собирается debug APK.  
При создании тега `v*` — создаётся GitHub Release с APK.

## Требования

- Android 10+ (minSdk 29), targetSdk 35
- FreeBSD 13/14/15 + Python 3.9+
- Локальная сеть (Wi-Fi)

## Безопасность

- HTTP разрешён только в RFC1918-диапазонах.
- HTTPS использует сертификат из assets (или User CA).
- Все запросы требуют Bearer-токен.
- Данные никуда не уходят, кроме указанного тобой хоста.
