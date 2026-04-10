module com.santea.javafxapp {
    requires javafx.controls;
    requires transitive javafx.graphics;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.sql;
    requires java.desktop;
    requires jakarta.mail;
    requires spring.security.crypto;
    requires webcam.capture;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;
    requires org.kordamp.bootstrapfx.core;
    requires com.almasb.fxgl.all;

    opens com.santea to javafx.fxml;
    opens com.santea.controller to javafx.fxml;
    exports com.santea;
}