package dev.netlab.core;

import java.util.*;

/** Console state machine shared by the server adapter and dependency-free regression checks. */
public final class TrafficConsole {
    private static final Map<Console,Session> SESSIONS=new WeakHashMap<>();
    private static final Map<String,long[]> ATTEMPTS=new HashMap<>();
    private static final class Session {String domain,state="locked",staged="";int vlan;TrafficAuthority.Lease role;}
    public static TrafficAuthority.Lease role(Console cli){var s=SESSIONS.get(cli);return s==null||!s.state.equals("ready")?null:s.role;}
    public static String prompt(Console cli){var s=SESSIONS.get(cli);return s!=null&&s.role!=null?"TrafficControl("+(s.role.vlan==0?"admin":"vlan"+s.role.vlan)+")#":"TrafficControl>";}
    public static String open(TrafficAuthority auth,TrafficAuthority.Domain d,Network n,long id,Console cli){
        try{var s=SESSIONS.get(cli);
            if(s==null||!d.id.equals(s.domain)){s=new Session();s.domain=d.id;SESSIONS.put(cli,s);}
            if(!s.state.equals("locked")&&!s.state.equals("ready")){cli.pending="traffic-password";return "Продолжи ввод пароля.\nPassword:";}
            if(s.role!=null&&auth.valid(s.role)&&(s.role.vlan==0||s.role.vlan==new LanFabric(n).attachedVlan(id)))return "Traffic Controller / "+d.id+"\n"+prompt(cli);
            s.role=null;if(d.adminHash.isBlank()){s.state="setup";cli.pending="traffic-password";return "LAN ещё не зарегистрирована. Создай пароль администратора (12–128 символов).\nPassword:";}
            s.state="locked";cli.pending="";return "LAN зарегистрирована. login admin или login vlan N; help — справка.\n"+prompt(cli);
        }catch(IllegalArgumentException e){cli.pending="";return "% "+e.getMessage()+"\nTrafficControl>";}
    }
    private static void allowedVlan(Network n,long id,int vlan){if(vlan!=new LanFabric(n).attachedVlan(id))throw new IllegalArgumentException("Контроллер должен быть подключён access-портом именно к VLAN "+vlan);}
    public static String execute(TrafficAuthority auth,TrafficAuthority.Domain d,Network n,long id,Console cli,UUID player,long now,String line){
        var device=n.devices.get(id);String actor=device.identity;
        try{
            var s=SESSIONS.computeIfAbsent(cli,k->new Session());
            if(s.domain==null)s.domain=d.id;if(!s.domain.equals(d.id)){SESSIONS.remove(cli);cli.pending="";throw new IllegalArgumentException("Контроллер перемещён в другую LAN. Открой Terminal заново.");}
            if(!s.state.equals("locked")&&!s.state.equals("ready")){
                if(s.state.equals("setup")||s.state.equals("grant")||s.state.equals("password")){
                    s.staged=TrafficAuthority.hash(line);s.state="confirm-"+s.state;cli.pending="traffic-password";return "Повтори новый пароль.\nPassword:";
                }
                if(s.state.startsWith("confirm-")){
                    if(!TrafficAuthority.verify(line,s.staged)){s.state=s.state.substring("confirm-".length());s.staged="";throw new IllegalArgumentException("Пароли не совпадают. Введи новый пароль заново.");}
                    String operation=s.state.substring("confirm-".length()),hash=s.staged;s.staged="";s.state=operation;
                    if(operation.equals("setup"))s.role=auth.setup(d,actor,hash);
                    else if(operation.equals("grant")){auth.grantPassword(s.role,s.vlan,line);}
                    else{s.role=auth.changeAdminPassword(s.role,hash);}
                    s.staged="";s.state="ready";cli.pending="";return "Пароль сохранён.\n"+prompt(cli);
                }
                if(s.state.equals("login-admin")||s.state.equals("login-vlan")){
                    String key=d.id+"|"+player;var gate=ATTEMPTS.computeIfAbsent(key,k->new long[]{0,0});if(ATTEMPTS.size()>4096)ATTEMPTS.clear();
                    if(now<gate[1])throw new IllegalArgumentException("Слишком много попыток. Подожди 30 секунд.");
                    int vlan=s.state.equals("login-admin")?0:s.vlan;if(vlan!=0)allowedVlan(n,id,vlan);
                    try{s.role=auth.authenticate(d,actor,vlan,line);gate[0]=0;}catch(IllegalArgumentException e){if(++gate[0]>=5){gate[1]=now+600;gate[0]=0;}s.role=null;s.state="locked";throw e;}
                    s.state="ready";cli.pending="";return "Доступ: "+(vlan==0?"администратор LAN":"VLAN "+vlan)+"\n"+prompt(cli);
                }
            }
            String[] a=line.strip().toLowerCase(Locale.ROOT).split("\\s+");String cmd=String.join(" ",a);
            if(cmd.equals("setup")){if(!d.adminHash.isBlank())throw new IllegalArgumentException("LAN уже зарегистрирована; используй login admin");s.state="setup";s.role=null;cli.pending="traffic-password";return "Создай пароль администратора LAN.\nPassword:";}
            if(cmd.equals("help")||cmd.equals("?"))return "login admin | login vlan N\ngrant vlan N (admin; новый пароль вводится скрыто)\nrevoke vlan N | password admin\nspeed lan [all|arp|icmp|tcp|udp|dhcp|ndp] FACTOR\nspeed vlan N [PROTOCOL] FACTOR\nclear lan | clear vlan N\nshow status | show policy | logout\nFACTOR: 1 = обычная скорость, 0.001 = в 1000 раз медленнее.\n"+prompt(cli);
            if(cmd.equals("login admin")){if(d.adminHash.isBlank()){s.state="setup";s.role=null;cli.pending="traffic-password";return "Пароль LAN ещё не создан. Создай новый пароль администратора (12–128 символов), затем повтори его.\nPassword:";}s.state="login-admin";s.role=null;cli.pending="traffic-password";return "Пароль администратора LAN.\nPassword:";}
            if(a.length==3&&a[0].equals("login")&&a[1].equals("vlan")){if(d.adminHash.isBlank())throw new IllegalArgumentException("Пароль LAN ещё не создан администратором. Сначала setup.");s.vlan=Addressing.number(a[2],1,4094);allowedVlan(n,id,s.vlan);s.state="login-vlan";s.role=null;cli.pending="traffic-password";return "Пароль управления VLAN "+s.vlan+".\nPassword:";}
            if(cmd.equals("logout")||cmd.equals("exit")){s.role=null;s.state="locked";cli.pending="";return "Выход.\n"+prompt(cli);}
            if(!auth.valid(s.role))throw new IllegalArgumentException(d.adminHash.isBlank()?"Пароль администратора LAN ещё не создан. Введи setup и дважды введи новый пароль (12–128 символов).":"Сначала login admin или login vlan N. Доступ мог быть отозван.");
            if(s.role.vlan!=0)allowedVlan(n,id,s.role.vlan);
            if(a.length==3&&a[0].equals("grant")&&a[1].equals("vlan")){auth.require(s.role,0);s.vlan=Addressing.number(a[2],1,4094);s.state="grant";cli.pending="traffic-password";return "Создай пароль для VLAN "+s.vlan+".\nPassword:";}
            if(a.length==3&&a[0].equals("revoke")&&a[1].equals("vlan")){auth.revoke(s.role,Addressing.number(a[2],1,4094));return "Доступ VLAN отозван.\n"+prompt(cli);}
            if(cmd.equals("password admin")){auth.require(s.role,0);s.state="password";cli.pending="traffic-password";return "Новый пароль администратора LAN.\nPassword:";}
            if(a[0].equals("speed")){
                int vlan,offset;if(a.length>=3&&a[1].equals("lan")){vlan=0;offset=2;}else if(a.length>=4&&a[1].equals("vlan")){vlan=Addressing.number(a[2],1,4094);offset=3;}else throw new IllegalArgumentException("speed lan [PROTOCOL] FACTOR | speed vlan N [PROTOCOL] FACTOR");
                if(a.length!=offset+1&&a.length!=offset+2)throw new IllegalArgumentException("Лишние аргументы speed");String protocol=a.length==offset+2?a[offset]:"all";double factor=Double.parseDouble(a[a.length-1]);auth.speed(s.role,vlan,protocol,factor);return "Скорость "+(vlan==0?"LAN":"VLAN "+vlan)+" / "+protocol+": "+factor+"x (для новых передач).\n"+prompt(cli);
            }
            if(cmd.equals("clear lan")){auth.clear(s.role,0);return "Правила LAN очищены.\n"+prompt(cli);}
            if(a.length==3&&a[0].equals("clear")&&a[1].equals("vlan")){auth.clear(s.role,Addressing.number(a[2],1,4094));return "Правила VLAN очищены.\n"+prompt(cli);}
            if(cmd.equals("show status"))return "LAN: "+d.id+"\nRole: "+(s.role.vlan==0?"admin":"VLAN "+s.role.vlan)+"\nController: "+actor+"\n"+prompt(cli);
            if(cmd.equals("show policy")){var out=new StringBuilder();for(var r:auth.rules)if(r.domain.equals(d.id)&&(s.role.vlan==0||r.vlan==0||r.vlan==s.role.vlan))out.append(r.vlan==0?"LAN":"VLAN "+r.vlan).append(' ').append(r.protocol).append(' ').append(r.speed).append("x\n");return out+prompt(cli);}
            throw new IllegalArgumentException("Неизвестная команда. Введи help.");
        }catch(IllegalArgumentException e){var s=SESSIONS.get(cli);if(s!=null&&!auth.valid(s.role)){s.role=null;if(s.state.contains("grant")||s.state.contains("password")){s.state="locked";s.staged="";}}
            if(s!=null&&s.state.contains("setup")&&!d.adminHash.isBlank()){s.state="locked";s.staged="";}
            if(s!=null&&Set.of("setup","confirm-setup","grant","confirm-grant","password","confirm-password").contains(s.state)){
                cli.pending="traffic-password";return "% "+e.getMessage()+"\n"+(s.state.startsWith("confirm-")?"Повтори новый пароль.":"Введи новый пароль (12–128 символов).")+"\nPassword:";
            }
            cli.pending="";if(s!=null&&!s.state.equals("ready"))s.state="locked";return "% "+e.getMessage()+"\n"+prompt(cli);}
    }
}

