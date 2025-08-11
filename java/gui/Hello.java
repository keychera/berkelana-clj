package gui;

import imgui.ImGui;
import imgui.app.Application;
import imgui.app.Configuration;

public class Hello extends Application {
    public static void hello(String message) {
        System.out.println("hello from Java, your message: " + message);
         launch(new Hello());
    }

    @Override
    protected void configure(Configuration config) {
        config.setTitle("Dear ImGui is Awesome!");
    }

    @Override
    public void process() {
        ImGui.text("Hello, World!");
    }
}