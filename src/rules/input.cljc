(ns rules.input
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o])
  #?(:clj (:import [org.lwjgl.glfw GLFW])))

#?(:clj
   (defn glfw-keycode->keyname [keycode]
     (condp = keycode
       GLFW/GLFW_KEY_SPACE ::space
       GLFW/GLFW_KEY_LEFT ::left
       GLFW/GLFW_KEY_UP ::up
       GLFW/GLFW_KEY_RIGHT ::right
       GLFW/GLFW_KEY_DOWN ::down
       GLFW/GLFW_KEY_R ::r
       nil))
   :cljs
   (defn js-keyCode->keyname [keycode]
     (condp = keycode
       32 ::space
       37 ::left
       38 ::up
       39 ::right
       40 ::down
       82 ::r
       nil)))

(s/def ::x number?)
(s/def ::y number?)

(s/def ::pressed-key keyword?)
(s/def ::keydown any?)
(s/def ::keyup any?)

(def rules
  (o/ruleset
   {::mouse
    [:what
     [::mouse ::x mouse-x]
     [::mouse ::y mouse-y]]

    ::pressed-key
    [:what
     [keyname ::pressed-key keystate]]}))
