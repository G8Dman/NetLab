package dev.netlab.core;

import java.util.*;

/** Exercises the same password state machine used by the live console adapter. */
public final class TrafficConsoleTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static final class Lab {
        final TrafficAuthority auth=new TrafficAuthority();
        final Network net=new Network();
        final Console cli=new Console();
        final UUID player=UUID.randomUUID();
        Lab(){net.devices.put(1L,new Device("traffic"));net.devices.put(2L,new Device("switch"));net.wires.add(new Network.Wire(new Network.Endpoint(1,0),new Network.Endpoint(2,0),4));}
        TrafficAuthority.Domain domain(){return auth.resolve(new LanFabric(net).anchors(new Network.Endpoint(1,0)),true);}
        String open(){return TrafficConsole.open(auth,domain(),net,1,cli);}
        String run(String line){return TrafficConsole.execute(auth,domain(),net,1,cli,player,1000,line);}
    }
    public static int run(){
        checks=0;var lab=new Lab();String secret="Administrator-test-123";
        check(lab.open().endsWith("Password:"),"first console open starts password creation");
        check(lab.run("short").contains("12–128")&&lab.cli.pending.equals("traffic-password"),"short password keeps setup active and hidden");
        check(lab.domain().adminHash.isBlank()&&lab.auth.leases.isEmpty(),"rejected password never registers an administrator");
        check(lab.open().contains("Продолжи"),"GUI reopening after a short password preserves the retry stage");
        check(lab.run(secret).contains("Повтори новый пароль"),"valid retry requests confirmation");
        check(lab.domain().adminHash.isBlank(),"first password entry alone does not register the LAN");
        check(lab.open().endsWith("Password:")&&lab.cli.echo(secret).equals("<hidden>"),"reopening during confirmation cannot expose credentials");
        check(lab.run("wrong-confirmation").contains("не совпадают"),"confirmation mismatch is explicit");
        check(lab.run(secret).contains("Повтори новый пароль"),"mismatch restarts creation without locking the user out");
        check(lab.run(secret).contains("Пароль сохранён"),"matching confirmation saves the password");
        String hash=lab.domain().adminHash,domain=lab.domain().id;
        check(TrafficAuthority.verify(secret,hash)&&lab.cli.pending.isBlank(),"stored credential verifies and exits hidden input");
        check(lab.run("speed lan all 0.001").contains("Скорость LAN"),"new administrator can immediately control all LAN traffic");
        check(lab.open().contains("TrafficControl(admin)#")&&lab.domain().id.equals(domain)&&lab.domain().adminHash.equals(hash),"GUI reopen retains authentication, registration and password");
        check(lab.run("speed lan all 0.01").contains("Скорость LAN"),"speed command still works after GUI reopen");
        lab.run("logout");
        check(lab.run("login admin").endsWith("Password:"),"registered LAN requests existing administrator password");
        check(lab.run("incorrect-password").contains("Неверный пароль")&&lab.domain().adminHash.equals(hash),"incorrect login cannot erase the saved password");
        lab.run("login admin");check(lab.run(secret).contains("администратор LAN"),"correct password logs back in");
        lab.run("grant vlan 10");check(lab.run("tiny").endsWith("Password:"),"short delegated password remains in hidden retry");
        check(lab.open().endsWith("Password:"),"admin role cannot skip an unfinished VLAN password prompt on reopen");
        lab.run(secret);check(lab.run(secret).contains("должен отличаться"),"VLAN credential cannot reuse the LAN master password");
        String vlanSecret="Student-password-123";lab.run(vlanSecret);
        check(lab.run(vlanSecret).contains("Пароль сохранён")&&TrafficAuthority.verify(vlanSecret,lab.domain().grants.get(10).hash),"rejected VLAN credential can be corrected without abandoning the console");
        var fresh=new Console();
        check(TrafficConsole.open(lab.auth,lab.domain(),lab.net,1,fresh).contains("LAN зарегистрирована"),"a fresh console session discovers the registered LAN");
        TrafficConsole.execute(lab.auth,lab.domain(),lab.net,1,fresh,lab.player,1000,"login admin");
        check(TrafficConsole.execute(lab.auth,lab.domain(),lab.net,1,fresh,lab.player,1000,secret).contains("администратор LAN"),"fresh session authenticates using saved credentials");
        var empty=new Lab();
        check(empty.run("login admin").contains("Создай новый пароль"),"login admin on an uninitialized LAN guides password creation");
        empty.run(secret);empty.run(secret);
        check(empty.run("speed lan 0.001").contains("Скорость LAN"),"guided first login completes a usable administrator session");
        return checks;
    }
}
