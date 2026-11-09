package dev.netlab.academy.client;
import dev.netlab.academy.*;
import dev.netlab.academy.core.Course;
import dev.netlab.client.GuideBookContents;
import java.util.*;
/** Builds the real handbook pages from server-filtered progress. No independent screen. */
public final class CourseBook {
    public static List<GuideBookContents.Section> sections(Set<String> installed,Set<String> completed,Set<String> evidence,Course.Policy policy,boolean goals){
        if(installed.isEmpty())return List.of();var tasks=Catalog.COURSE.available(installed,policy);var articles=new ArrayList<GuideBookContents.Article>();
        StringBuilder route=new StringBuilder("Учись прямо в Библии по CISCO. Все задания проверяет сервер; кнопки выдачи достижений нет. Обычное дерево также доступно в меню достижений Minecraft.\n\n");
        route.append("Прогресс: ").append(tasks.stream().filter(t->completed.contains(t.id())).count()).append(" / ").append(tasks.size()).append(".\nЦели установленной сборки: ").append(goals?"выполнены":"ещё не все выполнены").append(".\n\nОсновной маршрут:\n");
        for(String id:Catalog.COURSE.main){var t=Catalog.COURSE.task(id);route.append(completed.contains(id)?"✓ ":"○ ").append(id).append(" — ").append(t.title()).append('\n');}
        var next=tasks.stream().filter(t->!completed.contains(t.id())&&completed.containsAll(t.parents())).findFirst();
        route.append("\nСледующий доступный шаг: ").append(next.map(t->t.id()+" — "+t.title()+"\n"+t.task()).orElse("Все доступные шаги завершены.")).append("\n\nВыполненные действия запоминаются до выполнения предыдущих заданий. Чужой готовый стенд не заменяет изготовление или собственную настройку. При удалении аддона его ветка исчезает, сохранённый прогресс остаётся.\n\nДля базового курса нужен разрешённый сборкой источник FE. Творческий генератор не требуется.\n\nНеобязательные внешние сервисы в общем финале: интернет — ").append(policy.external()?"да":"нет").append(", голос — ").append(policy.voice()?"да":"нет").append(", резервуары жидкостей — ").append(policy.fluids()?"да":"нет").append('.');
        articles.add(new GuideBookContents.Article("netlabacademy:course/route","Маршрут и прогресс",route.toString()));
        String group="";
        for(var t:tasks){boolean done=completed.contains(t.id()),ready=completed.containsAll(t.parents());String status=done?"Выполнено":evidence.contains(t.id())?"Действие зачтено; заверши предыдущие задания":ready?"Доступно":"Нужны предыдущие задания";
            var body=new StringBuilder(t.groupTitle()+"\n\nСостояние: "+status+"\n\nЧто сделать и проверить:\n"+t.task()+"\n\nПредыдущие шаги:\n");
            if(t.parents().isEmpty())body.append("Не требуются.\n");for(String id:t.parents()){var p=Catalog.COURSE.task(id);body.append(completed.contains(id)?"✓ ":"○ ").append(id).append(" — ").append(p.title()).append('\n');}
            body.append("\nГде настраивать и читать:\nБиблия → ").append(t.guide()).append("\n\n").append(Hints.forTask(t.id()));
            if(t.id().equals("F03"))body.append("\nТекущие финалы сборки: ").append(String.join(", ",Catalog.COURSE.goals(installed,policy))).append(". Ранее выданный значок не отзывается при установке новых аддонов.");
            articles.add(new GuideBookContents.Article("netlabacademy:course/"+t.id().toLowerCase(Locale.ROOT),(done?"✓ ":ready?"› ":"○ ")+t.id()+" — "+t.title(),body.toString()));
        }
        return List.of(new GuideBookContents.Section("netlabacademy:course","Обучение",5,List.copyOf(articles)));
    }
}
