package dev.netlab.core;

public final class ConsoleLink {
    /** Physical diagnostics shared by the server resolver and headless regression tests. */
    public static String problem(Device host,int source,Device remote,int destination,String cable,boolean reciprocal){
        if(!Hardware.powered(host))return "ПК/ноутбук выключен. Включи питание на физической панели.";
        if(source<0||source>=host.ports.size()||host.ports.get(source).hidden||!host.ports.get(source).console)
            return "Нет доступного RS232. Установи USB-RS232 в usb0 ноутбука.";
        if(remote==null)return "На другом конце кабеля пустая ячейка шкафа.";
        if(destination<0||destination>=remote.ports.size()||!compatible(host,source,remote,destination))
            return "Нужно соединение RS232 ПК/ноутбука с Console маршрутизатора или коммутатора.";
        if(!cable.equals("auto")&&!cable.equals("console"))return "Для Terminal нужен консольный кабель RS232 / Console.";
        if(!reciprocal)return "Соединение кабеля не подтверждено вторым устройством. Переподключи кабель.";
        if(!Hardware.powered(remote)){
            String profile=remote.hardware==null?remote.kind:remote.hardware.profile;
            String power=Hardware.powerProblem(remote);
            return "Кабель подключён к "+remote.hostname+" ("+profile+"), но устройство выключено.\n"+(power.isBlank()?"":power+"\n")+"Включи питание на физической панели подключённого устройства.";
        }
        if(!host.ports.get(source).up||!remote.ports.get(destination).up)return "Консольный порт недоступен. Проверь питание и адаптер.";
        return "";
    }
    public static boolean compatible(Device a,int ap,Device b,int bp){
        if(ap<0||bp<0||ap>=a.ports.size()||bp>=b.ports.size())return false;
        return !a.ports.get(ap).hidden&&!b.ports.get(bp).hidden&&a.ports.get(ap).console && b.ports.get(bp).console
            && (a.kind.equals("pc")&&!b.kind.equals("pc")&&DeviceKinds.console(b.kind)
                || b.kind.equals("pc")&&!a.kind.equals("pc")&&DeviceKinds.console(a.kind));
    }
}
