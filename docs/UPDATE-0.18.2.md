# NetLab 0.18.2 — передняя панель и show running-config

## Порты

На Catalyst 2960/2950/3560/3650 порты располагаются вертикальными парами,
слева направо при взгляде спереди. У 2960 верхний ряд — Fa0/1, Fa0/3, …, Fa0/23;
нижний — Fa0/2, Fa0/4, …, Fa0/24. Справа отдельная пара Gi0/1 над Gi0/2.
Направление исправлено и у обычных маршрутизаторов/коммутаторов.

Порядок и пары совпадают в модели мира и физическом меню. Под разъёмами мира
появились короткие подписи портов. Отрисовка, выбор разъёма и конец кабеля
используют одну геометрию. Физические индексы устройств не меняются.

[Схема передней панели](assets/2960-port-map.svg) · [Пример нового show run](examples/2960-default-running-config.txt)

## Конфигурация

`show run` выводит Building configuration, размер конфигурации в байтах,
секции с разделителями `!`, один блок на интерфейс, разделы line и конечный
`end`. Интерфейсы сортируются численно: Fa0/1…24, Gi0/1…2, затем Vlan1.
Убраны повторные блоки и строки стандартных access/native VLAN 1, allowed all,
duplex/speed auto, стандартной bandwidth. Description, shutdown, IP, ACL,
port-security, STP, DHCP snooping и прочие заданные параметры сохраняются.

На новых фиксированных Catalyst стандартный режим портов — dynamic auto;
неактивный Vlan1 имеет no ip address и shutdown. Старые явно сохранённые режимы
не меняются при обновлении. `default interface` восстанавливает стандартные
настройки выбранного профиля.

Строка version обозначает совместимость формата учебной конфигурации, а не
настоящую Cisco IOS. VTY-параметры отражают состояние модели: no login не
подменяется вымышленным login. При VTP server/client обычные VLAN находятся
в базе vlan.dat; проверяются через show vlan brief. Все настройки, включая
базу VLAN, сохраняются во внутренних снимках и на картах памяти.

## Почему VLAN 1 не исчезает от allowed vlan 10

Access VLAN, native VLAN и allowed list — независимые параметры. Команда
`switchport trunk allowed vlan 10` не меняет native VLAN 1. Но пользовательский
трафик VLAN 1 через такой trunk не разрешается. Access VLAN на trunk не действует.

`switchport mode access` отключает trunk-режим, сохраняя ранее заданные
trunk-параметры. Значения по умолчанию не засоряют show run. Чтобы убрать
явно заданные trunk-параметры, на выбранном интерфейсе выполни:

```text
no switchport trunk native vlan
no switchport trunk allowed vlan
```

Они возвращают native VLAN 1 и allowed all, не меняя access VLAN и режим access.
Объяснения обновлены также в «Библии по CISCO».

## Источники Cisco

- [Нумерация портов 2960](https://www.cisco.com/c/en/us/td/docs/switches/lan/catalyst2960/hardware/installation/guide/2960_hg/higover.html)
- [Access, trunk, native VLAN и allowed list](https://www.cisco.com/c/en/us/td/docs/switches/lan/catalyst2960/software/release/12-2_58_se/configuration/guide/2960scg/swvlan.html)

Оформление сверено с предоставленным примером sh run.txt. Проверки выполняются
без запуска Minecraft, миров, игрового сервера и GameTest.

Результат сборки: **9030 проверок ядра** (включая 958 форм CLI) и **499 отдельных
проверок меню/рецептов/конфигураций** прошли. Проверены номера и пары для всех
четырёх направлений и двух ячеек шкафа, выбор разъёмов, положительная передача
VLAN 10 при исключённой VLAN 1, сохранение неактивных trunk-параметров, команды
их явного сброса, сохранение карты и отсутствие дублей в show run.
