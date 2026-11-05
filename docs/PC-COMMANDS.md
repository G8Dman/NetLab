# Команды PC/Laptop

Исходный учебный перечень: `Cisco_Packet_Tracer_PC_Laptop_Commands_Settings.txt`.

В Command Prompt PC/Laptop доступны `ipconfig`, `ipconfig /all`, `/release`, `/renew`, `ipv6config`, `arp -a`, `netstat`, `ping`, `tracert`, `nslookup`, `ssh`, `telnet`, `dir`, `delete`, `ftp`, `snmpget`, `snmpgetbulk` и `snmpset`. Также поддерживается форма `ipconfig IP MASK [GATEWAY]` для быстрой учебной настройки IPv4.

Это внутриигровая модель Packet Tracer, поэтому FTP/SNMP/SSH и DNS не создают реальные соединения за пределами симулированной сети. GUI-приложения из справочника (Desktop, IP Configuration, Firewall, Web Browser) пока представлены командами и состоянием устройства.

У роутеров и коммутаторов теперь есть отдельный порт `Console`; у PC/Laptop — `RS232`. Он отделён от Ethernet-интерфейсов и не участвует в IP-маршрутизации.
