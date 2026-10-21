package clyvr.view;
import clyvr.controller.*;import clyvr.model.Entities.*;import clyvr.ui.*;import javax.swing.*;import java.awt.*;
public class NotificationsView extends JPanel {
 public NotificationsView(AppController app){super(new BorderLayout());JPanel p=ViewSupport.page("Notifications","Updates relating to your account and appointments");int n=0;for(Notice notice:app.store.notices){if(!notice.userId.equals(app.current.id))continue;n++;notice.read=true;ViewSupport.line(p,notice.text,notice.time.toLocalDate()+" · "+notice.time.toLocalTime().withNano(0));}if(n==0)p.add(ViewSupport.empty("No notifications yet."));add(ViewSupport.wrap(p));}
}
