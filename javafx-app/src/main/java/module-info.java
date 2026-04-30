module com.santea.javafxapp {
    requires javafx.controls;
    requires transitive javafx.graphics;
    requires javafx.fxml;
    requires javafx.swing;
    requires javafx.web;
    requires java.sql;
    requires java.desktop;
    requires java.net.http;
    requires jdk.httpserver;
    requires jdk.jsobject;
    requires jakarta.mail;
    requires spring.security.crypto;
    requires webcam.capture;
    requires org.bytedeco.javacv;
    requires vosk;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.bootstrapfx.core;
    requires com.almasb.fxgl.all;

    opens com.santea to javafx.fxml;
    opens com.santea.controller to javafx.fxml, javafx.web;
    exports com.santea;
}