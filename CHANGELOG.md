# Donate Integrate 1.1.0

## Главное

- Добавлена поддержка DonationAlerts через OAuth и Centrifugo WebSocket.
- DonatePay и DonationAlerts теперь могут работать одновременно.
- Полностью переработано меню Config: отдельные панели DonatePay и DonationAlerts, отдельные кнопки подключения и остановки.
- Добавлена русская и английская локализация интерфейса, сообщений команд и окна миграции.
- Добавлена автоматическая миграция старого `config/dintegrate.cfg` в новый `config/dintegrate.json`.

## DonationAlerts

- Добавлена авторизация через браузер без ручного копирования токена и user id.
- После авторизации сохраняются access token, refresh token, user id и имя аккаунта.
- Добавено подключение к DonationAlerts Centrifugo и подписка на канал донатов.
- События DonationAlerts обрабатываются теми же правилами, что и DonatePay.
- Добавлена команда сброса логина DonationAlerts.

## DonatePay

- User ID можно получить по API токену через кнопку Get ID.
- User ID теперь можно также указать вручную, если API DonatePay временно недоступен.
- При изменении токена DonatePay user id и имя аккаунта автоматически сбрасываются.
- Добавлены более подробные статусы подключения, как у DonationAlerts.

## GUI

- Убраны глобальные Start/Stop/Restart и Save and Restart.
- Настройки сохраняются сразу после изменения, без автоматического переподключения.
- Для каждого сервиса теперь свои кнопки Reconnect и Stop.
- DonatePay token скрыт по умолчанию: видно только первые и последние символы.
- Перед раскрытием токена показывается подтверждение.
- Вкладка Misc переименована в Test.
- Панели Config адаптируются под разные разрешения и GUI scale; на широком экране DonatePay и DonationAlerts стоят рядом.

## Команды

- Команды управления разделены по сервисам:
  - `/dpi dp token <token>`
  - `/dpi dp getid`
  - `/dpi dp user <id>`
  - `/dpi dp reconnect`
  - `/dpi dp stop`
  - `/dpi dp status`
  - `/dpi da token <token>`
  - `/dpi da user <id>`
  - `/dpi da reset`
  - `/dpi da channels <channels>`
  - `/dpi da reconnect`
  - `/dpi da stop`
  - `/dpi da status`
- `/dpi test <name> <sum> <message>` оставлена общей для проверки правил.
- `/dpi reload` теперь перезагружает конфиг без переподключения.

## Конфиги и совместимость

- При обнаружении старого `dintegrate.cfg` на главном экране предлагается конвертация.
- Старый cfg после успешной миграции архивируется как `dintegrate.cfg.zip`.
- В новый JSON переносятся DonatePay token, user id, socket/token endpoints и все старые правила с командами.
- Команды из старого cfg сохраняются в порядке `cmd1`, `cmd2`, `cmd3` и т.д.
- `startdelay` переносится как `/delay`.
- Добавлена защита от потери неизвестных старых ключей: они сохраняются в `legacy_values`.
- Добавлена поддержка старых cfg в UTF-8 и Windows-1251, чтобы не ломать русские команды.

## Исправления

- Исправлены проблемы с повторными подключениями.
- Исправлены случаи, когда DonatePay account text залезал под поле user id.
- Убраны лишние debug/test элементы из Config GUI.
- Улучшена обработка ошибок DonatePay user API, включая 429 Too Many Requests.
- Токены больше не выводятся в лог целиком.
