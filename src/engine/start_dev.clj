(ns engine.start-dev
  (:require [engine.start :as start]
            [clojure.spec.test.alpha :as st]
            [play-cljc.gl.core :as pc])
  (:import (gui Hello ImGuiImplGlfw ImGuiImplGl3)
           (imgui ImGui)
           (imgui.flag ImGuiConfigFlags)
           (org.lwjgl.glfw GLFW)))

;; https://github.com/codingminecraft/IntegratingImGui
(defn imgui-init [imguiGlfw imGuiGl3 window]
  (ImGui/createContext)
  (doto (ImGui/getIO)
    (.addConfigFlags ImGuiConfigFlags/ViewportsEnable)
    (.addConfigFlags ImGuiConfigFlags/DockingEnable)
    (.setFontGlobalScale 2.0))
  (doto imguiGlfw
    (.init (:handle window) true)
    (.setCallbacksChainForAllWindows true))
  (.init imGuiGl3 "#version 300 es"))

(defn imgui-layer []
  (ImGui/begin "Cool Window")
  (ImGui/text "Hello Imgui from Clojure!")
  (ImGui/end))

(defn imgui-frame [imguiGlfw imGuiGl3]
  (.newFrame imguiGlfw)
  (.newFrame imGuiGl3)
  (ImGui/newFrame)
  (imgui-layer)
  (ImGui/render)
  (.renderDrawData imGuiGl3 (ImGui/getDrawData))
  (let [backupWindowPtr (GLFW/glfwGetCurrentContext)]
    (ImGui/updatePlatformWindows)
    (ImGui/renderPlatformWindowsDefault)
    (GLFW/glfwMakeContextCurrent backupWindowPtr)))

(defn imgui-destroy [imguiGlfw imGuiGl3]
  (.dispose imGuiGl3)
  (.dispose imguiGlfw)
  (ImGui/destroyContext))

(defn start []
  (st/instrument)
  (println "hello from clojure")
  (Hello/hello "msg from clojure")
  (let [window (start/->window)
        game (pc/->game (:handle window))
        imguiGlfw (ImGuiImplGlfw.) imGuiGl3 (ImGuiImplGl3.)
        callback #::start{:init-fn (partial imgui-init imguiGlfw imGuiGl3)
                          :frame-fn (partial imgui-frame imguiGlfw imGuiGl3)
                          :destroy-fn (partial imgui-destroy imguiGlfw imGuiGl3)}]
    (start/start game window callback)))

(defn -main []
  (start)
  (shutdown-agents))
