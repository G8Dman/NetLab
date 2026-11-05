# Проверка обновления 0.5.0-alpha

ПКМ по ПК или ноутбуку открывает Desktop с полями IPv4, маски и шлюза,
кнопками Apply IPv4, DHCP и Command Prompt. Apply/DHCP отправляют команды
на сервер, результат показывается в Command Prompt.

Исправлены обработчики VTP domain/mode/password/version, spanning-tree mode,
port-channel load-balance и адреса SVI. Добавлена проверка применения VTP/VLAN.

Аудит выявил незавершённые функции предыдущей версии: STP, EtherChannel,
Port Security, VTP propagation, DHCP Snooping и SPAN не имеют полноценной
логики передачи кадров. Часть команд хранит настройки, часть ещё требует
реализации. Файлы SWITCH-COMMANDS.md и PC-COMMANDS.md ранее завышали готовность.
Wi-Fi, Firewall, Web Browser, FTP и SNMP не реализованы как полноценные
приложения/службы. Отдельные физические разъёмы пока не имеют новой модели
панели, а Console/RS232 ещё не обеспечивают сеанс PC Terminal по кабелю.

Minecraft не запускался по просьбе пользователя; сборка и Java core-тесты
не подтверждают внешний вид окна в игре.
