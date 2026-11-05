# Аудит команд NetLab 0.10.2-alpha

Проверено поддержанное дерево CLI маршрутизатора, коммутатора и ПК/ноутбука:
957 вариантов с учётом устройства, режима, RIP/OSPF/EIGRP и форм `do`/`no`.
Это не 957 разных команд. Перечень берётся непосредственно из CommandGrammar,
а не из вручную поддерживаемого списка. Объяснение и пример обязательны для
каждой объявленной формы; отсутствие статьи ломает проверку сборки.

В «Библии по CISCO» в разделе «Команды» добавлены отдельные статьи с режимом,
назначением, синтаксисом, примером, проверкой результата и ограничениями.
Контроллер LAN/VLAN описан отдельно как расширение NetLab, а не Cisco IOS.

## Что проверялось автоматически

- Каждый пример проходит текущую грамматику и вызывается через Console.execute
  в подготовленном контексте. Не допускаются падения и ответы об отсутствии
  обработчика. Отказы из-за отсутствия маршрута, авторизации или предварительной
  настройки допустимы: это проверка покрытия, не доказательство сетевого эффекта.
- Отдельные проверки состояния: установка/удаление статических MAC, фильтр VLAN,
  вывод switchport без выбранного порта, сохранение физического типа интерфейса
  при сбросе, сохранение trunk при назначении access VLAN.
- ПК: настоящая для модели SSH/Telnet-авторизация к маршрутизатору и SVI
  коммутатора; удалённая команда изменяет именно удалённое устройство.
- DNS на ПК: получение записи настроенного достижимого сервера и отказ после
  разрыва связи. FTP/SNMP больше не сообщают фиктивный успех.
- Ранее существующие сценарии ядра проверяют маршруты, DHCP/relay, ACL/NAT,
  VLAN/trunk, STP, EtherChannel, port security, модули, Wi-Fi, права и время.

## Исправлено

- Для статической MAC-записи исправлено количество аргументов, включая `no`.
- ПК больше не предлагает команды IOS вроде `enable` и `configure terminal`.
- SSH/Telnet ПК используют обработчик удалённого CLI и проверку VTY, а не текст
  о симуляции успеха. Целью может быть и коммутатор с настроенным SVI.
- `nslookup` использует доступный DNS модели, а не только локальный словарь.
- FTP, SNMP и netstat явно сообщают об отсутствующей реализации.
- Исправлены фильтры статических MAC по VLAN/интерфейсу, STP и port-security
  по интерфейсу, EtherChannel по группе, вывод switchport без выбора порта,
  а также отсутствовавший обработчик `show cdp interface`.
- `default interface` сохраняет аппаратный слот, среду и предельную скорость.
- В OSPF не предлагается `no auto-summary`; в EIGRP не предлагается OSPF/RIP-
  форма `default-information originate`; `always` ограничен OSPF.

## Границы соответствия Cisco

Полное соответствие IOS не достигнуто. Конкретный набор команд и значения
по умолчанию зависят от платформы и релиза. Ни настоящая IOS, ни Packet Tracer,
ни Minecraft для сравнения в этой проверке не запускались.

Не реализованы FTP, SNMP/MIB и транспортная таблица ПК. Duplex/voice VLAN,
PortFast и некоторые флаги сохраняются, но не обеспечивают полного физического
или протокольного эффекта. CDP, STP и маршрутизация рассчитываются аналитически;
полные таймеры, BPDU, LSA, DUAL и механизмы реального SSH отсутствуют.
Реальный DCE/DTE упрощён; Serial clock требуется на обоих концах.
Примеры в книге не настраивают всю топологию автоматически.

Объяснения описывают код текущей модели. Официальная документация использована
для сопоставления назначения, режимов и выбранных исправлений; это не построчная
проверка каждой опции всех релизов IOS и не сертификация совместимости.

## Официальные источники

- [MAC/SPAN, IOS LAN Switching](https://www.cisco.com/c/en/us/td/docs/ios/lanswitch/command/reference/lsw_book/lsw_m1.html)
- [VLAN/STP, IOS LAN Switching](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/lanswitch/command/lsw-cr-book/lsw-s2.html)
- [Маршруты и passive-interface](https://www.cisco.com/c/en/us/td/docs/ios/iproute_pi/command/reference/iri_book/iri_pi1.html)
- [EIGRP, auto-summary](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/iproute_eigrp/command/ire-cr-book/ire-a1.html)
- [DHCP server](https://www.cisco.com/c/en/us/td/docs/ios-xml/ios/ipaddr_dhcp/configuration/12-4/dhcp-12-4-book/config-dhcp-server.html)
- [SSH и доступ к линиям](https://www.cisco.com/c/en/us/td/docs/ios/sec_user_services/configuration/guide/12_4/sec_securing_user_services_12-4_book/sec_ssh_term_line.html)
- [bandwidth — параметр маршрутизации, не физический лимит](https://www.cisco.com/E-Learning/bulk/public/tac/cim/cib/using_cisco_ios_software/cmdrefs/bandwidth.htm)
- [Packet Tracer: ПК, ноутбук и Terminal](https://tutorials.ptnetacad.net/help/default/config_PCs.htm)

Проверка воспроизводится командой `./dev.sh build`; Minecraft она не запускает.
