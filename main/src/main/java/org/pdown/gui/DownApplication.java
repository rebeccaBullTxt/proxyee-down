package org.pdown.gui;

import de.felixroske.jfxsupport.AbstractJavaFxApplicationSupport;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.pdown.core.util.OsUtil;
import org.pdown.gui.com.Browser;
import org.pdown.gui.com.Components;
import org.pdown.gui.content.PDownConfigContent;
import org.pdown.gui.http.EmbedHttpServer;
import org.pdown.gui.http.handler.DirChooserHandler;
import org.pdown.gui.http.handler.FileChooserHandler;
import org.pdown.gui.http.handler.GetLocaleHandler;
import org.pdown.gui.http.handler.SetLocaleHandler;
import org.pdown.gui.util.ConfigUtil;
import org.pdown.gui.util.I18nUtil;
import org.pdown.rest.util.PathUtil;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "org.pdown.rest")
public class DownApplication extends AbstractJavaFxApplicationSupport {

  private static final String OS = OsUtil.isWindows() ? "windows"
      : (OsUtil.isMac() ? "mac" : "linux");
  private static final String ICON_NAME = OS + "/logo.png";

  private Stage stage;
  private Browser browser;
  private TrayIcon trayIcon;

  private CountDownLatch countDownLatch;
  private int frontPort;

  @Override
  public void start(Stage primaryStage) throws Exception {
    stage = primaryStage;
    Platform.setImplicitExit(false);
    //load config
    PDownConfigContent.getInstance().load();
    initConfig();
    initEmbedHttpServer();
    initTray();
    initWindow();
    initBrowser();
    show();
  }

  private void initConfig() throws IOException {
    frontPort = ConfigUtil.getInt("front.port");
    if ("prd".equals(ConfigUtil.getString("spring.profiles.active"))) {
      try {
        frontPort = OsUtil.getFreePort(frontPort);
      } catch (IOException e) {
        Components.alert(I18nUtil.getMessage("gui.alert.startError") + e.getMessage());
        throw e;
      }
    }

  }

  private void initEmbedHttpServer() {
    countDownLatch = new CountDownLatch(1);
    new Thread(() -> {
      EmbedHttpServer embedHttpServer = new EmbedHttpServer(7478);
      embedHttpServer.addRouter("/native/fileChooser", new FileChooserHandler());
      embedHttpServer.addRouter("/native/dirChooser", new DirChooserHandler());
      embedHttpServer.addRouter("/native/getLocale", new GetLocaleHandler());
      embedHttpServer.addRouter("/native/setLocale", new SetLocaleHandler());
      embedHttpServer.start(future -> countDownLatch.countDown());
    }).start();
  }

  //加载托盘
  private void initTray() throws AWTException {
    if (SystemTray.isSupported()) {
      // 获得系统托盘对象
      SystemTray systemTray = SystemTray.getSystemTray();
      // 获取图片所在的URL
      URL url = Thread.currentThread().getContextClassLoader().getResource(ICON_NAME);
      // 为系统托盘加托盘图标
      Image trayImage = Toolkit.getDefaultToolkit().getImage(url);
      Dimension trayIconSize = systemTray.getTrayIconSize();
      trayImage = trayImage.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH);
      trayIcon = new TrayIcon(trayImage, "proxyee-down");
      systemTray.add(trayIcon);

      //双击事件监听
      trayIcon.addActionListener(event -> Platform.runLater(() -> show()));

      //添加右键菜单
      PopupMenu popupMenu = new PopupMenu();
      MenuItem showItem = new MenuItem(I18nUtil.getMessage("gui.tray.show"));
      showItem.addActionListener(event -> Platform.runLater(() -> show()));
      MenuItem closeItem = new MenuItem(I18nUtil.getMessage("gui.tray.exit"));
      closeItem.addActionListener(event -> {
        Platform.exit();
        System.exit(0);
      });
      popupMenu.add(showItem);
      popupMenu.add(closeItem);
      trayIcon.setPopupMenu(popupMenu);
    }
  }

  //加载webView
  private void initBrowser() throws AWTException {
    browser = new Browser();
    stage.setScene(new Scene(browser));
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
    }
    browser.load("http://127.0.0.1:" + frontPort);
  }

  //加载gui窗口
  private void initWindow() {
    stage.setTitle("Proxyee Down");
    Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
    stage.setX(bounds.getMinX());
    stage.setY(bounds.getMinY());
    stage.setWidth(bounds.getWidth());
    stage.setHeight(bounds.getHeight());
    stage.getIcons().add(new javafx.scene.image.Image(Thread.currentThread().getContextClassLoader().getResourceAsStream(ICON_NAME)));
    //关闭窗口监听
    stage.setOnCloseRequest(event -> {
      event.consume();
      stage.hide();
    });
  }

  //显示gui窗口
  private void show() {
    if (stage.isShowing()) {
      if (stage.isIconified()) {
        stage.setIconified(false);
      } else {
        stage.toFront();
      }
    } else {
      stage.show();
      stage.toFront();
    }
    /*//避免有时候窗口不弹出
    if (isFront && !isTray && OsUtil.isWindows()) {
      stage.setIconified(true);
      stage.setIconified(false);
    }*/
  }

  static {
    //设置日志存放路径
    System.setProperty("LOG_PATH", PathUtil.ROOT_PATH);
    //webView允许跨域访问
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

    //处理MAC dock图标
    if (OsUtil.isMac()) {
      try {
        Class<?> appClass = Class.forName("com.apple.eawt.Application");
        Method getApplication = appClass.getMethod("getApplication");
        Object application = getApplication.invoke(appClass);
        Method setDockIconImage = appClass.getMethod("setDockIconImage", Image.class);
        URL url = Thread.currentThread().getContextClassLoader().getResource("mac/dock_logo.png");
        Image image = Toolkit.getDefaultToolkit().getImage(url);
        setDockIconImage.invoke(application, image);
      } catch (Exception e) {
      }
    }
  }

  public static void main(String[] args) {
    launch(DownApplication.class, null, args);
  }
}
