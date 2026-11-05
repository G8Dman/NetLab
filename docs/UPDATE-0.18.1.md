# NetLab 0.18.1 — VLAN, контекстная справка и аппаратные порты

## Терминал

`vlan 10` переводит консоль в `Switch(config-vlan)#`. `name STUDENTS` меняет имя
VLAN, `exit` возвращает в `(config)#`, `end` — в `Switch#`. Сам режим VLAN уже
существовал; прежнее приглашение ошибочно отображало его как EXEC.

`?` теперь описывает следующий уровень команды и аргументы, отдельно от Tab:

```text
Switch(config)# do ?
do ping
do show

Switch(config)# vlan ?
<1-4094>  VLAN IDs 1-4094 (normal range 1-1005)

Switch(config)# vlan 10 ?
<cr>
```

Справка не создаёт VLAN и не меняет режим. Tab по-прежнему дописывает команды
и реальные имена интерфейсов. VLAN 1002–1005 остаются зарезервированными.

## Модули и имена интерфейсов

- HWIC в `hwic0`: `GigabitEthernet0/0/0`; в `hwic1`: `GigabitEthernet0/1/0`.
- NIM в ISR4331: `0/1/порт`, `0/2/порт`; встроенные порты находятся в `0/0/порт`.
- NM: `1/порт`; WIC на NM: `1/подслот/порт`.
- У 3650: downlink `GigabitEthernet1/0/1..24`, фиксированные SFP-uplink
  `GigabitEthernet1/1/1..4`. Сами интерфейсы и клетки присутствуют без SFP.
- Router-PT использует `слот/порт`. Учебные шасси NetLab с дополнительными
  PT-разъёмами резервируют отдельные номера, чтобы не перекрывать встроенные/NM.

`GLC-GE-100FX` в `HWIC-1GE-SFP` меняет среду и скорость существующего порта
на оптику/100 Мбит/с. Он не создаёт отдельный FastEthernet и не удаляет
встроенный RJ45 `GigabitEthernet0/1`. При извлечении SFP остаётся пустая клетка.

Старые имена обновляются при чтении устройства/карты памяти. Физические
индексы сохраняются, поэтому привязки кабелей, IP и настройки портов остаются
на месте. Ссылки на интерфейсы в NAT, маршрутах, static MAC, SPAN и
passive-interface обновляются вместе с именами.

## Проверки

Сборка успешна: **8849 проверок ядра** и **499 отдельных проверок меню,
рецептов и конфигураций**. Игровой запуск не выполнялся.

Регрессии проверяются через настоящий Console.execute и снимки ConfigStore:
контекст `?`, VLAN/name/exit/end, ошибочные ID, HWIC + 100FX, смена трансивера,
сохранение индекса/IP и миграция ссылок, фиксированные SFP-клетки и NIM.
Minecraft, Prism, игровой сервер, мир и GameTest не запускаются.

## Документация Cisco

- [VLAN и вход в config-vlan](https://www.cisco.com/c/en/us/td/docs/switches/lan/catalyst2960l/software/15-2_6_e/configuration_guide/b_1526e_consolidated_2960l_cg/m_vlan_cg_2960l.html)
- [Нумерация интерфейсов карт GE SFP](https://www.cisco.com/c/en/us/td/docs/routers/access/interfaces/software/feature/guide/EHWIC_1_GE_SFP_CU_FM.html)
- [Нумерация downlink/uplink Catalyst 3650](https://www.cisco.com/c/en/us/td/docs/switches/lan/catalyst3650/software/release/16-12/configuration_guide/int_hw/b_1612_int_and_hw_3650_cg/configuring_interface_characteristics.html)
