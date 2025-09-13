(ns rules.input
  (:require
   [clojure.spec.alpha :as s]
   [engine.macros :refer [s->]]
   [odoyle.rules :as o]))

(defn js-keyCode->keyname [keycode]
  (condp = keycode
    32 ::space
    37 ::left
    38 ::up
    39 ::right
    40 ::down
    82 ::r
    nil))

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
     [keyname ::pressed-key ::keydown]]

    ::one-frame-keyup
    [:what
     [keyname ::pressed-key ::keyup]
     :then
     (s-> session (o/retract keyname ::pressed-key))]}))
