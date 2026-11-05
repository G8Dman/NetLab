# Команды коммутатора

Актуальная реализация 0.6.0 описана в [STUDY-AND-SWITCHING.md](STUDY-AND-SWITCHING.md).
Текст ниже относится к первоначальному CLI 0.4.0.

Исходный учебный перечень: `Cisco_Packet_Tracer_Switch_Commands.txt`.

Добавлен Catalyst-2960-подобный слой CLI для коммутатора: VLAN и SVI, access/voice VLAN, trunk/native/allowed VLAN, PortFast/BPDU Guard, EtherChannel, Port Security, VTP, CDP, DHCP Snooping, SPAN, MAC-таблица, сохранение конфигурации и диагностические `show`-команды. Сокращения и Tab используют то же дерево команд, что и выполнение.

Это учебная модель, а не Cisco IOS. L2 forwarding продолжает использовать собственную модель NetLab; STP, VTP, DHCP Snooping и SPAN хранят и показывают настройки, но не моделируют все таймеры и кадры реального Catalyst. Команды, отсутствующие у конкретной модели Packet Tracer, также могут быть недоступны в реальном IOS.

Minecraft/Prism в этом обновлении не запускался. Проверена только локальная сборка и core-проверки.
